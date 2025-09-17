package it.eng.connector.configuration;

import com.fasterxml.jackson.databind.JsonNode;
import it.eng.catalog.model.CatalogError;
import it.eng.catalog.serializer.CatalogSerializer;
import it.eng.datatransfer.model.TransferError;
import it.eng.negotiation.model.ContractNegotiationErrorMessage;
import it.eng.tools.model.DSpaceConstants;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;

import java.io.IOException;
import java.util.Arrays;

/**
 * Error response formatter for Protocol endpoints.
 * Formats authentication errors according to the DSP protocol specifications.
 */
public class ProtocolErrorResponseFormatter implements ErrorResponseFormatter {

    private static final String EN = "en";
    private static final String NOT_AUTH = "Not authorized";
    private static final String NOT_AUTH_CODE = "401";

    @Override
    public JsonNode formatAuthenticationError(AuthenticationException ex, WebRequest request, String uri) {
        String consumerPid = "NA";
        String providerPid = "NA";
        
        // Try to extract PIDs from request body
        try {
            if (request instanceof ServletWebRequest) {
                String body = ((ServletWebRequest) request).getRequest().getReader().lines().reduce("", String::concat);
                if (StringUtils.isNotBlank(body)) {
                    JsonNode node = CatalogSerializer.deserializeProtocol(body, JsonNode.class);
                    consumerPid = node.get(DSpaceConstants.DSPACE_CONSUMER_PID) != null ?
                            node.get(DSpaceConstants.DSPACE_CONSUMER_PID).asText() : "NA";
                    providerPid = node.get(DSpaceConstants.DSPACE_PROVIDER_PID) != null ?
                            node.get(DSpaceConstants.DSPACE_PROVIDER_PID).asText() : "NA";
                }
            }
        } catch (IOException e) {
            // Log error but continue with default PIDs
        }

        // Create appropriate error response based on endpoint type
        if (uri.contains("catalog")) {
            CatalogError catalogError = CatalogError.Builder.newInstance()
                    .code(NOT_AUTH_CODE)
                    .reason(Arrays.asList(it.eng.catalog.model.Reason.Builder.newInstance()
                            .language(EN).value(NOT_AUTH).build()))
                    .build();
            return CatalogSerializer.serializeProtocolJsonNode(catalogError);
            
        } else if (uri.contains("negotiations")) {
            ContractNegotiationErrorMessage negotiationError = ContractNegotiationErrorMessage.Builder.newInstance()
                    .consumerPid(consumerPid)
                    .providerPid(providerPid)
                    .code(NOT_AUTH_CODE)
                    .reason(Arrays.asList(it.eng.negotiation.model.Reason.Builder.newInstance()
                            .language(EN).value(NOT_AUTH).build()))
                    .build();
            return CatalogSerializer.serializeProtocolJsonNode(negotiationError);
            
        } else if (uri.contains("transfers")) {
            TransferError transferError = TransferError.Builder.newInstance()
                    .consumerPid(consumerPid)
                    .providerPid(providerPid)
                    .code(NOT_AUTH_CODE)
                    .reason(Arrays.asList(it.eng.datatransfer.model.Reason.Builder.newInstance()
                            .language(EN).value(NOT_AUTH).build()))
                    .build();
            return CatalogSerializer.serializeProtocolJsonNode(transferError);
        }
        
        // Default error response - create a simple error object
        CatalogError defaultError = CatalogError.Builder.newInstance()
                .code(NOT_AUTH_CODE)
                .reason(Arrays.asList(it.eng.catalog.model.Reason.Builder.newInstance()
                        .language(EN).value(NOT_AUTH).build()))
                .build();
        return CatalogSerializer.serializeProtocolJsonNode(defaultError);
    }
}
