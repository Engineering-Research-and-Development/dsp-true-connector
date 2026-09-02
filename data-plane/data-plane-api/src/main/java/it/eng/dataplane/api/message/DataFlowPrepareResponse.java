package it.eng.dataplane.api.message;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonProperty.Access;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import it.eng.dataplane.api.DataPlaneConstants;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.ValidationException;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Response returned by a Data Plane after a {@link DataFlowPrepareMessage} request.
 *
 * <p>The {@code dataAddress} map carries protocol-specific details:
 * <ul>
 *   <li>HTTP-PULL (provider): {@code endpoint} with the pre-signed GET URL for the artifact,
 *       plus {@code endpointType} set to {@code https://w3id.org/idsa/v4.1/HTTP}.</li>
 *   <li>HTTP-PULL (consumer viewData): {@code presignedUrl} with the pre-signed GET URL for the stored file.</li>
 *   <li>HTTP-PUSH (consumer): S3 credential fields ({@code bucketName}, {@code region}, {@code objectKey},
 *       {@code accessKey}, {@code secretKey}) and optionally {@code endpointOverride}.</li>
 * </ul>
 * </p>
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@JsonDeserialize(builder = DataFlowPrepareResponse.Builder.class)
public class DataFlowPrepareResponse {

    @NotNull
    @JsonProperty(value = DataPlaneConstants.CONTEXT, access = Access.READ_ONLY)
    private List<String> context = List.of(DataPlaneConstants.DSPACE_2025_01_CONTEXT);

    @NotBlank
    private String processId;

    /** Protocol-specific data address returned by the prepare operation. May be {@code null} for no-op protocols. */
    private Map<String, String> dataAddress;

    /** Builder for {@link DataFlowPrepareResponse}. */
    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Builder {

        private final DataFlowPrepareResponse response = new DataFlowPrepareResponse();

        private Builder() {}

        /**
         * Creates a new builder instance.
         *
         * @return new builder
         */
        public static Builder newInstance() {
            return new Builder();
        }

        /**
         * Sets the process ID.
         *
         * @param processId the transfer process ID this response relates to
         * @return this builder
         */
        public Builder processId(String processId) {
            response.processId = processId;
            return this;
        }

        /**
         * Sets the data address map.
         *
         * @param dataAddress protocol-specific key/value pairs (presignedUrl, credentials, etc.)
         * @return this builder
         */
        public Builder dataAddress(Map<String, String> dataAddress) {
            response.dataAddress = dataAddress;
            return this;
        }

        /**
         * Builds and validates the response.
         *
         * @return validated DataFlowPrepareResponse
         * @throws ValidationException if required fields are missing
         */
        public DataFlowPrepareResponse build() {
            Set<ConstraintViolation<DataFlowPrepareResponse>> violations;
            try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
                violations = factory.getValidator().validate(response);
            }
            if (violations.isEmpty()) {
                return response;
            }
            throw new ValidationException("DataFlowPrepareResponse - " +
                violations.stream()
                    .map(v -> v.getPropertyPath() + " " + v.getMessage())
                    .collect(Collectors.joining(", ")));
        }
    }

    /**
     * Returns the DSP message type for JSON serialization.
     *
     * @return simple class name
     */
    @JsonProperty(value = DataPlaneConstants.TYPE, access = Access.READ_ONLY)
    public String getType() {
        return DataFlowPrepareResponse.class.getSimpleName();
    }
}
