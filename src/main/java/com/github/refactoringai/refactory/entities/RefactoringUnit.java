package com.github.refactoringai.refactory.entities;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Objects;

import javax.persistence.CascadeType;
import javax.persistence.CollectionTable;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OrderColumn;
import javax.persistence.Table;

import org.gitlab4j.api.models.Diff;

import io.quarkus.hibernate.orm.panache.PanacheEntity;

@Entity
@Table(name = "refactoring_unit")
public class RefactoringUnit extends PanacheEntity implements Comparable<RefactoringUnit> {

    @Column(name = "old_path", nullable = false)
    public String oldPath;

    @Column(name = "new_path", nullable = false)
    public String newPath;

    @Column(name = "line_number", nullable = false)
    public Integer lineNumber;

    @Column(name = "should_refactor_probability", nullable = false)
    public Float shouldRefactorProbability;

    @Column(name = "should_refactor", nullable = false)
    public Boolean shouldRefactor;

    @Column(name = "was_recommended", nullable = false)
    public Boolean wasRecommended;

    @ManyToOne(cascade = CascadeType.REMOVE, optional = false)
    @JoinColumn(name = "refactory_merge_request_id", nullable = false)
    public RefactoryMergeRequest refactoryMergeRequest;

    @ManyToOne(cascade = CascadeType.REMOVE, optional = false)
    @JoinColumn(name = "model_id", nullable = false)
    public Model model;

    @ElementCollection
    @OrderColumn
    @CollectionTable(name = "raw_input", joinColumns = @JoinColumn(name = "refactoring_unit_id"))
    public float[] input;

    @Column(name = "unit_name", nullable = false)
    public String unitName;

    public static RefactoringUnit createRefactoringUnit(Diff diff, Model model, int lineNumber, float[] input,
            String unitName) {
        var refactoringUnit = new RefactoringUnit();
        refactoringUnit.model = model;
        refactoringUnit.oldPath = diff.getOldPath();
        refactoringUnit.newPath = diff.getNewPath();
        refactoringUnit.lineNumber = lineNumber;
        refactoringUnit.input = input;
        refactoringUnit.unitName = unitName;
        refactoringUnit.wasRecommended = false;
        return refactoringUnit;
    }

    public Path getNewPath() {
        return Paths.get(newPath);
    }

    @Override
    public int compareTo(RefactoringUnit otheRefactoringUnit) {
        return -Float.compare(this.shouldRefactorProbability, otheRefactoringUnit.shouldRefactorProbability);
    }

    public void recommendationWasPlaced() {
        wasRecommended = true;
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#hashCode()
     */

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + Arrays.hashCode(input);
        result = prime * result + Objects.hash(lineNumber, model, newPath, oldPath, refactoryMergeRequest,
                shouldRefactor, shouldRefactorProbability, unitName, wasRecommended);
        return result;
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#equals(java.lang.Object)
     */

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        RefactoringUnit other = (RefactoringUnit) obj;
        return Arrays.equals(input, other.input) && Objects.equals(lineNumber, other.lineNumber)
                && Objects.equals(model, other.model) && Objects.equals(newPath, other.newPath)
                && Objects.equals(oldPath, other.oldPath)
                && Objects.equals(refactoryMergeRequest, other.refactoryMergeRequest)
                && Objects.equals(shouldRefactor, other.shouldRefactor)
                && Objects.equals(shouldRefactorProbability, other.shouldRefactorProbability)
                && Objects.equals(unitName, other.unitName) && Objects.equals(wasRecommended, other.wasRecommended);
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#toString()
     */

    @Override
    public String toString() {
        return String.format(
                "RefactoringUnit [lineNumber=%s, newPath=%s, mergeRequestIid=%s, shouldRefactor=%s, shouldRefactorProbability=%s, unitName=%s]\n",
                lineNumber, newPath, refactoryMergeRequest.mergeRequestIid, shouldRefactor,
                shouldRefactorProbability, unitName);
    }

}
