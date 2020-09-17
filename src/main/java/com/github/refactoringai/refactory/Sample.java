package com.github.refactoringai.refactory;

import java.nio.file.Path;

import com.github.mauricioaniche.ck.CKClassResult;

public class Sample {

    // The metrics resulting from CK
    private CKClassResult metrics;

    // TODO maybe just put the diff here, not done yet since that would make the
    // tool not git provide agnostic

    // The previous path of the changed file in the diff
    private Path oldPath;

    // The current path of the changed file in the diff
    private Path newPath;

    /**
     * @param metrics
     * @param oldPath
     * @param newPath
     */
    public Sample(CKClassResult metrics, Path oldPath, Path newPath) {
        this.metrics = metrics;
        this.oldPath = oldPath;
        this.newPath = newPath;
    }

    /**
     * @return the metrics
     */
    public CKClassResult getMetrics() {
        return metrics;
    }

    /**
     * @param metrics the metrics to set
     */
    public void setMetrics(CKClassResult metrics) {
        this.metrics = metrics;
    }

    /**
     * @return the oldPath
     */
    public Path getOldPath() {
        return oldPath;
    }

    /**
     * @param oldPath the oldPath to set
     */
    public void setOldPath(Path oldPath) {
        this.oldPath = oldPath;
    }

    /**
     * @return the newPath
     */
    public Path getNewPath() {
        return newPath;
    }

    /**
     * @param newPath the newPath to set
     */
    public void setNewPath(Path newPath) {
        this.newPath = newPath;
    }

}
