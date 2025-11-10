package it.eng.connector.configuration;

import com.fasterxml.jackson.databind.JsonNode;
import it.eng.catalog.model.CatalogError;
import it.eng.catalog.serializer.CatalogSerializer;
import it.eng.datatransfer.model.TransferError;
import it.eng.negotiation.model.ContractNegotiationErrorMessage;
import it.eng.tools.model.DSpaceConstants;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.*;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.io.IOException;
import java.util.List;

@ControllerAdvice
@Slf4j
public class DataspaceProtocolEndpointsExceptionHandler extends ResponseEntityExceptionHandler {

    private static final String NOT_AUTH = "Not authorized";
    private static final String NOT_AUTH_CODE = String.valueOf(HttpStatus.UNAUTHORIZED.value());

    @ExceptionHandler({AuthenticationException.class})
    @ResponseBody
    public ResponseEntity<JsonNode> handleAuthenticationException(Exception ex, WebRequest request) {
        String uri = ((ServletWebRequest) request).getRequest().getRequestURI();
        String consumerPid = "NA";
        String providerPid = "NA";
        try {
            String body = ((ServletWebRequest) request).getRequest().getReader().lines().reduce("", String::concat);
            if (StringUtils.isNotBlank(body)) {
                // TODO maybe use plain jsonMapper here and not one from sub modules
                JsonNode node = CatalogSerializer.deserializeProtocol(body, JsonNode.class);
                consumerPid = node.get(DSpaceConstants.CONSUMER_PID) != null ?
                        node.get(DSpaceConstants.CONSUMER_PID).asText() :
                        "NA";
                providerPid = node.get(DSpaceConstants.PROVIDER_PID) != null ?
                        node.get(DSpaceConstants.PROVIDER_PID).asText() :
                        "NA";
            } else {
                log.debug("No body to parse");
            }
        } catch (IOException e) {
            log.error("Error while getting body form the request");
        }
        JsonNode error = null;
        if (uri.contains("api/")) {
            ErrorResponse errorResponse = new ErrorResponse() {

                @Override
                public HttpStatusCode getStatusCode() {
                    return HttpStatus.UNAUTHORIZED;
                }

                @Override
                public ProblemDetail getBody() {
                    return createProblemDetail(ex, getStatusCode(), uri, NOT_AUTH, null, request);
                }
            };
            error = CatalogSerializer.serializeProtocolJsonNode(errorResponse);
        } else {
            if (uri.contains("catalog")) {
                CatalogError catalogError = CatalogError.Builder.newInstance()
                        .code(NOT_AUTH_CODE)
                        .reason(List.of(NOT_AUTH))
                        .build();
                error = CatalogSerializer.serializeProtocolJsonNode(catalogError);
            } else if (uri.contains("negotiations")) {
                ContractNegotiationErrorMessage negotiationError = ContractNegotiationErrorMessage.Builder.newInstance()
                        .consumerPid(consumerPid)
                        .providerPid(providerPid)
                        .code(NOT_AUTH_CODE)
                        .reason(List.of(NOT_AUTH))
                        .build();
                error = CatalogSerializer.serializeProtocolJsonNode(negotiationError);
            } else if (uri.contains("transfers")) {
                TransferError transferError = TransferError.Builder.newInstance()
                        .consumerPid(consumerPid)
                        .providerPid(providerPid)
                        .code(NOT_AUTH_CODE)
                        .reason(List.of(NOT_AUTH))
                        .build();
                error = CatalogSerializer.serializeProtocolJsonNode(transferError);
            }
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).contentType(MediaType.APPLICATION_JSON).body(error);
    }
}
