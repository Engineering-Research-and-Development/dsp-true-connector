package it.eng.tools.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Request-only payload for updating a tenant and optionally rotating/migrating
 * bucket credentials.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@JsonDeserialize(builder = TenantUpdateRequest.Builder.class)
public class TenantUpdateRequest {

    private String name;
    private String description;
    private boolean automaticNegotiation;
    private boolean automaticTransfer;
    private String bucketName;
    private String realm;
    private String clientSecret;
    private String accessKey;
    private String secretKey;
    private boolean verifyConnection;

    /**
     * Converts this request to tenant updates consumed by {@link it.eng.tools.service.TenantService}.
     *
     * @param existingTenant existing tenant used to preserve immutable and required fields
     * @return tenant containing the mutable, non-bucket update fields
     */
    public Tenant toTenantUpdates(Tenant existingTenant) {
        return Tenant.Builder.newInstance()
                .id(existingTenant.getId())
                .name(name != null ? name : existingTenant.getName())
                .participantId(existingTenant.getParticipantId())
                .description(description)
                .automaticNegotiation(automaticNegotiation)
                .automaticTransfer(automaticTransfer)
                .realm(realm != null ? realm : existingTenant.getRealm())
                .clientSecret(clientSecret != null ? clientSecret : existingTenant.getClientSecret())
                .build();
    }

    /**
     * Converts this request to optional bucket credential input.
     *
     * @return request-only bucket credential carrier
     */
    public TenantBucketCredentialsRequest toCredentialsRequest() {
        return TenantBucketCredentialsRequest.Builder.newInstance()
                .bucketName(bucketName)
                .accessKey(accessKey)
                .secretKey(secretKey)
                .verifyConnection(verifyConnection)
                .build();
    }

    /**
     * Builder for {@link TenantUpdateRequest}.
     */
    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Builder {

        private final TenantUpdateRequest request;

        private Builder() {
            request = new TenantUpdateRequest();
        }

        /**
         * Creates a new builder instance.
         *
         * @return a new builder
         */
        public static Builder newInstance() {
            return new Builder();
        }

        /**
         * Sets the Keycloak realm.
         *
         * @param realm realm name, or {@code null}/blank to clear
         * @return this builder
         */
        public Builder realm(String realm) {
            request.realm = realm;
            return this;
        }

        /**
         * Sets the Keycloak client secret.
         *
         * @param clientSecret client secret, or {@code null}/blank to clear
         * @return this builder
         */
        public Builder clientSecret(String clientSecret) {
            request.clientSecret = clientSecret;
            return this;
        }

        /**
         * Sets tenant name.
         *
         * @param name tenant name
         * @return this builder
         */
        public Builder name(String name) {
            request.name = name;
            return this;
        }

        /**
         * Sets tenant description.
         *
         * @param description tenant description
         * @return this builder
         */
        public Builder description(String description) {
            request.description = description;
            return this;
        }

        /**
         * Sets automatic negotiation flag.
         *
         * @param automaticNegotiation automatic negotiation flag
         * @return this builder
         */
        public Builder automaticNegotiation(boolean automaticNegotiation) {
            request.automaticNegotiation = automaticNegotiation;
            return this;
        }

        /**
         * Sets automatic transfer flag.
         *
         * @param automaticTransfer automatic transfer flag
         * @return this builder
         */
        public Builder automaticTransfer(boolean automaticTransfer) {
            request.automaticTransfer = automaticTransfer;
            return this;
        }

        /**
         * Sets candidate bucket name.
         *
         * @param bucketName candidate bucket name
         * @return this builder
         */
        public Builder bucketName(String bucketName) {
            request.bucketName = bucketName;
            return this;
        }

        /**
         * Sets candidate access key.
         *
         * @param accessKey candidate access key
         * @return this builder
         */
        public Builder accessKey(String accessKey) {
            request.accessKey = accessKey;
            return this;
        }

        /**
         * Sets candidate secret key.
         *
         * @param secretKey candidate secret key
         * @return this builder
         */
        public Builder secretKey(String secretKey) {
            request.secretKey = secretKey;
            return this;
        }

        /**
         * Sets connection verification flag.
         *
         * @param verifyConnection verification flag
         * @return this builder
         */
        public Builder verifyConnection(boolean verifyConnection) {
            request.verifyConnection = verifyConnection;
            return this;
        }

        /**
         * Builds request instance.
         *
         * @return built request
         */
        public TenantUpdateRequest build() {
            return request;
        }
    }
}
