package it.eng.connector.model.wellknown;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class Auth {

    private String protocol;
    private String version;
    private List<String> profile;

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Builder {
        private final Auth instance;

        private Builder() {
            instance = new Auth();
        }

        public static Builder newInstance() {
            return new Builder();
        }

        public Builder protocol(String protocol) {
            instance.protocol = protocol;
            return this;
        }

        public Builder version(String version) {
            instance.version = version;
            return this;
        }

        public Builder profile(List<String> profile) {
            instance.profile = profile;
            return this;
        }

        public Auth build() {
            return instance;
        }
    }
}
