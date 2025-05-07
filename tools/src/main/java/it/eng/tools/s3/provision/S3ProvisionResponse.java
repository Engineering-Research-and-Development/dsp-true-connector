package it.eng.tools.s3.provision;

import lombok.Getter;
import software.amazon.awssdk.services.iam.model.Role;
import software.amazon.awssdk.services.sts.model.Credentials;

@Getter
public class S3ProvisionResponse {

    private final Role role;
    private final Credentials credentials;

    public S3ProvisionResponse(Role role, Credentials credentials) {
        this.role = role;
        this.credentials = credentials;
    }

}
