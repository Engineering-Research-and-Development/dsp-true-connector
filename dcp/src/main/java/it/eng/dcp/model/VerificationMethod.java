package it.eng.dcp.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

@JsonDeserialize(builder = VerificationMethod.Builder.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class VerificationMethod implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final Map<String, Object> publicKeyJwk = new LinkedHashMap<>();
    private String id;
    private String type;
    private String controller;
    private String publicKeyMultibase;

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Builder {

        private final VerificationMethod method;

        private Builder() {
            method = new VerificationMethod();
        }

        public static Builder newInstance() {
            return new Builder();
        }

        public Builder id(String id) {
            method.id = id;
            return this;
        }

        public Builder type(String type) {
            method.type = type;
            return this;
        }

        public Builder controller(String controller) {
            method.controller = controller;
            return this;
        }

        public Builder publicKeyMultibase(String publicKeyMultibase) {
            method.publicKeyMultibase = publicKeyMultibase;
            return this;
        }

        public Builder publicKeyJwk(Map<String, Object> publicKeyJwk) {
            method.publicKeyJwk.putAll(publicKeyJwk);
            return this;
        }

        public VerificationMethod build() {
            return method;
        }
    }
}
