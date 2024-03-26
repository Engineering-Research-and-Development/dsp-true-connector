package it.eng.negotiation.exception;

import it.eng.negotiation.model.ContractNegotiationErrorMessage;
import it.eng.negotiation.model.Description;
import it.eng.negotiation.model.Reason;
import it.eng.negotiation.model.Serializer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.Collections;

@RestControllerAdvice
public class ContractNegotiationExceptionAdvice extends ResponseEntityExceptionHandler {

    @ExceptionHandler(value = {ContractNegotiationNotFoundException.class})
    protected ResponseEntity<Object> handleContractNotFound(ContractNegotiationNotFoundException ex, WebRequest request) {

        ContractNegotiationErrorMessage errorMessage = ContractNegotiationErrorMessage.Builder.newInstance()
                .providerPid(ex.getProviderPid())
                .code(HttpStatus.NOT_FOUND.getReasonPhrase())
                .reason(Collections.singletonList(Reason.Builder.newInstance().language("en").value(ex.getLocalizedMessage()).build()))
                .description(Collections.singletonList(Description.Builder.newInstance().language("en").value(ex.getLocalizedMessage()).build())).build();
        String response = Serializer.serializeProtocol(errorMessage);

        return handleExceptionInternal(ex, response, new HttpHeaders(), HttpStatus.NOT_FOUND, request);
    }
}
