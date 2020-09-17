package com.github.refactoringai.refactory;

import java.util.List;

public class ModelInputSample {
    private final Sample originalSample;
    private final List<Number> modelInput;
    private final int correspondingLineNumber;

    /**
     * @param originalSample
     * @param modelInput
     */
    public ModelInputSample(Sample originalSample, List<Number> modelInput, int correspondingLineNumber) {
        this.originalSample = originalSample;
        this.modelInput = modelInput;
        this.correspondingLineNumber = correspondingLineNumber;
    }

    /**
     * @return the originalSample
     */
    public Sample getOriginalSample() {
        return originalSample;
    }

    /**
     * @return the modelInput
     */
    public List<Number> getModelInput() {
        return modelInput;
    }

    /**
     * @return the correspondingLineNumber
     */
    public int getCorrespondingLineNumber() {
        return correspondingLineNumber;
    }

}