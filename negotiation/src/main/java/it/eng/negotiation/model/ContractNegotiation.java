package it.eng.negotiation.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import it.eng.tools.model.DSpaceConstants;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.ValidationException;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.*;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@JsonDeserialize(builder = ContractNegotiation.Builder.class)
@Document(collection = "contract_negotiations")
public class ContractNegotiation extends AbstractNegotiationObject {

    @JsonIgnore
    @JsonProperty(DSpaceConstants.ID)
    @Id
    private String id;

    @JsonIgnore
    private String callbackAddress;
    @JsonIgnore
    private String assigner;
    // determines which role the connector is for that contract negotiation (consumer or provider)
    @JsonIgnore
    private String role;

    @JsonIgnore
    @DBRef
    private Offer offer;

    @JsonIgnore
    @DBRef
    private Agreement agreement;

    @NotNull
    private String consumerPid;

    @NotNull
    private ContractNegotiationState state;

    @JsonIgnore
    @CreatedBy
    private String createdBy;
    @JsonIgnore
    @CreatedDate
    private Instant created;

    @JsonIgnore
    @LastModifiedBy
    private String lastModifiedBy;
    @JsonIgnore
    @LastModifiedDate
    private Instant modified;

    @JsonIgnore
    @Version
    @Field("version")
    private Long version;

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Builder {

        private final ContractNegotiation message;

        private Builder() {
            message = new ContractNegotiation();
        }

        @JsonCreator
        public static ContractNegotiation.Builder newInstance() {
            return new ContractNegotiation.Builder();
        }

        @JsonProperty(DSpaceConstants.ID)
        public Builder id(String id) {
            message.id = id;
            return this;
        }

        public Builder state(ContractNegotiationState state) {
            message.state = state;
            return this;
        }

        /**
         * It is sent in a request and is stored on the responder side for the next request.
         * E.g. Consumer sends request to provider-> Provider stores callbackAddress for future request and responses with 200 ()
         *
         * @param callbackAddress - URL for future requests and responses
         * @return Builder object
         */
        public Builder callbackAddress(String callbackAddress) {
            message.callbackAddress = callbackAddress;
            return this;
        }

        public Builder assigner(String assigner) {
            message.assigner = assigner;
            return this;
        }

        public Builder role(String role) {
            message.role = role;
            return this;
        }

        public Builder offer(Offer offer) {
            message.offer = offer;
            return this;
        }

        public Builder agreement(Agreement agreement) {
            message.agreement = agreement;
            return this;
        }

        public Builder providerPid(String providerPid) {
            message.providerPid = providerPid;
            return this;
        }

        public Builder consumerPid(String consumerPid) {
            message.consumerPid = consumerPid;
            return this;
        }

        // Auditable fields
        @JsonProperty("version")
        public Builder version(Long version) {
            message.version = version;
            return this;
        }

        @JsonProperty("createdBy")
        public Builder createdBy(String createdBy) {
            message.createdBy = createdBy;
            return this;
        }

        @JsonProperty("created")
        public Builder created(Instant created) {
            message.created = created;
            return this;
        }

        @JsonProperty("modified")
        public Builder modified(Instant modified) {
            message.modified = modified;
            return this;
        }

        @JsonProperty("lastModifiedBy")
        public Builder lastModifiedBy(String lastModifiedBy) {
            message.lastModifiedBy = lastModifiedBy;
            return this;
        }

        public ContractNegotiation build() {
            if (message.id == null) {
                message.id = message.createNewPid();
            }
            if (message.providerPid == null) {
                message.providerPid = message.createNewPid();
            }
            Set<ConstraintViolation<ContractNegotiation>> violations
                    = Validation.buildDefaultValidatorFactory().getValidator().validate(message);
            if (violations.isEmpty()) {
                return message;
            }
            throw new ValidationException("ContractNegotiation - " +
                    violations
                            .stream()
                            .map(v -> v.getPropertyPath() + " " + v.getMessage())
                            .collect(Collectors.joining(", ")));
        }

    }

    @Override
    @JsonProperty(value = DSpaceConstants.TYPE, access = JsonProperty.Access.READ_ONLY)
    public String getType() {
        return ContractNegotiation.class.getSimpleName();
    }

    /**
     * Create new ContractNegotiation from initial, with new state.
     *
     * @param newState new ContractNegotiationState
     * @return new instance of ContractNegotiation
     */
    public ContractNegotiation withNewContractNegotiationState(ContractNegotiationState newState) {
        return ContractNegotiation.Builder.newInstance()
                .id(this.id)
                .consumerPid(this.consumerPid)
                .providerPid(this.providerPid)
                .callbackAddress(this.callbackAddress)
                .offer(this.offer)
                .assigner(this.assigner)
                .role(this.role)
                .agreement(this.agreement)
                // auditable fields
                .createdBy(this.createdBy)
                .created(created)
                .lastModifiedBy(this.lastModifiedBy)
                .modified(modified)
                .version(this.version)
                .state(newState)
                .build();
    }
}

