package com.github.refactoringai;

import javax.inject.Inject;

import com.github.refactoringai.refactory.Refactory;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class RefactoryTest {

    @Inject
    Refactory refactory;

    @Test
    void testRefactory() throws Exception {
        // assertEquals(0, refactory.run("/home/david/master-thesis/comment-test"));
    }
}
