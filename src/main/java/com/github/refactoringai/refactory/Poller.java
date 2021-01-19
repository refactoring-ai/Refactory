package com.github.refactoringai.refactory;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.json.bind.Jsonb;
import javax.transaction.Transactional;

import com.github.refactoringai.refactory.entities.Model;
import com.github.refactoringai.refactory.entities.RefactoringUnit;
import com.github.refactoringai.refactory.entities.RefactoryMergeRequest;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.gitlab4j.api.GitLabApiException;
import org.gitlab4j.api.models.Diff;
import org.gitlab4j.api.models.Discussion;
import org.gitlab4j.api.models.MergeRequest;
import org.jboss.logging.Logger;

import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import io.quarkus.hibernate.orm.panache.Panache;
import io.quarkus.narayana.jta.runtime.TransactionConfiguration;

@ApplicationScoped
public class Poller {

    private static final Logger LOG = Logger.getLogger(Poller.class);

    @Inject
    GitLab gitLab;

    @ConfigProperty(name = "ci.project.dirs")
    List<Path> repositoryPaths;

    @ConfigProperty(name = "git.username")
    String gitUsername;

    @ConfigProperty(name = "git.password")
    String gitPassword;

    @ConfigProperty(name = "gitlab.oauth2.token")
    String gitLabAccessToken;

    @ConfigProperty(name = "git.clone.uris")
    List<URI> gitCloneUris;

    @ConfigProperty(name = "gitlab.project.ids")
    List<Integer> gitlabProjectIds;

    @ConfigProperty(name = "model.path")
    Path modelPath;

    @Inject
    MetricCollector metricCollector;

    @Inject
    OnnxPredictor onnxPredictor;

    @Inject
    Jsonb jsonb;

    private Git openOrCloneRepository(Path repositoryPath, URI cloneUri) throws IOException, GitAPIException {
        var creds = new UsernamePasswordCredentialsProvider(gitUsername, gitPassword);
        try {
            return Git.open(repositoryPath.toFile());
        } catch (RepositoryNotFoundException rnfe) {
            LOG.infof("Repository not found at %s trying to clone from %s", repositoryPath, cloneUri);
            try {
                return Git.cloneRepository().setCloneAllBranches(true).setURI(cloneUri.toString())
                        .setDirectory(repositoryPath.toFile()).setCredentialsProvider(creds).call();
            } catch (GitAPIException gae) {
                LOG.errorf(rnfe, "Could not open repo at %s", repositoryPath);
                LOG.errorf(gae, "Could not clone %s Exiting", cloneUri);
                throw gae;
            }
        }
    }

    @Transactional
    public List<RefactoringUnit> getRefactoringUnitsForMergeRequest(MergeRequest mergeRequest,
            RefactoryMergeRequest refactoryMergeRequest, Path repositoryPath, URI cloneUri)
            throws GitLabApiException, IOException, GitAPIException, OrtException {

        try (var git = openOrCloneRepository(repositoryPath, cloneUri)) {
            // TODO create bean to prevent duplication above
            var creds = new UsernamePasswordCredentialsProvider(gitUsername, gitPassword);
            git.fetch().setCredentialsProvider(creds).setRemote("origin").call();
            git.checkout().setName(mergeRequest.getSha()).setForced(true).call();
        }
        var diffs = gitLab.diffsForProjectIdAndMergeRequestIid(mergeRequest.getProjectId(), mergeRequest.getIid());
        Map<Path, Diff> diffMap = diffs.stream().filter(diff -> !diff.toString().contains("/src/test/"))
                .collect(Collectors.toMap(diff -> Paths.get(diff.getNewPath()), Function.identity()));

        var modelPathStr = modelPath.toString();
        List<RefactoringUnit> refactoringUnits;
        try (var env = OrtEnvironment.getEnvironment();
                var session = env.createSession(modelPathStr, new OrtSession.SessionOptions())) {
            Model model;
            var modelFromJson = onnxPredictor.buildModel(modelPathStr, session);
            var modelOptional = Model.findByIdOptional(modelFromJson.id);
            if (!modelOptional.isPresent()) {
                modelFromJson.persist();
                model = modelFromJson;
            } else {
                model = (Model) modelOptional.get();
            }
            refactoringUnits = metricCollector.getMetrics(repositoryPath, diffMap, model);
            if (refactoringUnits.isEmpty()) {
                LOG.info("No metrics found for merge request");
                return List.of();
            }
            refactoringUnits.forEach(unit -> unit.refactoryMergeRequest = refactoryMergeRequest);
            onnxPredictor.predict(env, session, refactoringUnits);
        }

        return refactoringUnits;
    }

    @Transactional
    @TransactionConfiguration(timeout = Integer.MAX_VALUE)
    public List<Discussion> processMergeRequest(MergeRequest mergeRequest, RefactoryMergeRequest refactoryMergeRequest,
            Path repositoryPath, URI cloneUri)
            throws GitLabApiException, URISyntaxException, IOException, GitAPIException, OrtException {

        List<RefactoringUnit> toRecommend = getRefactoringUnitsForMergeRequest(mergeRequest, refactoryMergeRequest,
                repositoryPath, cloneUri);
        if (toRecommend.isEmpty()) {
            LOG.infof("No refactoring units for %s", mergeRequest);
            return List.of();
        }

        // Persist already to receive id from persistence provider.
        RefactoringUnit.persist(toRecommend);
        List<Discussion> discussions = gitLab.createDiscussionsFromRefactors(mergeRequest, toRecommend);

        // Keep track whether the recommendation was served
        toRecommend.forEach(Panache.getEntityManager()::merge);
        LOG.infof("Created %d merge request discussions", discussions.size());
        return discussions;
    }

    @Transactional
    @TransactionConfiguration(timeout = Integer.MAX_VALUE)
    public void persistMergeRequestInTransaction(RefactoryMergeRequest refactoryMergeRequest) {
        refactoryMergeRequest.persist();
    }

    public void poll() throws GitLabApiException, IOException, GitAPIException, OrtException, URISyntaxException {
        for (int i = 0; i < gitlabProjectIds.size(); i++) {
            var gitlabProjectId = gitlabProjectIds.get(i);
            var repositoryPath = repositoryPaths.get(i);
            var gitCloneUri = gitCloneUris.get(i);
            LOG.infof("Polling for merge requests for project \"%s\"", gitCloneUri);
            var mergeRequests = gitLab.getOpenedMergeRequests(gitlabProjectId);
            for (var mergeRequest : mergeRequests) {
                if (RefactoryMergeRequest.hasMergeRequestBeenProcessed(mergeRequest)) {
                    continue;
                }
                LOG.infof("Found not yet processed merge request \"%s\"", mergeRequest.getTitle());
                var refactoryMergeRequest = RefactoryMergeRequest.fromGitlabMergeRequest(mergeRequest);
                persistMergeRequestInTransaction(refactoryMergeRequest);
                processMergeRequest(mergeRequest, refactoryMergeRequest, repositoryPath, gitCloneUri);
            }
        }
        LOG.info("Finished polling all projects");
    }

}
