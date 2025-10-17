package it.eng.negotiation.model;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@AllArgsConstructor
public class ContractRequestMessageRequest implements Serializable {

    private String contractNegotiationId;
    private String forwardTo;
    @NotNull
    private JsonNode offer;
}
