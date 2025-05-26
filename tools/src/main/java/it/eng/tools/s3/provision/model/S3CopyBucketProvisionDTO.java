package it.eng.tools.s3.provision.model;

import lombok.Data;
import software.amazon.awssdk.services.iam.model.Role;

@Data
public class S3CopyBucketProvisionDTO {

    private Role role;
    private String bucketPolicy;

    public S3CopyBucketProvisionDTO(Role role) {
        this.role = role;
    }
}
