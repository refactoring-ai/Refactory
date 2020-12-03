package com.github.refactoringai.integration;

import javax.inject.Inject;

import com.github.refactoringai.refactory.GitLab;

import org.gitlab4j.api.GitLabApiException;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class GitlabTest {

    @Inject
    GitLab gitlab;

    @Test
    void testMakeComment() throws GitLabApiException {
    }
}
