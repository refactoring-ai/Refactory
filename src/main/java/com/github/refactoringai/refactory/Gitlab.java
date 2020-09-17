package com.github.refactoringai.refactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.gitlab4j.api.GitLabApi;
import org.gitlab4j.api.GitLabApiException;
import org.gitlab4j.api.MergeRequestApi;
import org.gitlab4j.api.models.Diff;
import org.gitlab4j.api.models.DiffRef;
import org.gitlab4j.api.models.Discussion;
import org.gitlab4j.api.models.Position;
import org.gitlab4j.api.models.Position.PositionType;
import org.jboss.logging.Logger;

@ApplicationScoped
public class Gitlab {

    private static final Logger LOG = Logger.getLogger(Gitlab.class);

    @Inject
    GitLabApi gitLabApi;

    /**
     * Creates discussions on the new paths of the merge request diffs. This method
     * does not check if {@link Refactor#shouldRefactor} is true and creates the
     * discussions for all passed refactors. it is the responsibillity of the caller
     * to check this.
     * 
     * @param projectPath    The GitLab project on which to operate.
     * @param mergeRequestId the id of the merge request to operate on.
     * @param refactors      The refactors for which to create recommendations
     * @return The created Discussions
     * @throws GitLabApiException If fetching information or placing discussions
     *                            fails
     */
    public Collection<Discussion> createDiscussionsFromRefactors(String projectPath, int mergeRequestId,
            Collection<Refactor> refactors) throws GitLabApiException {
        var diffRef = mergeRequestApi().getMergeRequest(projectPath, mergeRequestId).getDiffRefs();
        var resultingDiscussions = new ArrayList<Discussion>();
        for (Refactor refactor : refactors) {
            resultingDiscussions.add(createRefactorDiscussion(projectPath, mergeRequestId, refactor, diffRef));
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
    public List<Diff> diffs(String projectPath, int mergeRequestId) throws GitLabApiException {
        return mergeRequestApi().getMergeRequestChanges(projectPath, mergeRequestId).getChanges();
    }

    // TODO turn MergeRequestApi into a bean
    /**
     * Convienciene method to get the MergeRequestApi from the gitlabApi
     * 
     * @return
     */
    private MergeRequestApi mergeRequestApi() {
        return gitLabApi.getMergeRequestApi();
    }

    private Discussion createRefactorDiscussion(String projectPath, int mergeRequestId, Refactor refactor,
            DiffRef diffRef) throws GitLabApiException {
        var discussionsApi = gitLabApi.getDiscussionsApi();
        var position = new Position();
        position.setBaseSha(diffRef.getBaseSha());
        position.setHeadSha(diffRef.getHeadSha());
        position.setStartSha(diffRef.getStartSha());
        position.setPositionType(PositionType.TEXT);

        position.setNewLine(refactor.lineNumber);
        position.setOldPath(refactor.oldPath);
        position.setNewPath(refactor.newPath);
        try {
            return discussionsApi.createMergeRequestDiscussion(projectPath, mergeRequestId, refactor.description, null,
                    null, position);
        } catch (GitLabApiException e) {
            LOG.errorf("Cannot add discussion with refactor %s", refactor);
            throw e;
        }
    }
}
