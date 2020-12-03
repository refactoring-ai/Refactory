package com.github.refactoringai.refactory;

import java.util.List;
import java.util.Map;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.json.bind.Jsonb;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.refactoringai.refactory.entities.Model;
import com.github.refactoringai.refactory.entities.RefactoringUnit;
import com.google.common.base.Preconditions;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import ai.onnxruntime.OrtSession.Result;

@ApplicationScoped
public class OnnxPredictor {

    private static final String FLOAT_INPUT_IDENTIFIER_ONNX = "float_input";
    private static final long BOOLEAN_LONG_TRUE_REPRESENTATION = 1L;
    private static final int PREDICTION_LABEL_INDEX = 0;
    private static final int PREDICTION_PROBABILITY_INDEX = 1;
    private static final int TRUE_PREDICTION_PROBABILITY_INDEX = 1;
    private static final int FALSE_PREDICTION_PROBABILITY_INDEX = 0;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    Jsonb jsonb;

    public Model buildModel(String modelPathStr, OrtSession session) throws OrtException {
        String modelDescription = session.getMetadata().getDescription();
        var model = jsonb.fromJson(modelDescription, Model.class);
        model.modelPath = modelPathStr;
        return model;
    }

    public void predict(OrtEnvironment env, OrtSession session, List<RefactoringUnit> samples) throws OrtException {
        Preconditions.checkArgument(!samples.isEmpty(), "No samples to predict for");
        float[][] xs = samples.stream().map(refactoringUnit -> refactoringUnit.input).toArray(float[][]::new);

        var input = Map.of(FLOAT_INPUT_IDENTIFIER_ONNX, OnnxTensor.createTensor(env, xs));
        Result rawResult = session.run(input);

        long[] ysLong = (long[]) rawResult.get(PREDICTION_LABEL_INDEX).getValue();
        var ysProbabilitiesObj = rawResult.get(PREDICTION_PROBABILITY_INDEX).getValue();

        for (int i = 0; i < ysLong.length; i++) {
            RefactoringUnit result = samples.get(i);
            long longLabel = ysLong[i];
            boolean boolLabel = longLabel == BOOLEAN_LONG_TRUE_REPRESENTATION;
            result.shouldRefactor = boolLabel;
            // TODO save both, they are not always probabillities but also distance from
            // lines in some models

            if (ysProbabilitiesObj instanceof float[][]) {
                var ysProbabilitiesFloatArray = (float[][]) ysProbabilitiesObj;
                result.shouldRefactorProbability = boolLabel
                        ? ysProbabilitiesFloatArray[i][TRUE_PREDICTION_PROBABILITY_INDEX]
                        : ysProbabilitiesFloatArray[i][FALSE_PREDICTION_PROBABILITY_INDEX];
            } else {
                var ysProbabilitiesMap = (List<Map<Long, Float>>) ysProbabilitiesObj;
                result.shouldRefactorProbability = ysProbabilitiesMap.get(i).get(longLabel);
                ;
            }
        }

    }

}