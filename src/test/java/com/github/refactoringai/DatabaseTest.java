package com.github.refactoringai;

import javax.transaction.Transactional;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class DatabaseTest {

    @Test
    @Transactional
    void testInsertRefactors() {
        // var refactors = List.of(new Refactor("path", "", 1333, "test", true),
        //         new Refactor("path", "", 1333, "test", false));
        // for (Refactor refactor : refactors) {
        //     refactor.persist();
        //     assertNotNull(refactor.id);
        // }
    }
}
