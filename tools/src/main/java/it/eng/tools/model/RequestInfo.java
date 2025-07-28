package it.eng.tools.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@JsonDeserialize(builder = RequestInfo.Builder.class)
@NoArgsConstructor
@EqualsAndHashCode
public class RequestInfo {

    private String method;
    private String remoteAddress;
    private String remoteHost;
    private String username;

    public static class Builder {
        private final RequestInfo requestInfo;

        private Builder() {
            requestInfo = new RequestInfo();
        }

        public static RequestInfo.Builder newInstance() {
            return new RequestInfo.Builder();
        }

        public RequestInfo.Builder method(String method) {
            requestInfo.method = method;
            return this;
        }

        public RequestInfo.Builder remoteAddress(String remoteAddress) {
            requestInfo.remoteAddress = remoteAddress;
            return this;
        }

        public RequestInfo.Builder remoteHost(String remoteHost) {
            requestInfo.remoteHost = remoteHost;
            return this;
        }

        public RequestInfo.Builder username(String username) {
            requestInfo.username = username;
            return this;
        }

        public RequestInfo build() {
            return requestInfo;
        }

    }


}
