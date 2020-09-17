package com.github.refactoringai.refactory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import com.github.mauricioaniche.ck.CKClassResult;
import com.github.mauricioaniche.ck.CKMethodResult;

import org.jboss.logging.Logger;

public class Model {

    private static final Logger LOG = Logger.getLogger(Model.class);

    private static final String START_LINE_FEATURE_NAME = "startLine";
    private static final String IS_INNER_CLASS_FEATURE_NAME = "isInnerClass";
    private static final int CLASS_REFACTOR_LINE_NUMBER = 1;

    private final Path pythonPath;
    private final Path modelPath;
    private final Path scalerPath;
    private final Scope scope;
    private final String modelMessage;
    private final List<String> requiredFeatures;

    /**
     * @param pythonPath
     * @param modelPath
     * @param scalerPath
     * @param scope
     * @param modelMessage
     * @param requiredFeatures
     */
    public Model(Path pythonPath, Path modelPath, Path scalerPath, Scope scope, String modelMessage,
            List<String> requiredFeatures) {
        this.pythonPath = pythonPath;
        this.modelPath = modelPath;
        this.scalerPath = scalerPath;
        this.scope = scope;
        this.modelMessage = modelMessage;
        this.requiredFeatures = requiredFeatures;
    }

    public Collection<Refactor> execute(Collection<Sample> samples) throws IOException, InterruptedException {
        List<ModelInputSample> inputSamples = createModelSamples(samples);

        String predictScript = getPredictScriptFromResources();
        Process p = startProcess(predictScript);

        try {
            writeSamplesToScript(p, inputSamples);
            var result = parseResultFromStdIn(p, inputSamples);
            waitForSuccess(p);
            return result;
        } catch (IOException ioe) {
            tryReadStdErr(p);
            throw ioe;
        }
    }

    private List<ModelInputSample> createModelSamples(Collection<Sample> samples) {
        var result = new ArrayList<ModelInputSample>();
        for (var sample : samples) {
            if (scope == Scope.CLASS) {
                var rawSample = getMetrics(sample.getMetrics(), null);
                result.add(new ModelInputSample(sample, rawSample, CLASS_REFACTOR_LINE_NUMBER));
            } else {
                for (CKMethodResult methodMetrics : sample.getMetrics().getMethods()) {
                    var rawSample = getMetrics(sample.getMetrics(), methodMetrics);
                    result.add(new ModelInputSample(sample, rawSample, getStartLine(methodMetrics)));
                }
            }
        }
        return result;
    }

    private String getPredictScriptFromResources() throws IOException {
        String predictScript;
        try (BufferedReader bf = new BufferedReader(
                new InputStreamReader(Predictor.class.getResourceAsStream("/META-INF/predict.py")))) {
            predictScript = bf.lines().collect(Collectors.joining(System.lineSeparator()));
        }
        return predictScript;
    }

    private Process startProcess(String predictScript) throws IOException {
        var processBuilder = new ProcessBuilder(pythonPath.toString(), "-c", predictScript, scalerPath.toString(),
                modelPath.toString());
        try {
            return processBuilder.start();
        } catch (IOException e) {
            LOG.error("Could not start python predictor. Processbuilder is %s", processBuilder, e);
            throw e;
        }
    }

    private void writeSamplesToScript(Process p, List<ModelInputSample> inputSamples) throws IOException {
        try (var bf = new BufferedWriter(new OutputStreamWriter(p.getOutputStream()))) {
            for (ModelInputSample sample : inputSamples) {
                for (Number metric : sample.getModelInput()) {
                    bf.append(String.valueOf(metric));
                    bf.append(' ');
                }
                bf.newLine();
            }
        }
    }

    private void waitForSuccess(Process p) throws IOException, InterruptedException {
        int exitCode = p.waitFor();
        if (exitCode != 0) {
            throw new IOException(String.format("Exit code of predictor was %d rather than %d", exitCode, 0));
        }
    }

    private Collection<Refactor> parseResultFromStdIn(Process p, List<ModelInputSample> inputSamples)
            throws IOException {
        List<Boolean> rawPredictions;
        try (var br = new BufferedReader(new InputStreamReader(p.getInputStream())); var linesStream = br.lines()) {
            rawPredictions = linesStream.map(Boolean::parseBoolean).collect(Collectors.toList());
        }
        var refactors = new ArrayList<Refactor>();
        for (int i = 0; i < rawPredictions.size(); i++) {
            var correspondingSample = inputSamples.get(i);
            var correspondingOriginalSample = correspondingSample.getOriginalSample();
            var refactor = new Refactor(correspondingOriginalSample.getOldPath().toString(),
                    correspondingOriginalSample.getNewPath().toString(),
                    correspondingSample.getCorrespondingLineNumber(), modelMessage, rawPredictions.get(i));
            refactors.add(refactor);
        }
        return refactors;
    }

    private void tryReadStdErr(Process p) {
        try (var br = new BufferedReader(new InputStreamReader(p.getErrorStream()))) {
            var err = br.lines().collect(Collectors.joining("\n"));
            LOG.errorf("Predictor failed. std err is %s", err);
        } catch (IOException ioe) {
            LOG.errorf("Could not read std err after exception. %s", ioe);
        }
    }

    private List<Number> getMetrics(CKClassResult ckResult, CKMethodResult methodMetrics) {
        var result = new ArrayList<Number>(requiredFeatures.size());

        for (String featureName : requiredFeatures) {
            // Nice to have: all this pattern matching is a bit of a mess, a normal api
            // (e.g. i call the identifier on te class, method, etc method will give me the
            // right metric) that is the
            // same across projects (CK data collection, machine learning and this) would be
            // better.
            Double feature;
            if (featureName.startsWith("class") || featureName.equals(IS_INNER_CLASS_FEATURE_NAME)) {
                feature = classMetricForName(featureName, ckResult);
            } else if (featureName.startsWith("method") || featureName.equals(START_LINE_FEATURE_NAME)) {
                feature = methodMetricForName(featureName, methodMetrics);
            } else if (featureName.startsWith("variable")) {
                feature = variableMetricForName(featureName, methodMetrics);
            } else if (featureName.startsWith("field")) {
                feature = fieldMetricForName(featureName, methodMetrics);
            } else {
                throw new IllegalArgumentException(String.format("Not a supported metric %s", featureName));
            }
            if (Double.isNaN(feature) || Double.isInfinite(feature)) {
                feature = 0d; // TODO enquire whether this is ok to do, done to prevent errors in sklearn
                              // predict
            }
            result.add(feature);
        }

        return result;
    }

    // TODO its still unclear if we need this maybe we can remove this later
    enum Scope {
        CLASS, METHOD, VARIABLE, ATTRIBUTE, FIELD, OTHER;
    }

    private double classMetricForName(String metricName, CKClassResult metrics) {
        switch (metricName) {
            case "classAnonymousClassesQty":
                return metrics.getAnonymousClassesQty();
            case "classAssignmentsQty":
                return metrics.getAssignmentsQty();
            case "classCbo":
                return metrics.getCbo();
            case "classComparisonsQty":
                return metrics.getComparisonsQty();
            case "classLambdasQty":
                return metrics.getLambdasQty();
            case "classLcom":
                return metrics.getLcom();
            case "classLoc":
                return metrics.getLoc();
            case "classLCC":
                return metrics.getLooseClassCohesion();
            case "classLoopQty":
                return metrics.getLoopQty();
            case "classMathOperationsQty":
                return metrics.getMathOperationsQty();
            case "classMaxNestedBlocks":
                return metrics.getMaxNestedBlocks();
            case "classNosi":
                return metrics.getNosi();
            case "classNumberOfAbstractMethods":
                return metrics.getNumberOfAbstractMethods();
            case "classNumberOfDefaultFields":
                return metrics.getNumberOfDefaultFields();
            case "classNumberOfDefaultMethods":
                return metrics.getNumberOfDefaultMethods();
            case "classNumberOfFields":
                return metrics.getNumberOfFields();
            case "classNumberOfFinalFields":
                return metrics.getNumberOfFinalFields();
            case "classNumberOfFinalMethods":
                return metrics.getNumberOfFinalMethods();
            case "classNumberOfMethods":
                return metrics.getNumberOfMethods();
            case "classNumberOfPrivateFields":
                return metrics.getNumberOfPrivateFields();
            case "classNumberOfPrivateMethods":
                return metrics.getNumberOfPrivateMethods();
            case "classNumberOfProtectedFields":
                return metrics.getNumberOfProtectedFields();
            case "classNumberOfProtectedMethods":
                return metrics.getNumberOfProtectedMethods();
            case "classNumberOfPublicFields":
                return metrics.getNumberOfPublicFields();
            case "classNumberOfPublicMethods":
                return metrics.getNumberOfPublicMethods();
            case "classNumberOfStaticFields":
                return metrics.getNumberOfStaticFields();
            case "classNumberOfStaticMethods":
                return metrics.getNumberOfStaticMethods();
            case "classNumberOfSynchronizedFields":
                return metrics.getNumberOfSynchronizedFields();
            case "classNumberOfSynchronizedMethods":
                return metrics.getNumberOfSynchronizedMethods();
            case "classNumbersQty":
                return metrics.getNumbersQty();
            case "classParenthesizedExpsQty":
                return metrics.getParenthesizedExpsQty();
            case "classReturnQty":
                return metrics.getReturnQty();
            case "classRfc":
                return metrics.getRfc();
            case "classStringLiteralsQty":
                return metrics.getStringLiteralsQty();
            case "classSubClassesQty":
                return metrics.getInnerClassesQty();
            case "classTryCatchQty":
                return metrics.getTryCatchQty();
            case "classUniqueWordsQty":
                return metrics.getUniqueWordsQty();
            case "classVariablesQty":
                return metrics.getVariablesQty();
            case "classWmc":
                return metrics.getWmc();
            case "classTCC":
                return metrics.getTightClassCohesion();
            case IS_INNER_CLASS_FEATURE_NAME:
                return metrics.getType().equals("innerclass") ? 1 : 0;
            default:
                throw new IllegalArgumentException("No such metric in CK class result");
        }
    }

    private int getStartLine(CKMethodResult metrics) {
        return (int) methodMetricForName(START_LINE_FEATURE_NAME, metrics);
    }

    private double methodMetricForName(String metricName, CKMethodResult metrics) {
        switch (metricName) {
            case "methodAnonymousClassesQty":
                return metrics.getAnonymousClassesQty();
            case "methodAssignmentsQty":
                return metrics.getAssignmentsQty();
            case "methodCbo":
                return metrics.getCbo();
            case "methodComparisonsQty":
                return metrics.getComparisonsQty();
            case "methodLambdasQty":
                return metrics.getLambdasQty();
            case "methodLoc":
                return metrics.getLoc();
            case "methodLoopQty":
                return metrics.getLoopQty();
            case "methodMathOperationsQty":
                return metrics.getMathOperationsQty();
            case "methodMaxNestedBlocks":
                return metrics.getMaxNestedBlocks();
            case "methodNumbersQty":
                return metrics.getNumbersQty();
            case "methodParametersQty":
                return metrics.getParametersQty();
            case "methodParenthesizedExpsQty":
                return metrics.getParenthesizedExpsQty();
            case "methodReturnQty":
                return metrics.getReturnQty();
            case "methodRfc":
                return metrics.getRfc();
            case "methodStringLiteralsQty":
                return metrics.getStringLiteralsQty();
            case "methodSubClassesQty":
                return metrics.getInnerClassesQty();
            case "methodTryCatchQty":
                return metrics.getTryCatchQty();
            case "methodUniqueWordsQty":
                return metrics.getUniqueWordsQty();
            case "methodVariablesQty":
                return metrics.getVariablesQty();
            case "methodWmc":
                return metrics.getWmc();
            case START_LINE_FEATURE_NAME:
                return metrics.getStartLine();
            default:
                throw new IllegalArgumentException("No such metric in CK method result");
        }
    }

    private double fieldMetricForName(String metricName, CKMethodResult metrics) {
        return metrics.getFieldUsage().get(metricName);
    }

    private double variableMetricForName(String metricName, CKMethodResult metrics) {
        return metrics.getVariablesUsage().get(metricName);
    }
}
