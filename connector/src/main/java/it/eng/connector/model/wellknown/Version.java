package it.eng.connector.model.wellknown;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class Version {

    private String binding = "HTTPS";
    private String path;
    private String version;
    private Auth auth;
    private String identifierType;
    private String serviceId;

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Builder {
        private final Version instance;

        private Builder() {
            instance = new Version();
        }

        public static Version.Builder newInstance() {
            return new Builder();
        }

        public Builder binding(String binding) {
            instance.binding = binding;
            return this;
        }

        public Builder path(String path) {
            instance.path = path;
            return this;
        }

        public Builder version(String version) {
            instance.version = version;
            return this;
        }

        public Builder auth(Auth auth) {
            instance.auth = auth;
            return this;
        }

        public Builder identifierType(String identifierType) {
            instance.identifierType = identifierType;
            return this;
        }

        public Builder serviceId(String serviceId) {
            instance.serviceId = serviceId;
            return this;
        }

        public Version build() {
            return instance;
        }
    }
}
