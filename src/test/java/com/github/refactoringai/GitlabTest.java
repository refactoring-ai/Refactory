package com.github.refactoringai;

import javax.inject.Inject;

import com.github.refactoringai.refactory.Gitlab;

import org.gitlab4j.api.GitLabApiException;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class GitlabTest {

    @Inject
    Gitlab gitlabHelper;

    @Test
    void testMakeComment() throws GitLabApiException {
        // var refactor = Refactor.refactorFactory(
        //         "benchmarks/src/main/java/org/elasticsearch/benchmark/indices/breaker/MemoryStatsBenchmark.java", 4,
        //         "test");
        // gitlabHelper.comment("geert", List.of(refactor));
    }
}
