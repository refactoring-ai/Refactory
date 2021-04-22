package com.github.refactoringai.tools;

import javax.inject.Inject;

import com.github.refactoringai.refactory.GitLab;

import org.jboss.logging.Logger;

import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.QuarkusApplication;

public class ThresholdDeterminer {

    public static void main(String... args) {
        try {
            Quarkus.run(ThresholdDeterminerApplication.class, args);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static class ThresholdDeterminerApplication implements QuarkusApplication {

        private static final Logger LOG = Logger.getLogger(ThresholdDeterminerApplication.class);

        @Inject
        GitLab gitlab;

        @Override
        public int run(String... args) throws Exception {
            if (args.length != 1) {
                throw new IllegalArgumentException("Please pass a projectIdOrPath");
            }

            double med = gitlab.medianCommitsPerMergeRequest(args[0]);
            LOG.infof("{median: %f}", med);
            return 0;
        }

    }
}
