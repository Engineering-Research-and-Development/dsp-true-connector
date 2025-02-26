package it.eng.negotiation.model;

import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
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

/*
{
    "@context":  "https://w3id.org/dspace/2024/1/context.json",
    "@type": "dspace:ContractNegotiation",
    "dspace:providerPid": "urn:uuid:a343fcbf-99fc-4ce8-8e9b-148c97605aab",
    "dspace:consumerPid": "urn:uuid:32541fe6-c580-409e-85a8-8a9a32fbe833",
    "dspace:state": "dspace:REQUESTED"
  }
"required": [ "@context", "@type", "dspace:providerPid", "dspace:consumerPid", "dspace:state" ]
 */

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@JsonDeserialize(builder = ContractNegotiation.Builder.class)
@JsonPropertyOrder(value = {"@context", "@type", "@id"}, alphabetic = true)
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
    // determins which role the connector is for that contract negotiation (consumer or provider)
    @JsonIgnore
    private String role;
    
    @JsonIgnore
    @DBRef
    private Offer offer;
    
    @JsonIgnore
    @DBRef
    private Agreement agreement;
    
    @NotNull
    @JsonProperty(DSpaceConstants.DSPACE_CONSUMER_PID)
    private String consumerPid;

    @NotNull
    @JsonProperty(DSpaceConstants.DSPACE_STATE)
    private ContractNegotiationState state;
    
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

        @JsonProperty(DSpaceConstants.DSPACE_STATE)
        public Builder state(ContractNegotiationState state) {
            message.state = state;
            return this;
        }
        
        /**
         * It is sent in a request and is stored on the responder side for the next request.
         * E.g. Consumer sends request to provider-> Provider stores callbackAddress for future request and responses with 200 ()
         * @param callbackAddress
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

        @JsonProperty(DSpaceConstants.DSPACE_PROVIDER_PID)
        public Builder providerPid(String providerPid) {
            message.providerPid = providerPid;
            return this;
        }

        @JsonProperty(DSpaceConstants.DSPACE_CONSUMER_PID)
        public Builder consumerPid(String consumerPid) {
            message.consumerPid = consumerPid;
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
    @JsonIgnoreProperties(value = {"type"}, allowGetters = true)
    public String getType() {
        return DSpaceConstants.DSPACE + ContractNegotiation.class.getSimpleName();
    }
    
    /**
     * Create new ContractNegotiation from initial, with new state.
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
    			// not yet auditable fields
//    			.createdBy(this.createdBy)
//				.lastModifiedBy(this.lastModifiedBy)
//				.version(this.version)
    			.state(newState)
    			.build();
    }
}

