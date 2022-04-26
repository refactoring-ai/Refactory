package com.github.refactoringai.refactory.entities;

import java.util.Collection;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.transaction.Transactional;

import org.gitlab4j.api.models.MergeRequest;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import io.quarkus.panache.common.Parameters;

@Table(name = "merge_request")
@Entity
public class RefactoryMergeRequest extends PanacheEntity {

    @Column(name = "merge_request_iid", nullable = false)
    public Long mergeRequestIid;

    @ManyToOne(optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    public RefactoryProject project;

    @OneToMany(mappedBy = "refactoryMergeRequest", cascade = CascadeType.ALL, orphanRemoval = true)
    public Collection<RefactoringUnit> predictions;

    public static RefactoryMergeRequest fromGitlabMergeRequest(MergeRequest mergeRequest) {
        var result = new RefactoryMergeRequest();
        result.mergeRequestIid = mergeRequest.getIid();
        return result;
    }

    @Transactional
    public static boolean hasMergeRequestBeenProcessed(MergeRequest mergeRequest, RefactoryProject refactoryProject) {
        var params = Parameters.with("iid", mergeRequest.getIid()).and("project", refactoryProject);

        return find("mergeRequestIid = :iid and project = :project", params).firstResultOptional().isPresent();
    }

}
