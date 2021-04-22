package com.github.refactoringai.refactory;

import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import com.github.refactoringai.refactory.entities.RefactoringUnit;
import com.github.refactoringai.refactory.entities.RefactoryMergeRequest;

import org.apache.commons.math3.stat.StatUtils;
import org.apache.http.client.utils.URIBuilder;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.gitlab4j.api.Constants.MergeRequestState;
import org.gitlab4j.api.DiscussionsApi;
import org.gitlab4j.api.GitLabApi;
import org.gitlab4j.api.GitLabApiException;
import org.gitlab4j.api.MergeRequestApi;
import org.gitlab4j.api.ProjectApi;
import org.gitlab4j.api.models.Diff;
import org.gitlab4j.api.models.DiffRef;
import org.gitlab4j.api.models.Discussion;
import org.gitlab4j.api.models.MergeRequest;
import org.gitlab4j.api.models.MergeRequestFilter;
import org.gitlab4j.api.models.Position;
import org.gitlab4j.api.models.Position.PositionType;
import org.gitlab4j.api.models.Project;
import org.jboss.logging.Logger;

@ApplicationScoped
public class GitLab {

    private static final Logger LOG = Logger.getLogger(GitLab.class);
    private static final String SURVALYZER_URL_VAR_IDENTIFIER = "urlVar%02d";

    private final String surveyBaseUrl;
    private final Integer amountOfRecommendations;
    private final Float minimumCertaintyToRecommendThreshold;

    private final MergeRequestApi mergeRequestApi;
    private final ProjectApi projectApi;
    private final DiscussionsApi discussionsApi;

    /**
     * @param recommendationTextTemplate
     * @param surveyBaseUrl
     * @param mergeRequestApi
     * 
     */
    @Inject
    // TODO this is probably not the best way of injecting these values, i need to
    // learn more about the framework probably to do it more idiomatic
    public GitLab(GitLabApi gitlabApi,
            @ConfigProperty(name = "amount.of.recommendations", defaultValue = "2147483647") Integer amountOfRecommendations,
            @ConfigProperty(name = "survey.base.url") String surveyBaseUrl,
            @ConfigProperty(name = "min.certainty.recommend.threshold", defaultValue = "0.5") Float minimumCertaintyToRecommendThreshold) {
        this.mergeRequestApi = gitlabApi.getMergeRequestApi();
        this.projectApi = gitlabApi.getProjectApi();
        this.discussionsApi = gitlabApi.getDiscussionsApi();
        this.amountOfRecommendations = amountOfRecommendations;
        this.surveyBaseUrl = surveyBaseUrl;
        this.minimumCertaintyToRecommendThreshold = minimumCertaintyToRecommendThreshold;
    }

    /**
     * Creates discussions on the new paths of the merge request diffs. discussions
     * for all passed refactors.
     * 
     * @param projectPath      The GitLab project on which to operate.
     * @param mergeRequestId   the id of the merge request to operate on.
     * @param refactoringUnits The refactors for which to create recommendations
     * @return The created Discussions
     * @throws GitLabApiException    If fetching information or placing discussions
     *                               fails
     * @throws URISyntaxException
     * @throws MalformedURLException
     */
    public List<Discussion> createDiscussionsFromRefactors(final MergeRequest mergeRequest,
            final List<RefactoringUnit> refactoringUnits)
            throws GitLabApiException, MalformedURLException, URISyntaxException {

        LOG.infof("The following refactors are candidates to suggest: %s", refactoringUnits);
        List<RefactoringUnit> toRecommend = refactoringUnits.stream()
                .filter(refactoringUnit -> refactoringUnit.shouldRefactor)
                .filter(refactoringUnit -> refactoringUnit.shouldRefactorProbability >= minimumCertaintyToRecommendThreshold)
                .sorted().collect(Collectors.toList());
        LOG.infof("The following refactors adhere to the requirements of suggestion: %s", toRecommend);
        var resultingDiscussions = new ArrayList<Discussion>();
        int addedAmount = 0;

        for (RefactoringUnit refactoringUnit : toRecommend) {
            // We add until we have successfully posted amountOfRecommendations discussions.
            if (addedAmount == amountOfRecommendations) {
                break;
            }
            try {
                var discussion = createRefactorDiscussion(mergeRequest.getProjectId(), mergeRequest.getIid(),
                        refactoringUnit, mergeRequest.getDiffRefs());
                resultingDiscussions.add(discussion);
                refactoringUnit.recommendationWasPlaced();
                addedAmount++;
                // Gitlab can complain if we recommend stuff that is not in the diff, or
                // sometimes a 500 is given by random, we don't want to stop recommending if
                // this happens
                // so we catch this exception
            } catch (GitLabApiException glae) {
                LOG.infof("Cannot add discussion with refactoringUnit %s due to %s", refactoringUnit,
                        glae.getMessage());
                LOG.debugf(glae, "");
            }
        }
        LOG.infof("The following refactors were succesfully suggested: %s", toRecommend.stream()
                .filter(refactoringUnit -> refactoringUnit.wasRecommended).collect(Collectors.toList()));

        return resultingDiscussions;
    }

    public List<Diff> diffsForProjectIdAndMergeRequestIid(Integer projectId, Integer mergeRequestIid)
            throws GitLabApiException {
        return diffsForMergeRequestAndProjectIdOrPath(projectId, mergeRequestIid);
    }

    public List<Diff> diffsForProjectPathAndMergeRequestIid(String projectPath, Integer mergeRequestIid)
            throws GitLabApiException {
        return diffsForMergeRequestAndProjectIdOrPath(projectPath, mergeRequestIid);
    }

    public double medianCommitsPerMergeRequest(Object projectIdOrPath) throws GitLabApiException {
        List<MergeRequest> mrs = mergeRequestApi.getMergeRequests(projectIdOrPath);

        List<MergeRequest> mrsDivergingCommits = new ArrayList<>();
        for (MergeRequest mergeRequest : mrs) {
            try {
                var withDivergingCommits = mergeRequestApi.getMergeRequest(projectIdOrPath, mergeRequest.getIid(),
                        false, true, false);
                mrsDivergingCommits.add(withDivergingCommits);
                LOG.infof("Finished %d out of %d", mrsDivergingCommits.size(), mrs.size());

            } catch (GitLabApiException gae) {
                LOG.errorf(gae, "Failed to fetch %d", mergeRequest.getIid());
            }
        }

        double[] commitCounts = mrsDivergingCommits.stream()
                .filter(mr -> mr.getDivergedCommitsCount() != null && mr.getDivergedCommitsCount() != 0
                        && mr.getDivergedCommitsCount() < 50)
                .mapToDouble(mr -> mr.getDivergedCommitsCount().doubleValue()).toArray();
        return StatUtils.percentile(commitCounts, 50d);
    }

    public String generateDescription(RefactoringUnit predictionResult)
            throws MalformedURLException, URISyntaxException {
        return String.format(
                "Consider extracting part of the method \"%s\" to a separate method."
                        + " ([More info](https://refactoring.com/catalog/extractFunction.html))."
                        + " It would be of great help if you could help evaluate these recommendations:"
                        + "[please consider filling in this survey.](%s)",
                predictionResult.unitName, generateSurveyUrl(predictionResult));
    }

    public URL generateSurveyUrl(RefactoringUnit predictionResult) throws MalformedURLException, URISyntaxException {
        return new URIBuilder(surveyBaseUrl).addParameter(survalyzerUrlVariable(1), predictionResult.unitName)
                .addParameter(survalyzerUrlVariable(2), predictionResult.id.toString()).build().toURL();
    }

    public List<MergeRequest> getOpenedMergeRequests(Project project) throws GitLabApiException {
        var filter = new MergeRequestFilter();
        filter.setState(MergeRequestState.OPENED);
        Integer projectId = project.getId();
        filter.setProjectId(projectId);
        List<MergeRequest> mergeRequestsWithoutDiffRefs = mergeRequestApi.getMergeRequests(filter);

        // Gitlab returns lists of merge requests without diffreffs which we need for
        // the sha, So we have to fetch all of them separately
        List<MergeRequest> mergeRequestsWithDiffRefs = new ArrayList<>();
        for (MergeRequest mergeRequest : mergeRequestsWithoutDiffRefs) {
            var mergeRequestsWithDiffRef = mergeRequestApi.getMergeRequest(projectId, mergeRequest.getIid());
            mergeRequestsWithDiffRefs.add(mergeRequestsWithDiffRef);
        }

        return mergeRequestsWithDiffRefs;
    }

    public Project getProjectById(Integer projectId) throws GitLabApiException {
        return projectApi.getProject(projectId);
    }

    public String getFileUrl(RefactoringUnit refactoringUnit) throws GitLabApiException {
        RefactoryMergeRequest refactoryMergeRequest = refactoringUnit.refactoryMergeRequest;
        Project gitlabProject = getProjectById(refactoryMergeRequest.project.gitlabId);
        String projectUrl = gitlabProject.getWebUrl();
        MergeRequest gitlabMergeRequest = mergeRequestApi.getMergeRequest(gitlabProject.getId(),
                refactoryMergeRequest.mergeRequestIid);
        final String format = "%s/blob/%s/%s";
        return String.format(format, projectUrl, gitlabMergeRequest.getSha(), refactoringUnit.newPath);
    }

    private String survalyzerUrlVariable(Integer number) {
        return String.format(Locale.US, SURVALYZER_URL_VAR_IDENTIFIER, number);
    }

    private Discussion createRefactorDiscussion(Object projectIdOrPath, int mergeRequestIid, RefactoringUnit refactor,
            DiffRef diffRef) throws GitLabApiException, MalformedURLException, URISyntaxException {

        var position = new Position();
        position.setBaseSha(diffRef.getBaseSha());
        position.setHeadSha(diffRef.getHeadSha());
        position.setStartSha(diffRef.getStartSha());
        position.setPositionType(PositionType.TEXT);
        position.setNewLine(refactor.lineNumber);
        position.setOldPath(refactor.oldPath);
        position.setNewPath(refactor.newPath);
        return discussionsApi.createMergeRequestDiscussion(projectIdOrPath, mergeRequestIid,
                generateDescription(refactor), null, null, position);

    }

    /**
     * Fetches the diffs for a certain merge request.
     * 
     * @param projectPath    The project on which to operate.
     * @param mergeRequestId The id of the merge request of which to fetch the diffs
     *                       of.
     * @return The diffs of the merge request.
     * @throws GitLabApiException If fetching the diffs was not succesfull.
     */
    private List<Diff> diffsForMergeRequestAndProjectIdOrPath(Object projectIdOrPath, int mergeRequestIid)
            throws GitLabApiException {
        return mergeRequestApi.getMergeRequestChanges(projectIdOrPath, mergeRequestIid).getChanges();
    }
}
