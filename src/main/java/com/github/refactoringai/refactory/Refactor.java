package com.github.refactoringai.refactory;

import javax.persistence.Column;
import javax.persistence.Entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;

@Entity
public class Refactor extends PanacheEntity {

    @Column(name = "old_path", nullable = false)
    public String oldPath;

    @Column(name = "new_path", nullable = false)
    public String newPath;

    @Column(name = "line_number", nullable = false)
    public int lineNumber;

    @Column(name = "description", nullable = false)
    public String description;

    @Column(name = "should_refactor", nullable = false)
    public boolean shouldRefactor;

    public Refactor() {

    }

    /**
     * @param oldPath
     * @param newPath
     * @param lineNumber
     * @param description
     * @param shouldRefactor
     * @param metrics
     */
    public Refactor(String oldPath, String newPath, int lineNumber, String description, boolean shouldRefactor) {
        this.oldPath = oldPath;
        this.newPath = newPath;
        this.lineNumber = lineNumber;
        this.description = description;
        this.shouldRefactor = shouldRefactor;
    }

    public boolean shouldRefactor() {
        return shouldRefactor;
    }

}
