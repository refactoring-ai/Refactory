package com.github.refactoringai;

import java.io.IOException;
import java.net.URISyntaxException;

import javax.inject.Inject;

import com.github.refactoringai.refactory.Poller;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.gitlab4j.api.GitLabApiException;
import org.jboss.logging.Logger;

import ai.onnxruntime.OrtException;
import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;
import io.quarkus.scheduler.Scheduled;
import io.quarkus.scheduler.Scheduler;

@QuarkusMain(name = "poller")
public class PollerPredictor {

    /**
     * This method starts the recommendation pipeline
     * 
     * @param args None as of yet
     */
    public static void main(String... args) {
        try {
            Quarkus.run(PollerApplication.class, args);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static class PollerApplication implements QuarkusApplication {

        private static final Logger LOG = Logger.getLogger(PollerApplication.class);

        @Inject
        Scheduler scheduler;

        @Inject
        Poller poller;

        @ConfigProperty(name = "scheduler.enabled")
        Boolean schedulerEnabled;

        @Override
        public int run(String... args) throws Exception {
            LOG.info("Starting to poll. press CTRL+C to stop.");
            waitt();
            return 0;
        }

        public synchronized void waitt() throws InterruptedException {
            while (true) {
                this.wait(); // TODO find better way than this to stop program from finishing
            }
        }

        @Scheduled(every = "20s")
        public void pollForMergeRequests()
                throws GitLabApiException, IOException, GitAPIException, OrtException, URISyntaxException {
            if (scheduler.isRunning() && schedulerEnabled) {
                scheduler.pause();
                try {
                    poller.poll();
                } finally {
                    scheduler.resume();
                }
            }
        }

    }
}