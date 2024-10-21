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
  "@type": "dspace:TransferRequestMessage",
  "dspace:consumerPid": "urn:uuid:32541fe6-c580-409e-85a8-8a9a32fbe833",
  "dspace:agreementId": "urn:uuid:e8dc8655-44c2-46ef-b701-4cffdc2faa44",
  "dct:format": "example:HTTP_PUSH",
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
  },
  "dspace:callbackAddress": "https://......"
}
 */

@Getter
@JsonDeserialize(builder = TransferRequestMessage.Builder.class)
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Document(collection = "transfer_request_messages")
public class TransferRequestMessage extends AbstractTransferMessage {

	private static final long serialVersionUID = 8814457068103190252L;

	@JsonIgnore
    @JsonProperty(DSpaceConstants.ID)
    @Id
    private String id;
    
	@NotNull
	@JsonProperty(DSpaceConstants.DSPACE_AGREEMENT_ID)
	private String agreementId;
	
	@JsonProperty(DSpaceConstants.DCT_FORMAT)
	private String format;
	
	@JsonProperty(DSpaceConstants.DSPACE_DATA_ADDRESS)
	private DataAddress dataAddress;
	
	@NotNull
	@JsonProperty(DSpaceConstants.DSPACE_CALLBACK_ADDRESS)
	private String callbackAddress;
	
	@JsonPOJOBuilder(withPrefix = "")
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class Builder {
		
		private TransferRequestMessage message;
		
		private Builder() {
			message = new TransferRequestMessage();
		}
		
		public static Builder newInstance() {
			return new Builder();
		}
		
		public Builder id(String id) {
        	message.id = id;
        	return this;
        }
		
		@JsonProperty(DSpaceConstants.DSPACE_AGREEMENT_ID)
		public Builder agreementId(String agreementId) {
			message.agreementId = agreementId;
			return this;
		}

		@JsonProperty(DSpaceConstants.DCT_FORMAT)
		public Builder format(String format) {
			message.format = format;
			return this;
		}
		
		@JsonProperty(DSpaceConstants.DSPACE_DATA_ADDRESS)
		public Builder dataAddress(DataAddress dataAddress) {
			message.dataAddress = dataAddress;
			return this;
		}
		
		@JsonProperty(DSpaceConstants.DSPACE_CONSUMER_PID)
		public Builder consumerPid(String consumerPid) {
			message.consumerPid = consumerPid;
			return this;
		}
		
		@JsonProperty(DSpaceConstants.DSPACE_CALLBACK_ADDRESS)
		public Builder callbackAddress(String callbackAddress) {
			message.callbackAddress = callbackAddress;
			return this;
		}

		public TransferRequestMessage build() {
			if (message.id == null) {
	               message.id = message.createNewId();
	        }
			Set<ConstraintViolation<TransferRequestMessage>> violations 
				= Validation.buildDefaultValidatorFactory().getValidator().validate(message);
			if(violations.isEmpty()) {
				return message;
			}
			throw new ValidationException("TransferRequestMessage - " +
					violations
						.stream()
						.map(v -> v.getPropertyPath() + " " + v.getMessage())
						.collect(Collectors.joining(",")));
			}
	}	
	
	@Override
	public String getType() {
		return DSpaceConstants.DSPACE + TransferRequestMessage.class.getSimpleName();
	}
	
}
