package com.github.refactoringai.integration;

import java.io.IOException;
import java.net.URISyntaxException;

import javax.inject.Inject;

import com.github.refactoringai.refactory.GitLab;
import com.github.refactoringai.refactory.Poller;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.gitlab4j.api.GitLabApiException;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.Test;

import ai.onnxruntime.OrtException;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class RefactoryTest {

    private static final Logger LOG = Logger.getLogger(RefactoryTest.class);

    private static final Integer TEST_PROJECT_ID = 22073873;
    private static final Integer TEST_MERGE_REQUEST_IID = 5;

    @Inject
    Poller poller;

    @Inject
    GitLab gitlab;


    @Test
    void testMergeRequest() throws GitLabApiException, IOException, GitAPIException, OrtException, URISyntaxException {
        poller.processMergeRequest(gitlab.getMergeRequestByIid(TEST_PROJECT_ID, TEST_MERGE_REQUEST_IID));
    }
}
