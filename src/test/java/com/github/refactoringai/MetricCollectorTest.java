package com.github.refactoringai;

import javax.inject.Inject;

import com.github.refactoringai.refactory.MetricCollector;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class MetricCollectorTest {

    @Inject
    MetricCollector metricCollector;

    @Test
    void testDataCollection() {
        // metricCollector.getMetrics("/home/david/comment-test", "/home/david/comment-test/src/main/java/org/elasticsearch/client");
    }
}
