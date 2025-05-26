package it.eng.tools.s3.provision.model;

import lombok.Getter;
import lombok.Setter;
import software.amazon.awssdk.services.iam.model.Role;
import software.amazon.awssdk.services.sts.model.Credentials;

@Getter
@Setter
public class S3ProvisionResponse {

    private final Role role;
    private final Credentials credentials;

    public S3ProvisionResponse(Role role, Credentials credentials) {
        this.role = role;
        this.credentials = credentials;
    }

    @Override
    public String toString() {
        return "S3ProvisionResponse{" +
                "role=" + role +
                ", credentials=" + credentials +
                '}';
    }
}
