package it.eng.dcp.common.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.ValidationException;
import jakarta.validation.ValidatorFactory;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * DCP Presentation Response Message.
 *
 * <p>Returned by holder's credential service in response to a presentation query.
 * Contains the verifiable presentations matching the requested scopes.
 *
 * <p>This is a shared protocol message used by both holder and verifier modules.
 */
@Getter
@JsonDeserialize(builder = PresentationResponseMessage.Builder.class)
@NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
public class PresentationResponseMessage extends BaseDcpMessage implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Override
    @JsonProperty(value = DCPConstants.TYPE, access = JsonProperty.Access.READ_ONLY)
    public String getType() {
        return PresentationResponseMessage.class.getSimpleName();
    }

    private List<Object> presentation = new ArrayList<>();

    private Map<String, Object> presentationSubmission;

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Builder {
        private final PresentationResponseMessage msg;

        private Builder() {
            msg = new PresentationResponseMessage();
            msg.getContext().add(DCPConstants.DCP_CONTEXT);
        }

        @JsonCreator
        public static Builder newInstance() {
            return new Builder();
        }

        @JsonProperty(DCPConstants.CONTEXT)
        public Builder context(List<String> context) {
            if (context != null) {
                msg.getContext().clear();
                msg.getContext().addAll(context);
            }
            return this;
        }

        @JsonProperty("presentation")
        public Builder presentation(List<Object> presentation) {
            if (presentation != null) {
                msg.presentation.clear();
                msg.presentation.addAll(presentation);
            }
            return this;
        }

        public Builder presentationSubmission(Map<String, Object> submission) {
            msg.presentationSubmission = submission;
            return this;
        }

        public PresentationResponseMessage build() {
            try (ValidatorFactory vf = Validation.buildDefaultValidatorFactory()) {
                Set<ConstraintViolation<PresentationResponseMessage>> violations = vf.getValidator().validate(msg);
                if (violations.isEmpty()) {
                    if (msg.presentation == null) {
                        msg.presentation = new ArrayList<>();
                    }
                    return msg;
                }
                throw new ValidationException("PresentationResponseMessage - " +
                        violations.stream().map(v -> v.getPropertyPath() + " " + v.getMessage()).collect(Collectors.joining(",")));
            }
        }
    }

}
