package com.github.refactoringai.refactory;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.transaction.Transactional;

import com.github.refactoringai.refactory.entities.Model;
import com.github.refactoringai.refactory.entities.RefactoringUnit;
import com.github.refactoringai.refactory.entities.RefactoryMergeRequest;
import com.github.refactoringai.refactory.entities.RefactoryProject;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.gitlab4j.api.GitLabApiException;
import org.gitlab4j.api.models.Diff;
import org.gitlab4j.api.models.Discussion;
import org.gitlab4j.api.models.MergeRequest;
import org.gitlab4j.api.models.Project;
import org.jboss.logging.Logger;

import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import io.quarkus.hibernate.orm.panache.Panache;
import io.quarkus.narayana.jta.runtime.TransactionConfiguration;
import io.quarkus.scheduler.Scheduled;
import io.quarkus.scheduler.Scheduler;

@ApplicationScoped
public class Poller {

    private static final Logger LOG = Logger.getLogger(Poller.class);

    @ConfigProperty(name = "git.fetch.before.checkout", defaultValue = "true")
    Boolean fetchBeforeCheckout;

    @ConfigProperty(name = "git.username")
    String gitUsername;

    @ConfigProperty(name = "gitlab.oauth2.token")
    String gitLabAccessToken;

    @ConfigProperty(name = "model.path")
    Path modelPath;

    @ConfigProperty(name = "projects.path")
    Optional<Path> projectsPath;

    @ConfigProperty(name = "project.ids")
    List<Integer> projectIds;

    @Inject
    GitLab gitLab;

    @Inject
    MetricCollector metricCollector;

    @Inject
    OnnxPredictor onnxPredictor;

    @Inject
    Scheduler scheduler;

    private Git openOrCloneRepository(Path repositoryPath, String cloneUri, CredentialsProvider credentialsProvider)
            throws IOException, GitAPIException {

        try {
            // TODO allow for specification of other git folders than .git
            var dotGit = repositoryPath.resolve(".git");
            if (Files.isDirectory(dotGit)) {
                return Git.open(repositoryPath.toFile());
            }
            // is repo a submodule, in submodules there is a file with a path to the git
            // objects instead of a .git folder (e.g. gitdir: ../../../../.git/modules/refactory-test\n hopefully git
            // doesn't change the format of this):
            var dotGitContents = Files.readString(dotGit);

            // Trim necesarry for linebreak at the end of the file
            var splitDotGitContents  = dotGitContents.split(" ")[1].trim();
            var gitFilesPath = repositoryPath.resolve(splitDotGitContents).normalize();
            return Git.open(gitFilesPath.toFile());
        } catch (RepositoryNotFoundException rnfe) {
            LOG.infof("Repository not found at %s trying to clone from %s", repositoryPath, cloneUri);
            Files.createDirectories(repositoryPath);
            try {
                return Git.cloneRepository().setCloneAllBranches(true).setURI(cloneUri)
                        .setDirectory(repositoryPath.toFile()).setCredentialsProvider(credentialsProvider).call();
            } catch (GitAPIException gae) {
                LOG.errorf(rnfe, "Could not open repo at %s", repositoryPath);
                LOG.errorf(gae, "Could not clone %s Exiting", cloneUri);
                throw gae;
            }
        }
    }

    private void prepareRepository(Path repositoryPath, String cloneUri, String sha)
            throws IOException, GitAPIException {
        var credentialsProvider = new UsernamePasswordCredentialsProvider(gitUsername, gitLabAccessToken);

        try (Git git = openOrCloneRepository(repositoryPath, cloneUri, credentialsProvider)) {
            if (fetchBeforeCheckout) {
                // TODO allow for specification of other remotes than origin
                git.fetch().setCredentialsProvider(credentialsProvider).setRemote("origin").call();
            }

            git.checkout().setName(sha).setForced(true).call();
        }
    }

    private Map<Path, Diff> buildDiffMap(Project project, MergeRequest mergeRequest) throws GitLabApiException {
        List<Diff> diffs = gitLab.diffsForProjectIdAndMergeRequestIid(project.getId(), mergeRequest.getIid());
        return diffs.stream().filter(diff -> !diff.getNewPath().contains("/src/test/"))
                .collect(Collectors.toMap(diff -> Paths.get(diff.getNewPath()), Function.identity()));
    }

    @Transactional
    @TransactionConfiguration(timeout = Integer.MAX_VALUE)
    public List<RefactoringUnit> getRefactoringUnitsForMergeRequest(Path repositoryPath, Map<Path, Diff> diffMap)
            throws OrtException {

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
            onnxPredictor.predict(env, session, refactoringUnits);
        }

        return refactoringUnits;
    }

    @Transactional
    @TransactionConfiguration(timeout = Integer.MAX_VALUE)
    public List<Discussion> processMergeRequest(MergeRequest mergeRequest, List<RefactoringUnit> toRecommend,
            Project project) throws GitLabApiException, URISyntaxException, IOException {

        if (toRecommend.isEmpty()) {
            LOG.infof("No refactoring units for %s", mergeRequest.getTitle());
            return List.of();
        }

        // Persist already to receive id from persistence provider.
        RefactoringUnit.persist(toRecommend);
        List<Discussion> discussions = gitLab.createDiscussionsFromRefactors(mergeRequest, toRecommend);

        // Keep track whether the recommendation was served
        toRecommend.forEach(Panache.getEntityManager()::merge);
        LOG.infof("Created %d merge request discussions at %s", discussions.size(), mergeRequest.getWebUrl());
        return discussions;
    }

    @Transactional
    @TransactionConfiguration(timeout = Integer.MAX_VALUE)
    public void persistMergeRequestInTransaction(RefactoryMergeRequest refactoryMergeRequest) {
        refactoryMergeRequest.persist();
    }

    private List<Discussion> processProject(Project project)
            throws GitLabApiException, IOException, GitAPIException, OrtException, URISyntaxException {
        LOG.infof("Polling for merge requests for project \"%s\"", project.getName());
        Path repositoryPath = repositoryPathFromProject(project);
        RefactoryProject refactoryProject = persistRefactoryProjectIfNotPersisted(project);
        List<MergeRequest> mergeRequests = gitLab.getOpenedMergeRequests(project);
        var resultingGitlabDiscussions = new ArrayList<Discussion>();
        for (MergeRequest mergeRequest : mergeRequests) {

            if (RefactoryMergeRequest.hasMergeRequestBeenProcessed(mergeRequest, refactoryProject)) {
                continue;
            }
            LOG.infof("Found not yet processed merge request \"%s\"", mergeRequest.getTitle());
            prepareRepository(repositoryPath, project.getHttpUrlToRepo(), mergeRequest.getSha());
            var refactoryMergeRequest = RefactoryMergeRequest.fromGitlabMergeRequest(mergeRequest);
            refactoryMergeRequest.project = refactoryProject;
            persistMergeRequestInTransaction(refactoryMergeRequest);
            Map<Path, Diff> diffMap = buildDiffMap(project, mergeRequest);

            List<RefactoringUnit> toRecommend = getRefactoringUnitsForMergeRequest(repositoryPath, diffMap);
            toRecommend.forEach(unit -> unit.refactoryMergeRequest = refactoryMergeRequest);

            resultingGitlabDiscussions.addAll(processMergeRequest(mergeRequest, toRecommend, project));
        }
        return resultingGitlabDiscussions;
    }

    public List<Discussion> poll()
            throws GitLabApiException, IOException, GitAPIException, OrtException, URISyntaxException {
        var projects = new ArrayList<Project>();
        for (Integer projectId : projectIds) {
            projects.add(gitLab.getProjectById(projectId));
        }
        List<Discussion> resultingGitLabDiscussions = new ArrayList<>();
        for (Project project : projects) {
            resultingGitLabDiscussions.addAll(processProject(project));
        }
        LOG.info("Finished polling all projects");
        return resultingGitLabDiscussions;
    }

    private Path repositoryPathFromProject(Project project) throws IOException {
        return projectsPath.orElse(Files.createTempDirectory(null)).resolve(project.getPath());
    }

    @Transactional
    @TransactionConfiguration(timeout = Integer.MAX_VALUE)
    RefactoryProject persistRefactoryProjectIfNotPersisted(Project project) {
        var refactoryProject = RefactoryProject.fromGitLabProject(project);
        var refactoryProjectOptional = refactoryProject.findByGitLabId();
        if (refactoryProjectOptional.isEmpty()) {
            refactoryProject.persist();
        } else {
            refactoryProject = refactoryProjectOptional.get();
        }
        return refactoryProject;
    }

    @Scheduled(every = "5m")
    public void pollForMergeRequests() throws GitLabApiException, IOException, GitAPIException, OrtException,
            URISyntaxException, IllegalStateException {

        if (scheduler.isRunning()) {
            scheduler.pause();
            try {
                poll();
            } finally {
                scheduler.resume();
            }
        }
    }

}
