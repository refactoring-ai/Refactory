package com.github.refactoringai;

import javax.enterprise.inject.Produces;

import com.github.mauricioaniche.ck.CK;
import com.github.refactoringai.refactory.Refactory;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.gitlab4j.api.GitLabApi;

import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.annotations.QuarkusMain;

@QuarkusMain
public class RefactoryMain {

    // The server to request GitLab information from and make mutations. This is set
    // by GitLab by default.
    @ConfigProperty(name = "ci.server.url")
    String gitLabServerUrl;

    // The token used to authenticate to the GitLab server. The token should have
    // permission to read merge requests and create discussions on them
    @ConfigProperty(name = "oauth2.token")
    String gitLabAccessToken;

    /**
     * This method starts the recommendation pipeline
     * 
     * @param args None as of yet
     */
    public static void main(String... args) {
        Quarkus.run(Refactory.class, args);
    }

    @Produces
    public GitLabApi gitLabApi() {
        return new GitLabApi(gitLabServerUrl, gitLabAccessToken);
    }

    @Produces
    public CK ck() {
        return new CK(false, 0, true);
    }
}