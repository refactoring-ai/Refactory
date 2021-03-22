package com.github.refactoringai.refactory.entities;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import javax.json.bind.annotation.JsonbProperty;
import javax.persistence.CascadeType;
import javax.persistence.CollectionTable;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.OneToMany;
import javax.persistence.OrderColumn;
import javax.persistence.Table;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;

@Entity
@Table(name = "model")
public class Model extends PanacheEntityBase {

    @Id
    @JsonbProperty("id")
    public UUID id;

    @Column(name = "refactor_type", nullable = false)
    @JsonbProperty("refactoring_type")
    public String refactoringType;

    @Column(name = "trained_on_dataset_name", nullable = false)
    @JsonbProperty("trained_on")
    public String trainedOnDatasetName;

    @Column(name = "model_path", nullable = false)
    public String modelPath;

    @Column(name = "model_type", nullable = false)
    @JsonbProperty("model_type")
    public String modelType;

    @ElementCollection
    @CollectionTable(name = "feature_names", joinColumns = @JoinColumn(name = "model_id"))
    @OrderColumn
    @JsonbProperty("feature_names")
    public List<String> featureNames;

    @OneToMany(mappedBy = "model", cascade = CascadeType.REMOVE)
    public Collection<RefactoringUnit> mergeRequests;

    public Path getModelPath() {
        return Paths.get(modelPath);
    }

}
