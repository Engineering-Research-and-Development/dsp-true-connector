package it.eng.connector.model.wellknown;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class VersionResponse {

    private List<Version> protocolVersions;

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Builder {
        private final VersionResponse versionResponse;

        private Builder() {
            versionResponse = new VersionResponse();
        }

        public static VersionResponse.Builder newInstance() {
            return new VersionResponse.Builder();
        }

        public Builder protocolVersions(List<Version> protocolVersions) {
            versionResponse.protocolVersions = protocolVersions;
            return this;
        }

        public VersionResponse build() {
            return versionResponse;
        }
    }

}
