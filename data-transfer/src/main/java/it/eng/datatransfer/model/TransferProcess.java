package it.eng.datatransfer.model;

import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

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

/*
 * {
  "@context":  "https://w3id.org/dspace/2024/1/context.json",
  "@type": "dspace:TransferProcess",
  "dspace:providerPid": "urn:uuid:a343fcbf-99fc-4ce8-8e9b-148c97605aab",
  "dspace:consumerPid": "urn:uuid:32541fe6-c580-409e-85a8-8a9a32fbe833",
  "dspace:state": "dspace:REQUESTED"
}
 * 
 */
@Getter
@JsonDeserialize(builder = TransferProcess.Builder.class)
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Document(collection = "transfer_process")
public class TransferProcess extends AbstractTransferMessage {

	private static final long serialVersionUID = -6329135422869881158L;

	@JsonIgnore
    @JsonProperty(DSpaceConstants.ID)
    @Id
    private String id;
    
	@NotNull
	@JsonProperty(DSpaceConstants.DSPACE_PROVIDER_PID)
	private String providerPid;
	
	@NotNull
	@JsonProperty(DSpaceConstants.DSPACE_STATE)
	private TransferState state;
	
	// used to store agreement so we can enforce it
	@JsonIgnore
	private String agreementId;
	@JsonIgnore
	private String callbackAddress;
	
    @JsonIgnore
    @CreatedBy
    private String createdBy;
    @JsonIgnore
    @LastModifiedBy
    private String lastModifiedBy;
    @JsonIgnore
    @Version
    @Field("version")
    private Long version;
	
	@JsonPOJOBuilder(withPrefix = "")
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class Builder {
		
		private TransferProcess message;
		
		private Builder() {
			message = new TransferProcess();
		}
		
		public static Builder newInstance() {
			return new Builder();
		}
		
		public Builder id(String id) {
        	message.id = id;
        	return this;
        }
		
		@JsonProperty(DSpaceConstants.DSPACE_CONSUMER_PID)
		public Builder consumerPid(String consumerPid) {
			message.consumerPid = consumerPid;
			return this;
		}

		@JsonProperty((DSpaceConstants.DSPACE_PROVIDER_PID))
		public Builder providerPid(String providerPid) {
			message.providerPid = providerPid;
			return this;
		}
		
		@JsonProperty((DSpaceConstants.DSPACE_STATE))
		public Builder state(TransferState state) {
			message.state = state;
			return this;
		}

		@JsonProperty("createdBy")
		public Builder createdBy(String createdBy) {
			message.createdBy = createdBy;
			return this;
		}

		@JsonProperty("lastModifiedBy")
		public Builder lastModifiedBy(String lastModifiedBy) {
			message.lastModifiedBy = lastModifiedBy;
			return this;
		}

		@JsonProperty("version")
		public Builder version(Long version) {
			message.version = version;
			return this;
		}
		
		@JsonProperty(DSpaceConstants.AGREEMENT_ID)
		public Builder agreementId(String agreementId) {
			message.agreementId = agreementId;
			return this;
		}
		
		@JsonProperty(DSpaceConstants.CALLBACK_ADDRESS)
		public Builder callbackAddress(String callbackAddress) {
			message.callbackAddress = callbackAddress;
			return this;
		}

		public TransferProcess build() {
			if (message.id == null) {
	               message.id = message.createNewPid();
	        }
			if (message.providerPid == null) {
                message.providerPid = message.createNewPid();
            }
			Set<ConstraintViolation<TransferProcess>> violations 
				= Validation.buildDefaultValidatorFactory().getValidator().validate(message);
			if(violations.isEmpty()) {
				return message;
			}
			throw new ValidationException("TransferProcess - " +
					violations
						.stream()
						.map(v -> v.getPropertyPath() + " " + v.getMessage())
						.collect(Collectors.joining(",")));
			}
	}	
	
	@Override
	public String getType() {
		return DSpaceConstants.DSPACE + TransferProcess.class.getSimpleName();
	}
	
	/**
	 * Create new TransferProcess from origin, with new TransferState.<br>
	 * Used to update state when transition happens.
	 * @param newTransferState
	 * @return new TransferProcess object from initial, with new state
	 */
	public TransferProcess copyWithNewTransferState(TransferState newTransferState) {
		return TransferProcess.Builder.newInstance()
				.id(this.id)
				.agreementId(this.agreementId)
				.consumerPid(this.consumerPid)
				.providerPid(this.providerPid)
				.callbackAddress(this.callbackAddress)
				.state(newTransferState)
				// no need to modify audit fields???
				.createdBy(this.createdBy)
				.lastModifiedBy(this.lastModifiedBy)
				.version(this.version)
				.build();
	}
}
