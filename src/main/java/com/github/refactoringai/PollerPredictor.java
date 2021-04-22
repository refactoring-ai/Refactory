package com.github.refactoringai;

import javax.inject.Inject;

import com.github.refactoringai.refactory.Poller;

import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;

@QuarkusMain
public class PollerPredictor {

    /**
     * This method starts the recommendation pipeline
     * 
     * @param args None as of yet
     */
    public static void main(String... args) {
        Quarkus.run(PollerApplication.class, args);
    }

    public static class PollerApplication implements QuarkusApplication {

        @Inject
        Poller poller;

        @Override
        public int run(String... args) throws Exception {
            blockMainThread();
            return 0;
        }

        public synchronized void blockMainThread() throws InterruptedException {
            while (true) {
                this.wait(); // TODO find better way than this to stop program from finishing (if there is
                             // one)
            }
        }

    }
}