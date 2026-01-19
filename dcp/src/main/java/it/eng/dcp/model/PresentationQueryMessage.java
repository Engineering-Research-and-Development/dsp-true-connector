package it.eng.dcp.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import it.eng.dcp.common.model.BaseDcpMessage;
import it.eng.dcp.common.model.DCPConstants;
import jakarta.validation.ValidationException;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Getter
@JsonDeserialize(builder = PresentationQueryMessage.Builder.class)
@NoArgsConstructor
public class PresentationQueryMessage extends BaseDcpMessage {

    @Serial
    private static final long serialVersionUID = 1L;

    @Override
    @JsonProperty(value = DCPConstants.TYPE, access = JsonProperty.Access.READ_ONLY)
    public String getType() {
        return PresentationQueryMessage.class.getSimpleName();
    }

    // Explicit getters to ensure consumers without Lombok annotation processing can access values
    // scope is optional but default to empty list
    private List<String> scope = new ArrayList<>();

    private Map<String, Object> presentationDefinition;

    // convenience accessor (alternate name) used by some callers
    public List<String> scope() {
        return scope;
    }

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Builder {
        private final PresentationQueryMessage msg;

        private Builder() {
            msg = new PresentationQueryMessage();
            // default context to DCP namespace and context
            msg.getContext().add(DCPConstants.DCP_NAMESPACE);
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

        public Builder scope(List<String> scope) {
            if (scope != null) {
                msg.scope.clear();
                msg.scope.addAll(scope);
            }
            return this;
        }

        public Builder presentationDefinition(Map<String, Object> presentationDefinition) {
            msg.presentationDefinition = presentationDefinition;
            return this;
        }

        public PresentationQueryMessage build() {

            try {
                msg.validateBase();
                if (msg.scope == null) msg.scope = new ArrayList<>();
                return msg;
            } catch (Exception e) {
                throw new ValidationException("PresentationQueryMessage - " + e.getMessage());
            }
        }
    }
}
