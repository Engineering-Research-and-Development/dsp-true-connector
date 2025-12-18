package it.eng.dcp.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import it.eng.dcp.common.model.BaseDcpMessage;
import it.eng.tools.model.DSpaceConstants;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotNull;

/**
 * Simple concrete message used in tests to exercise BaseDcpMessage behavior.
 */
@JsonDeserialize(builder = TestMessage.Builder.class)
@NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
public class TestMessage extends BaseDcpMessage {

    // allow tests to override the declared type when needed
    private String typeOverride;

    @NotNull
    private String payload;

    @Override
    @JsonProperty(value = DSpaceConstants.TYPE, access = JsonProperty.Access.READ_ONLY)
    public String getType() {
        return typeOverride; // intentionally null when not set so builders must provide type to pass validation
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static class Builder {
        private final TestMessage msg;

        private Builder() {
            msg = new TestMessage();
            // set default context
            msg.getContext().add(DSpaceConstants.DCP_CONTEXT);
        }

        public static Builder newInstance() {
            return new Builder();
        }

        public Builder type(String type) {
            msg.typeOverride = type;
            return this;
        }

        public Builder payload(String payload) {
            msg.payload = payload;
            return this;
        }

        public TestMessage build() {
            msg.validateBase();
            return msg;
        }
    }
}
