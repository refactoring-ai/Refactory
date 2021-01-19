package com.github.refactoringai.integration;

import javax.inject.Inject;
import javax.json.bind.Jsonb;

import com.github.refactoringai.refactory.MetricCollector;
import com.github.refactoringai.refactory.OnnxPredictor;

import org.junit.jupiter.api.Test;

import ai.onnxruntime.OrtException;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class OnnxPredictorTest {

    @Inject
    OnnxPredictor predictor;

    @Inject
    MetricCollector metricCollector;

    @Inject
    Jsonb jsonb;

    @Test
    void testPredict() throws OrtException {
        // var diff = new Diff();
        // diff.setNewPath("Feature.java");
        // var diffMap = Map.of(Paths.get("Feature.java"), diff);
        // var y = metricCollector.getMetrics(Paths.get("fixtures/real-world"), diffMap);
        // Model model = new Model(Paths.get("models/pipeline.onnx"), jsonb);
        // // model.modelPath = ;
        // var z = predictor.predict(y, model, new RefactoryMergeRequest());
        // System.out.println();
    }
}
