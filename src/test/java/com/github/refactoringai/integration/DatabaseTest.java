package com.github.refactoringai.integration;

import javax.transaction.Transactional;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class DatabaseTest {

    @Test
    @Transactional
    void testInsertRefactors() {
        
        // for (Refactor refactor : refactors) {
        //     refactor.persist();
        //     assertNotNull(refactor.id);
        // }
    }
}
