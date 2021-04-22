package com.github.refactoringai.tools;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import javax.inject.Inject;
import javax.transaction.Transactional;

import com.github.refactoringai.refactory.GitLab;
import com.github.refactoringai.refactory.entities.RefactoringUnit;

import org.gitlab4j.api.GitLabApiException;

import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.QuarkusApplication;

public class RefactoringUnitSampler {
    /**
     * This method starts the recommendation pipeline
     * 
     * @param args None as of yet
     */
    public static void main(String... args) {
        Quarkus.run(RefactoringUnitSamplerApplication.class, args);

    }

    public static class RefactoringUnitSamplerApplication implements QuarkusApplication {

        @Inject
        GitLab gitLab;

        @Override
        @Transactional
        public int run(String... args) throws IOException, GitLabApiException {
            var trueN = 20;
            var falseN = 10;
            var trueSamples = new ArrayList<RefactoringUnit>(trueN);
            var falseSamples = new ArrayList<RefactoringUnit>(falseN);

            List<RefactoringUnit> units = RefactoringUnit.listAll();
            Collections.sort(units);
            for (RefactoringUnit refactoringUnit : units) {
                if (refactoringUnit.shouldRefactorProbability < 0.9) {
                    break;
                }
                if (!exists(refactoringUnit, trueSamples)) {
                    trueSamples.add(refactoringUnit);
                }
            }

            Collections.shuffle(units);

            for (RefactoringUnit unit : units) {
                if (trueSamples.size() < trueN && unit.shouldRefactorProbability >= 0.85 && unit.shouldRefactor
                        && !exists(unit, trueSamples)) {
                    trueSamples.add(unit);
                } else if (falseSamples.size() < falseN && unit.shouldRefactorProbability <= 0.60
                        && !unit.shouldRefactor && !exists(unit, falseSamples)) {
                    falseSamples.add(unit);
                }
            }

            final var filePath = Paths.get("urls.csv");
            try {
                Files.delete(filePath);
            } catch (NoSuchFileException nsfe) {
                // Don't care if the file does not exist
            }
            Files.writeString(filePath, "URL to class at commit,Method name,Model confidence,Line number\n");

            final var format = "%s,%s,%f,%d\n";
            for (RefactoringUnit refactoringUnit : trueSamples) {
                Files.writeString(filePath,
                        String.format(Locale.US, format, gitLab.getFileUrl(refactoringUnit), refactoringUnit.unitName,
                                refactoringUnit.shouldRefactorProbability, refactoringUnit.lineNumber),
                        StandardOpenOption.APPEND);
            }

            for (RefactoringUnit refactoringUnit : falseSamples) {
                Files.writeString(filePath,
                        String.format(Locale.US, format, gitLab.getFileUrl(refactoringUnit), refactoringUnit.unitName,
                                refactoringUnit.shouldRefactorProbability, refactoringUnit.lineNumber),
                        StandardOpenOption.APPEND);
            }
            return 0;
        }

        private boolean exists(RefactoringUnit unit, List<RefactoringUnit> units) {
            for (RefactoringUnit refactoringUnit : units) {
                if (unit.newPath.equals(refactoringUnit.newPath) && unit.unitName.equals(refactoringUnit.unitName)) {
                    return true;
                }
            }
            return false;
        }
    }
}
