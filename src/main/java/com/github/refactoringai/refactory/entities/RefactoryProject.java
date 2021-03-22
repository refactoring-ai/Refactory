package com.github.refactoringai.refactory.entities;

import java.util.Collection;
import java.util.Optional;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.transaction.Transactional;

import org.gitlab4j.api.models.Project;

import io.quarkus.hibernate.orm.panache.PanacheEntity;

@Entity
@Table(name = "project")
public class RefactoryProject extends PanacheEntity {

    @Column(name = "gitlab_id", nullable = false)
    public Integer gitlabId;

    @Column(nullable = false)
    public String name;

    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true)
    public Collection<RefactoryMergeRequest> mergeRequests;

    @Transactional
    public Optional<RefactoryProject> findByGitLabId() {
        return find("gitlabId", gitlabId).firstResultOptional();
    }

    public static RefactoryProject fromGitLabProject(Project project) {
        var refactoryProject = new RefactoryProject();
        refactoryProject.gitlabId = project.getId();
        refactoryProject.name = project.getName();
        return refactoryProject;
    }
}
