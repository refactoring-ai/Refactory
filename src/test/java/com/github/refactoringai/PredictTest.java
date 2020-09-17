package com.github.refactoringai;

import java.io.IOException;

import javax.inject.Inject;

import com.github.refactoringai.refactory.Predictor;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class PredictTest {

    @Inject
    Predictor predictorHelper;

    @Test
    void testPredict() throws IOException, InterruptedException {
        // predictorHelper.predict();
    }
}
