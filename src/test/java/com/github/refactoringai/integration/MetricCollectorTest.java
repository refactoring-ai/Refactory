package com.github.refactoringai.integration;

import javax.inject.Inject;

import com.github.refactoringai.refactory.MetricCollector;

import org.gitlab4j.api.models.Diff;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class MetricCollectorTest {

    @Inject
    MetricCollector metricCollector;

    @Test
    void testDataCollection() {
        var diff = new Diff();
        diff.setNewPath("Feature.java");
        // var diffMap = Map.of(Paths.get("Feature.java"), diff);
        // var y = metricCollector.getMetrics(Paths.get("fixtures/real-world"), diffMap);
    }
}
