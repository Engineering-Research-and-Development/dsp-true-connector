package it.eng.dcp.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import it.eng.tools.model.DSpaceConstants;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.ValidationException;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import java.util.stream.Collectors;


@Getter
@JsonDeserialize(builder = PresentationResponseMessage.Builder.class)
@NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
public class PresentationResponseMessage extends BaseDcpMessage implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Override
    @JsonProperty(value = DSpaceConstants.TYPE, access = JsonProperty.Access.READ_ONLY)
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
            msg.getContext().add(DSpaceConstants.DCP_CONTEXT);
        }

        @JsonCreator
        public static Builder newInstance() {
            return new Builder();
        }

        @JsonProperty(DSpaceConstants.CONTEXT)
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
