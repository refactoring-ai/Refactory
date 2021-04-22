package com.github.refactoringai;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;
import java.util.stream.Collectors;

import javax.inject.Inject;

import com.github.refactoringai.refactory.Poller;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.gitlab4j.api.GitLabApi;
import org.gitlab4j.api.GitLabApiException;
import org.gitlab4j.api.MergeRequestApi;
import org.gitlab4j.api.ProjectApi;
import org.gitlab4j.api.models.MergeRequest;
import org.gitlab4j.api.models.MergeRequestFilter;
import org.gitlab4j.api.models.Project;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import ai.onnxruntime.OrtException;
import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class SmokeTest {

    private static final int MOCK_GITLAB_PROJECT_ID = 42;
    private static final int MOCK_GITLAB_MERGE_REQUEST_IID1 = 145;
    private static final int MOCK_GITLAB_MERGE_REQUEST_IID2 = 146;
    private static final List<Integer> MOCK_GITLAB_MERGE_REQUEST_IIDS = List.of(MOCK_GITLAB_MERGE_REQUEST_IID1,
            MOCK_GITLAB_MERGE_REQUEST_IID2);

    private static final String MOCK_GITLAB_PROJECT_PATH = "mock-project";
    private static final String MOCK_GITLAB_PROJECT_NAME = "mock-project";

    @BeforeAll
    public static void setup() throws GitLabApiException {
        var gitlabApiMock = Mockito.mock(GitLabApi.class);
        var gitlabProjectMock = Mockito.mock(Project.class);
        Mockito.when(gitlabProjectMock.getPath()).thenReturn(MOCK_GITLAB_PROJECT_PATH);
        Mockito.when(gitlabProjectMock.getId()).thenReturn(MOCK_GITLAB_PROJECT_ID);
        Mockito.when(gitlabProjectMock.getName()).thenReturn(MOCK_GITLAB_PROJECT_NAME);

        var projectApiMock = Mockito.mock(ProjectApi.class);
        Mockito.when(projectApiMock.getProject(anyInt())).thenReturn(gitlabProjectMock);
        Mockito.when(gitlabApiMock.getProjectApi()).thenReturn(projectApiMock);

        var mergeRequestApiMock = Mockito.mock(MergeRequestApi.class);
        var mockMergeRequests = MOCK_GITLAB_MERGE_REQUEST_IIDS.stream().map(SmokeTest::mockMergeRequest)
                .collect(Collectors.toList());
        Mockito.when(mergeRequestApiMock.getMergeRequests(any(MergeRequestFilter.class))).thenReturn(mockMergeRequests);

        mockMergeRequests
                .forEach(mockMergeRequest -> mockMergeRequestWithDiffRefs(mergeRequestApiMock, mockMergeRequest));

        Mockito.when(gitlabApiMock.getMergeRequestApi()).thenReturn(mergeRequestApiMock);
        QuarkusMock.installMockForType(gitlabApiMock, GitLabApi.class);
    }

    private static MergeRequest mockMergeRequest(int iid) {
        var mergeRequestApiMock = Mockito.mock(MergeRequest.class);
        Mockito.when(mergeRequestApiMock.getIid()).thenReturn(iid);
        return mergeRequestApiMock;
    }

    private static MergeRequest mockMergeRequestWithDiffRefs(MergeRequestApi mergeRequestApiMock,
            MergeRequest mergeRequestMock) {
        var mockedIid = mergeRequestMock.getIid();
        var mergeRequestApiMockWithDiffReffs = Mockito.mock(MergeRequest.class);
        Mockito.when(mergeRequestApiMockWithDiffReffs.getIid()).thenReturn(mockedIid);
        try {
            Mockito.when(mergeRequestApiMock.getMergeRequest(anyInt(), Mockito.eq(mockedIid)))
                    .thenReturn(mergeRequestMock);
        } catch (GitLabApiException glae) {
            throw new RuntimeException(glae);
        }
        return mergeRequestApiMockWithDiffReffs;
    }

    @Inject
    Poller poller;

    @Test
    void testTest() throws GitLabApiException, IOException, GitAPIException, OrtException, URISyntaxException {
        poller.poll();
    }

}
