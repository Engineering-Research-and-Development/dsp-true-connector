package it.eng.datatransfer.model;

import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

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
  "@type": "dspace:TransferStartMessage",
  "dspace:providerPid": "urn:uuid:a343fcbf-99fc-4ce8-8e9b-148c97605aab",
  "dspace:consumerPid": "urn:uuid:32541fe6-c580-409e-85a8-8a9a32fbe833",
  "dspace:dataAddress": {
    "@type": "dspace:DataAddress",
    "dspace:endpointType": "https://w3id.org/idsa/v4.1/HTTP",
    "dspace:endpoint": "http://example.com",
    "dspace:endpointProperties": [
      {
        "@type": "dspace:EndpointProperty",
        "dspace:name": "authorization",
        "dspace:value": "TOKEN-ABCDEFG"
      },
      {
        "@type": "dspace:EndpointProperty",
        "dspace:name": "authType",
        "dspace:value": "bearer"
      }
    ]
  }
}
 */

@Getter
@JsonDeserialize(builder = TransferStartMessage.Builder.class)
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Document(collection = "transfer_start_messages")
public class TransferStartMessage extends AbstractTransferMessage {

	private static final long serialVersionUID = 7918949682633114473L;

	@JsonIgnore
    @JsonProperty(DSpaceConstants.ID)
    @Id
    private String id;
    
	@NotNull
	@JsonProperty(DSpaceConstants.DSPACE_PROVIDER_PID)
	private String providerPid;
	
	//	The dataAddress is only provided if the current transfer is a pull transfer 
	//	and contains a transport-specific endpoint address for obtaining the data.
	@JsonProperty(DSpaceConstants.DSPACE_DATA_ADDRESS)
	private DataAddress dataAddress;
	
	@JsonPOJOBuilder(withPrefix = "")
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class Builder {
		
		private TransferStartMessage message;
		
		private Builder() {
			message = new TransferStartMessage();
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

		@JsonProperty(DSpaceConstants.DSPACE_DATA_ADDRESS)
		public Builder dataAddress(DataAddress dataAddress) {
			message.dataAddress = dataAddress;
			return this;
		}
		
		public TransferStartMessage build() {
			if (message.id == null) {
	               message.id = message.createNewPid();
	        }
			Set<ConstraintViolation<TransferStartMessage>> violations 
				= Validation.buildDefaultValidatorFactory().getValidator().validate(message);
			if(violations.isEmpty()) {
				return message;
			}
			throw new ValidationException("TransferStartMessage - " +
					violations
						.stream()
						.map(v -> v.getPropertyPath() + " " + v.getMessage())
						.collect(Collectors.joining(",")));
			}
	}
	
	@Override
	public String getType() {
		return DSpaceConstants.DSPACE + TransferStartMessage.class.getSimpleName();
	}

}
