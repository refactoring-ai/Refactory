package com.github.refactoringai;

import javax.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.gitlab4j.api.GitLabApi;

public class Beans {
    
    @ApplicationScoped
    public GitLabApi gitLabApi(@ConfigProperty(name = "ci.server.url") String gitLabServerUrl,
            @ConfigProperty(name = "gitlab.oauth2.token") String gitLabAccessToken,
            @ConfigProperty(name = "gitlab.ignore.certificate.errors", defaultValue = "false") Boolean gitLabIgnoreCertificateErrors) {
        var gitLabApi = new GitLabApi(gitLabServerUrl, gitLabAccessToken);
        gitLabApi.setIgnoreCertificateErrors(gitLabIgnoreCertificateErrors);
        return gitLabApi;
    }

}
