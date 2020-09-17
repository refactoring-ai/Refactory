package com.github.refactoringai.refactory;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import com.github.mauricioaniche.ck.CK;
import com.github.mauricioaniche.ck.CKClassResult;

import org.gitlab4j.api.models.Diff;

@ApplicationScoped
public class MetricCollector {

    @Inject
    CK ck;

    /**
     * Executes CK on the files changed in the list of diffs. First the non-Java
     * files are filtered out. Keep in mind that CK also analyses subclasses and
     * therefore the amount of files is not equal to the amount of samples
     * 
     * @param repoPath The location of the repository to analyse
     * @param diffs    The diffs which contain information on the changed files
     * @return The metrics with path information
     */
    public List<Sample> getMetrics(Path repoPath, Collection<Diff> diffs) {
        diffs = filterJavaFiles(diffs);
        // TODO maybe some validation on files existing. They should exists for gitlab
        // merge requests but in other situations might not.
        var absPathStrToDiffMap = new HashMap<String, Diff>();
        diffs.stream().forEach(diff -> absPathStrToDiffMap.put(repoPath.resolve(diff.getNewPath()).toString(), diff));

        var classResults = new ArrayList<CKClassResult>();
        ck.calculate(repoPath.toString(), classResults::add, absPathStrToDiffMap.keySet().toArray(new String[0]));

        return classResults.stream().map(classResult -> {
            var correspondingDiff = absPathStrToDiffMap.get(classResult.getFile());
            return new Sample(classResult, Paths.get(correspondingDiff.getOldPath()),
                    Paths.get(correspondingDiff.getNewPath()));
        }).collect(Collectors.toList());
    }

    private List<Diff> filterJavaFiles(Collection<Diff> toFilter) {
        return toFilter.stream().filter(diff -> diff.getNewPath().endsWith(".java")).collect(Collectors.toList());
    }
}
