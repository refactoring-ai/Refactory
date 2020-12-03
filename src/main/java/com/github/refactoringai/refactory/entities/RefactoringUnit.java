package com.github.refactoringai.refactory.entities;

import java.nio.file.Path;
import java.nio.file.Paths;

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

    @ManyToOne(cascade = CascadeType.ALL, optional = false)
    @JoinColumn(name = "refactory_merge_request_id", nullable = false)
    public RefactoryMergeRequest refactoryMergeRequest;

    @ManyToOne(cascade = CascadeType.ALL, optional = false)
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

    public void wasRecommended() {
        wasRecommended = true;
    }

}
