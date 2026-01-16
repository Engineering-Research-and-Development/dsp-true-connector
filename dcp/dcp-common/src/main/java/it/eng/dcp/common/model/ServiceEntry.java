package it.eng.dcp.common.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serial;
import java.io.Serializable;

public record ServiceEntry(String id, String type, String serviceEndpoint) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public ServiceEntry(@JsonProperty("id") String id,
                        @JsonProperty("type") String type,
                        @JsonProperty("serviceEndpoint") String serviceEndpoint) {
        this.id = id;
        this.type = type;
        this.serviceEndpoint = serviceEndpoint;
    }
}

