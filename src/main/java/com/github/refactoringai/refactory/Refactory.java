package com.github.refactoringai.refactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.transaction.Transactional;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.gitlab4j.api.GitLabApiException;
import org.gitlab4j.api.models.Diff;
import org.gitlab4j.api.models.Discussion;
import org.jboss.logging.Logger;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import io.quarkus.runtime.QuarkusApplication;

@ApplicationScoped
public class Refactory implements QuarkusApplication {

    private static final Logger LOG = Logger.getLogger(Refactory.class);

    @ConfigProperty(name = "ci.merge.request.project.path")
    String gitLabProjectPath;

    @ConfigProperty(name = "ci.project.dir")
    Path repositoryPath;

    @ConfigProperty(name = "ci.merge.request.id")
    Integer mergeRequestId;

    @Inject
    Gitlab gitLab;

    @Inject
    Predictor predictorHelper;

    @Inject
    MetricCollector metricCollector;

    /**
     * Main method of the application. First fetches the diffs then the metrics of
     * those diffs, then predicts the refactors on the metrics. then persists the
     * refactors and finally places discussions on the merge request
     */
    @Override
    public int run(String... args) throws GitLabApiException, InterruptedException, IOException {
        List<Diff> diffs;
        try {
            diffs = gitLab.diffs(gitLabProjectPath, mergeRequestId);
        } catch (Exception e) {
            LOG.errorf("Could not get diffref for project %s for merge request ID %s", gitLabProjectPath,
                    mergeRequestId);
            throw e;
        }
        LOG.infof("Retrieved %d diffs from merge request with id %d", diffs.size(), mergeRequestId);

        List<Sample> metricsList;
        try {
            metricsList = metricCollector.getMetrics(repositoryPath, diffs);
        } catch (Exception e) {
            LOG.errorf("Could not fetch metrics for repository at path %s and changed diffs %s", repositoryPath, diffs);
            throw e;
        }
        LOG.infof("Retrieved %d samples", metricsList.size());

        if (metricsList.isEmpty()) {
            LOG.info("No metrics detected in this merge request's changes");
            return 0;
        }

        Collection<Refactor> refactors;
        try {
            refactors = predictorHelper.predict(metricsList);
        } catch (Exception e) {
            LOG.errorf("Predicting failed with metrics %s", metricsList);
            throw e;
        }

        try {
            persistRefactors(refactors);
        } catch (Exception e) {
            LOG.errorf("Could not persist %s", refactors);
            throw e;
        }

        refactors = refactors.stream().filter(Refactor::shouldRefactor).collect(Collectors.toList());
        LOG.infof("%d possible refactors detected", refactors.size());

        Collection<Discussion> discussions;
        try {
            discussions = gitLab.createDiscussionsFromRefactors(gitLabProjectPath, mergeRequestId, refactors);
        } catch (Exception e) {
            LOG.errorf("Comment placement failed for gitlab project %s, merge requst %s and refactors %s",
                    gitLabProjectPath, mergeRequestId, refactors);
            throw e;
        }
        LOG.infof("Made %d recomendations", discussions.size());
        return 0;
    }

    /**
     * Separate method to persist the refactors in a transaction.
     * 
     * @param toPersist The collection of refactors to persist
     */
    @Transactional
    public void persistRefactors(Iterable<Refactor> toPersist) {
        PanacheEntityBase.persist(toPersist);
    }
}
