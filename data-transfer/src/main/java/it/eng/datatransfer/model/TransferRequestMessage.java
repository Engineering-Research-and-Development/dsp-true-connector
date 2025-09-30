package it.eng.datatransfer.model;

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
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.io.Serial;
import java.util.Set;
import java.util.stream.Collectors;

@Getter
@JsonDeserialize(builder = TransferRequestMessage.Builder.class)
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Document(collection = "transfer_request_messages")
public class TransferRequestMessage extends AbstractTransferMessage {

    @Serial
    private static final long serialVersionUID = 8814457068103190252L;

    @JsonIgnore
    @JsonProperty(DSpaceConstants.ID)
    @Id
    private String id;

    @NotNull
    private String agreementId;

    private String format;

    private DataAddress dataAddress;

    @NotNull
    private String callbackAddress;

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Builder {

        private final TransferRequestMessage message;

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

        public Builder agreementId(String agreementId) {
            message.agreementId = agreementId;
            return this;
        }

        public Builder format(String format) {
            message.format = format;
            return this;
        }

        public Builder dataAddress(DataAddress dataAddress) {
            message.dataAddress = dataAddress;
            return this;
        }

        public Builder consumerPid(String consumerPid) {
            message.consumerPid = consumerPid;
            return this;
        }

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
            if (violations.isEmpty()) {
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
        return TransferRequestMessage.class.getSimpleName();
    }

}
