package it.eng.negotiation.exception;

import java.util.Arrays;
import java.util.Collections;

import it.eng.negotiation.model.Serializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.fasterxml.jackson.databind.JsonNode;

import it.eng.negotiation.model.ContractNegotiationErrorMessage;
import it.eng.negotiation.model.Description;
import it.eng.negotiation.model.Reason;
import it.eng.negotiation.transformer.from.JsonFromContractNegotiationErrorMessageTrasformer;

@RestControllerAdvice
public class ContractNegotiationExceptionAdvice extends ResponseEntityExceptionHandler {

    @Value("${application.connectorid}")
    private String connectroId;

    @Value("${application.isconsumer}")
    private boolean isConsumer;

    @ExceptionHandler(value = {ContractNegotiationNotFoundException.class})
    protected ResponseEntity<Object> handleContractNotFound(ContractNegotiationNotFoundException ex, WebRequest request) {

        ContractNegotiationErrorMessage errorMessage = ContractNegotiationErrorMessage.Builder.newInstance()
                .providerPid(ex.getProviderId())
                .code(HttpStatus.NOT_FOUND.getReasonPhrase())
                .reason(Collections.singletonList(Reason.Builder.newInstance().language("en").value(ex.getLocalizedMessage()).build()))
                .description(Collections.singletonList(Description.Builder.newInstance().language("en").value(ex.getLocalizedMessage()).build())).build();
        String response = Serializer.serializeProtocol(errorMessage);

        return handleExceptionInternal(ex, response, new HttpHeaders(), HttpStatus.NOT_FOUND, request);
    }
}
