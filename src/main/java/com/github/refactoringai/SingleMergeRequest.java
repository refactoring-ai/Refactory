package com.github.refactoringai;

import javax.inject.Inject;

import com.github.refactoringai.refactory.GitLab;
import com.github.refactoringai.refactory.Poller;

import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;

@QuarkusMain(name = "singleMergeRequest")
public class SingleMergeRequest {
    /**
     * This method starts the recommendation pipeline
     * 
     * @param args None as of yet
     */
    public static void main(String... args) {
        try {
            Quarkus.run(SingeMergeRequestApplication.class, args);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static class SingeMergeRequestApplication implements QuarkusApplication {

        @Inject
        Poller poller;

        @Inject
        GitLab gitlab;

        @Override
        public int run(String... args) throws Exception {
            if (args.length < 2) {
                return 1;
            }
            var projectId = Integer.parseInt(args[0]);
            var mergeRequestIid = Integer.parseInt(args[1]);

            poller.processMergeRequest(gitlab.getMergeRequestByIid(projectId, mergeRequestIid));
            return 0;
        }

    }
}
