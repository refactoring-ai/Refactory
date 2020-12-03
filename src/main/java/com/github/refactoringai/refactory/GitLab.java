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

import org.apache.http.client.utils.URIBuilder;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.gitlab4j.api.Constants.MergeRequestState;
import org.gitlab4j.api.GitLabApi;
import org.gitlab4j.api.GitLabApiException;
import org.gitlab4j.api.MergeRequestApi;
import org.gitlab4j.api.models.Diff;
import org.gitlab4j.api.models.DiffRef;
import org.gitlab4j.api.models.Discussion;
import org.gitlab4j.api.models.MergeRequest;
import org.gitlab4j.api.models.MergeRequestFilter;
import org.gitlab4j.api.models.Position;
import org.gitlab4j.api.models.Position.PositionType;
import org.jboss.logging.Logger;

@ApplicationScoped
public class GitLab {

    private static final Logger LOG = Logger.getLogger(GitLab.class);
    private static final String SURVALYZER_URL_VAR_IDENTIFIER = "urlVar%02d";

    private final String surveyBaseUrl;
    private final String recommendationTextTemplate;
    private final Integer amountOfRecommendations;
    private final Float minimumCertaintyToRecommendThreshold;

    private final GitLabApi gitLabApi;
    private final MergeRequestApi mergeRequestApi;

    /**
     * @param recommendationTextTemplate
     * @param surveyBaseUrl
     * @param gitLabApi
     * @param mergeRequestApi
     * 
     * TODO this is probaly not the best way of injecting these values, i need to learn more about the framework  probably to do it more idiomatic
     */
    @Inject
    public GitLab(@ConfigProperty(name = "amount.of.recommendations") Integer amountOfRecommendations,
            @ConfigProperty(name = "ci.server.url") String gitLabServerUrl,
            @ConfigProperty(name = "gitlab.oauth2.token") String gitLabAccessToken,
            @ConfigProperty(name = "recommendation.text.template") String recommendationTextTemplate,
            @ConfigProperty(name = "survey.base.url") String surveyBaseUrl,
            @ConfigProperty(name = "min.certainty.recommend.threshold") Float minimumCertaintyToRecommendThreshold) {
        gitLabApi = new GitLabApi(gitLabServerUrl, gitLabAccessToken);
        gitLabApi.setIgnoreCertificateErrors(true);
        this.mergeRequestApi = gitLabApi.getMergeRequestApi();
        this.amountOfRecommendations = amountOfRecommendations;
        this.recommendationTextTemplate = recommendationTextTemplate;
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
        var toRecommend = refactoringUnits.stream().filter(refactoringUnit -> refactoringUnit.shouldRefactor).filter(
                refactoringUnit -> refactoringUnit.shouldRefactorProbability >= minimumCertaintyToRecommendThreshold)
                .sorted().collect(Collectors.toList());

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
                refactoringUnit.wasRecommended();
                addedAmount++;
                // Gitlab can complain if we recommend stuff that is not in the diff, or
                // sometimes a 500 is given by random, we don't want to stop recommending if
                // this happens
                // so we catch this exception
            } catch (GitLabApiException glae) {
                LOG.infof("Cannot add discussion with refactoringUnit %s due to %s", refactoringUnit);
                LOG.debugf(glae, "");
            }
        }
        return resultingDiscussions;
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

    public List<Diff> diffsForProjectIdAndMergeRequestIid(Integer projectId, Integer mergeRequestIid)
            throws GitLabApiException {
        return diffsForMergeRequestAndProjectIdOrPath(projectId, mergeRequestIid);
    }

    public List<Diff> diffsForProjectPathAndMergeRequestIid(String projectPath, Integer mergeRequestIid)
            throws GitLabApiException {
        return diffsForMergeRequestAndProjectIdOrPath(projectPath, mergeRequestIid);
    }

    private Discussion createRefactorDiscussion(Object projectIdOrPath, int mergeRequestIid, RefactoringUnit refactor,
            DiffRef diffRef) throws GitLabApiException, MalformedURLException, URISyntaxException {

        var discussionsApi = gitLabApi.getDiscussionsApi();
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

    public String generateDescription(RefactoringUnit predictionResult)
            throws MalformedURLException, URISyntaxException {
        return String.format(recommendationTextTemplate, predictionResult.unitName,
                generateSurveyUrl(predictionResult));
    }

    public URL generateSurveyUrl(RefactoringUnit predictionResult) throws MalformedURLException, URISyntaxException {
        return new URIBuilder(surveyBaseUrl).addParameter(survalyzerUrlVariable(1), predictionResult.unitName)
                .addParameter(survalyzerUrlVariable(2), predictionResult.id.toString()).build().toURL();
    }

    public List<MergeRequest> getOpenedMergeRequests(Integer projectId) throws GitLabApiException {
        var filter = new MergeRequestFilter();
        filter.setState(MergeRequestState.OPENED);
        filter.setProjectId(projectId);
        return mergeRequestApi.getMergeRequests(filter);
    }

    public MergeRequest getMergeRequestByIid(Integer projectId, Integer mergeRequestIid) throws GitLabApiException {
        return mergeRequestApi.getMergeRequest(projectId, mergeRequestIid);
    }

    private String survalyzerUrlVariable(Integer number) {
        return String.format(Locale.US, SURVALYZER_URL_VAR_IDENTIFIER, number);
    }

}
