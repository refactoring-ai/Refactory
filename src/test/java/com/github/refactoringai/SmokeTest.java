package com.github.refactoringai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import javax.inject.Inject;

import com.github.refactoringai.refactory.Poller;

import org.eclipse.jgit.api.errors.GitAPIException;
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
import org.gitlab4j.api.models.Project;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import ai.onnxruntime.OrtException;
import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class SmokeTest {

    private static final Integer MOCK_GITLAB_PROJECT_ID = 42;
    private static final Integer MOCK_GITLAB_MERGE_REQUEST_IID = 145;

    private static final String MOCK_GITLAB_PROJECT_PATH = "mockproject";
    private static final String MOCK_GITLAB_PROJECT_NAME = "mockproject";
    private static final String MOCK_GIT_SHA = "af81d4c7083a2f23d1aa66ed435765148708307a";
    private static final Path MOCK_TEST_JAVA_FILES_PATH = Paths.get("src/mockcompany");

    @Inject
    Poller poller;

    /**
     * Tests whether
     * @throws GitLabApiException
     * @throws IOException
     * @throws GitAPIException
     * @throws OrtException
     * @throws URISyntaxException
     */
    @Test
    void testRefactors() throws GitLabApiException, IOException, GitAPIException, OrtException, URISyntaxException {
        final Path MOCK_REFACTOR_PATH = MOCK_TEST_JAVA_FILES_PATH.resolve("MockJavaFileRefactor.java");
        setupAndInstallGitLabApiMock(MOCK_REFACTOR_PATH, MOCK_GIT_SHA);
        List<Discussion> gitlabDiscussions = poller.poll();
        assertEquals(3, gitlabDiscussions.size());
    }

    @Test
    void testNoRefactors() throws GitLabApiException, IOException, GitAPIException, OrtException, URISyntaxException {
        final Path MOCK_NO_REFACTOR_PATH = MOCK_TEST_JAVA_FILES_PATH.resolve("MockJavaFileNoRefactor.java");
        setupAndInstallGitLabApiMock(MOCK_NO_REFACTOR_PATH, MOCK_GIT_SHA);
        List<Discussion> gitlabDiscussions = poller.poll();
        assertEquals(0, gitlabDiscussions.size());
    }

    private static void setupAndInstallGitLabApiMock(Path changedFilePath, String commitSha) throws GitLabApiException {
        var gitLabApiMock = setupGitLabMock(changedFilePath, commitSha);
        QuarkusMock.installMockForType(gitLabApiMock, GitLabApi.class);
    }

    private static GitLabApi setupGitLabMock(Path changedFilePath, String commitSha) throws GitLabApiException {
        // Mock the api
        var gitlabApiMock = Mockito.mock(GitLabApi.class);

        // Create fake project and make projectapi return it, then make gitlabApi return that api
        var gitlabProjectMock = new Project();
        gitlabProjectMock.setPath(MOCK_GITLAB_PROJECT_PATH);
        gitlabProjectMock.setId(MOCK_GITLAB_PROJECT_ID);
        gitlabProjectMock.setName(MOCK_GITLAB_PROJECT_NAME);
        var projectApiMock = Mockito.mock(ProjectApi.class);
        Mockito.when(projectApiMock.getProject(anyInt())).thenReturn(gitlabProjectMock);
        Mockito.when(gitlabApiMock.getProjectApi()).thenReturn(projectApiMock);

        // Mock discussionApi and let gitLabApi return it
        var discussionsApiMock = Mockito.mock(DiscussionsApi.class);
        Mockito.when(gitlabApiMock.getDiscussionsApi()).thenReturn(discussionsApiMock);


        var mergeRequestApiMock = Mockito.mock(MergeRequestApi.class);

        // Create the Merge Request without diffrefs as the behaviour of GitLab
        var mergeRequest = createMergeRequest(MOCK_GITLAB_MERGE_REQUEST_IID);
        Mockito.when(mergeRequestApiMock.getMergeRequests(any(MergeRequestFilter.class)))
                .thenReturn(List.of(mergeRequest));

        // Create mock of individually requested merge request which contain the diffref we need
        var mergeRequestWithDiffReffs = createMergeRequestWithDiffRefs(mergeRequest.getIid(), commitSha);
        try {
            Mockito.when(mergeRequestApiMock.getMergeRequest(anyInt(), Mockito.eq(mergeRequest.getIid())))
                    .thenReturn(mergeRequestWithDiffReffs);
        } catch (GitLabApiException glae) {
            throw new RuntimeException(glae);
        }

        // Create mergeRequest that also has the changes
        var mergeRequestWithChanges = createMergeRequestWithChanges(mergeRequestWithDiffReffs.getIid(),
                mergeRequestWithDiffReffs.getSha(), changedFilePath);
        Mockito.when(
                mergeRequestApiMock.getMergeRequestChanges(anyInt(), Mockito.eq(mergeRequestWithDiffReffs.getIid())))
                .thenReturn(mergeRequestWithChanges);
        Mockito.when(gitlabApiMock.getMergeRequestApi()).thenReturn(mergeRequestApiMock);
        return gitlabApiMock;
    }

    private static MergeRequest createMergeRequest(Integer iId) {
        var mergeRequest = new MergeRequest();
        mergeRequest.setIid(iId);
        return mergeRequest;
    }

    private static MergeRequest createMergeRequestWithDiffRefs(Integer iId, String commitSha) {
        var mergeRequestWithDiffReffs = createMergeRequest(iId);
        mergeRequestWithDiffReffs.setSha(commitSha);
        var diffRef = new DiffRef();
        diffRef.setBaseSha(commitSha);
        diffRef.setStartSha(commitSha);
        diffRef.setHeadSha(commitSha);
        mergeRequestWithDiffReffs.setDiffRefs(diffRef);
        return mergeRequestWithDiffReffs;
    }

    private static MergeRequest createMergeRequestWithChanges(Integer iId, String commitSha, Path changedFilePath) {
        var mergeRequestWithChanges = createMergeRequestWithDiffRefs(iId, commitSha);
        var diff = new Diff();
        diff.setOldPath("");
        diff.setNewPath(changedFilePath.toString());
        mergeRequestWithChanges.setChanges(List.of(diff));
        return mergeRequestWithChanges;
    }

}
