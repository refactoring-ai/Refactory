package com.github.refactoringai.refactory;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.mauricioaniche.ck.CK;
import com.github.mauricioaniche.ck.CKClassResult;
import com.github.refactoringai.refactory.entities.Model;
import com.github.refactoringai.refactory.entities.RefactoringUnit;
import com.google.common.base.Preconditions;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.gitlab4j.api.models.Diff;
import org.jboss.logging.Logger;

@ApplicationScoped
public class MetricCollector {
    private static final List<String> PREFIXES_TO_REMOVE = List.of("class", "method");
    private static final int CLASS_START_LINE = 1;
    private static final Logger LOG = Logger.getLogger(MetricCollector.class);

    @ConfigProperty(name = "min.method.loc", defaultValue = "1")
    Integer minMethodLoc;

    @Inject
    ObjectMapper objectMapper;

    /**
     * Executes CK on the files changed in the list of diffs. First the non-Java
     * files are filtered out. Keep in mind that CK also analyses subclasses and
     * therefore the amount of files is not equal to the amount of samples
     * 
     * @param repoPath The location of the repository to analyse
     * @param diffs    The diffs which contain information on the changed files
     * @return The metrics with path information
     */
    public List<RefactoringUnit> getMetrics(Path repoPath, Map<Path, Diff> diffMap, Model model) {
        var ck = new CK(false, 0, true);
        List<RefactoringUnit> samples = new ArrayList<>();
        ck.calculate(repoPath, res -> fromCkClassResult(repoPath, res, samples, diffMap, model),
                diffMap.values().stream().map(Diff::getNewPath).map(Paths::get).toArray(Path[]::new));

        return samples;
    }

    private void fromCkClassResult(Path repoPath, CKClassResult ckClassResult, Collection<RefactoringUnit> samples,
            Map<Path, Diff> diffMap, Model model) {
        var diff = diffMap.get(relativize(repoPath, ckClassResult));

        List<String> featureNames = model.featureNames;
        toRawSample(featureNames, ckClassResult).ifPresent(input -> samples.add(RefactoringUnit
                .createRefactoringUnit(diff, model, CLASS_START_LINE, ArrayUtils.toObject(input), ckClassResult.getClassName())));

        var methods = ckClassResult.getMethods();
        methods = methods.stream().filter(method -> method.getLoc() >= minMethodLoc).collect(Collectors.toSet());
        for (var ckMethodResult : methods) {
            toRawSample(featureNames, ckClassResult, ckMethodResult)
                    .ifPresent(input -> samples.add(RefactoringUnit.createRefactoringUnit(diff, model,
                            ckMethodResult.getStartLine(), ArrayUtils.toObject(input), ckMethodResult.getMethodName().split("/")[0])));
        }

    }

    private Path relativize(Path repoPath, CKClassResult classResult) {
        return repoPath.relativize(Paths.get(classResult.getFile()));
    }

    private String changeMachineLearningFeatureNameToCKFeatureName(String featureName) {
        var prefixesToRemove = getPrefixToRemoveFromFeatureName();
        for (String prefix : prefixesToRemove) {
            featureName = StringUtils.removeStart(featureName, prefix);
        }

        if (featureName.equals("LCC")) {
            return "looseClassCohesion";
        } else if (featureName.equals("TCC")) {
            return "tightClassCohesion";
        } else if (featureName.equals("SubClassesQty")) {
            return "innerClassesQty";
        }

        return StringUtils.uncapitalize(featureName);
    }

    private List<String> getPrefixToRemoveFromFeatureName() {
        return PREFIXES_TO_REMOVE;
    }

    private Optional<float[]> toRawSample(List<String> featureNames, Object... ckResults) {
        Preconditions.checkNotNull(featureNames, "FeatureNames was null when converting to raw sample.");
        var featureNamesN = featureNames.size();
        var dataset = new float[featureNamesN];

        var maps = Stream.of(ckResults).map(this::ckResultToMap).collect(Collectors.toList());

        featureNames = featureNames.stream().map(this::changeMachineLearningFeatureNameToCKFeatureName)
                .collect(Collectors.toList());
        for (int i = 0; i < featureNamesN; i++) {
            String featureName = featureNames.get(i);
            var found = false;
            for (Map<String, Number> map : maps) {
                if (map.containsKey(featureName)) {
                    found = true;
                    dataset[i] = map.get(featureName).floatValue();
                }
            }
            if (!found) {
                LOG.debugf("Sample %s not compatible due to lacking of feature %s. skipping sample", this, featureName);
                return Optional.empty();
            }
        }
        return Optional.of(dataset);
    }

    private Map<String, Number> ckResultToMap(Object ckResult) {
        var objValueMap = objectMapper.convertValue(ckResult, new TypeReference<Map<String, Object>>() {
        });
        Map<String, Number> numberMap = new HashMap<>();
        for (var objValueEntry : objValueMap.entrySet()) {
            var key = objValueEntry.getKey();
            var value = objValueEntry.getValue();
            if (value instanceof Number) {
                numberMap.put(key, (Number) value);
            } else if (key.equals("type")) {
                numberMap.put("isInnerClass", value.equals("innerclass") ? 1f : 0f);
            }
        }
        return numberMap;
    }

}
