package com.github.refactoringai.refactory.entities;

import java.util.Collection;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.transaction.Transactional;

import org.gitlab4j.api.models.MergeRequest;

import io.quarkus.hibernate.orm.panache.PanacheEntity;

@Table(name = "merge_request")
@Entity
public class RefactoryMergeRequest extends PanacheEntity {

    @Column(name = "merge_request_iid", nullable = false)
    public Integer mergeRequestIid;

    @OneToMany(mappedBy = "refactoryMergeRequest")
    public Collection<RefactoringUnit> predictions;

    public static RefactoryMergeRequest fromGitlabMergeRequest(MergeRequest mergeRequest) {
        var result = new RefactoryMergeRequest();
        result.mergeRequestIid = mergeRequest.getIid();
        return result;
    }

    @Transactional
    public static boolean hasMergeRequestBeenProcessed(MergeRequest mergeRequest) {
        return find("mergeRequestIid", mergeRequest.getIid()).firstResultOptional().isPresent();
    }

}
