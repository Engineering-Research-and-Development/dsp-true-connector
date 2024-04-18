package it.eng.negotiation.exception;

import java.util.Collections;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import it.eng.negotiation.model.ContractNegotiationErrorMessage;
import it.eng.negotiation.model.Description;
import it.eng.negotiation.model.Reason;
import it.eng.negotiation.model.Serializer;
import it.eng.negotiation.rest.protocol.ConsumerContractNegotiationCallbackController;
import it.eng.negotiation.rest.protocol.ProviderContractNegotiationController;
import jakarta.validation.ValidationException;

@RestControllerAdvice(basePackageClasses = {ProviderContractNegotiationController.class, ConsumerContractNegotiationCallbackController.class})
public class ContractNegotiationExceptionAdvice extends ResponseEntityExceptionHandler {

    @ExceptionHandler(value = {ContractNegotiationNotFoundException.class})
    protected ResponseEntity<Object> handleContractNotFound(ContractNegotiationNotFoundException ex, WebRequest request) {

        ContractNegotiationErrorMessage errorMessage = ContractNegotiationErrorMessage.Builder.newInstance()
                .providerPid(ex.getProviderPid())
                .code(HttpStatus.NOT_FOUND.getReasonPhrase())
                .reason(Collections.singletonList(Reason.Builder.newInstance().language("en").value(ex.getLocalizedMessage()).build()))
                .description(Collections.singletonList(Description.Builder.newInstance().language("en").value(ex.getLocalizedMessage()).build())).build();

        return handleExceptionInternal(ex, Serializer.serializeProtocolJsonNode(errorMessage), new HttpHeaders(), HttpStatus.NOT_FOUND, request);
    }
    
    @ExceptionHandler(value = {ValidationException.class})
    protected ResponseEntity<Object> handleValidationException(ValidationException ex, WebRequest request) {
    	  ContractNegotiationErrorMessage errorMessage = ContractNegotiationErrorMessage.Builder.newInstance()
    			//TODO add proper provider and consumer pid
    			  .providerPid(createNewId())
    			  .consumerPid(createNewId())
                  .code(HttpStatus.BAD_REQUEST.getReasonPhrase())
                  .reason(Collections.singletonList(Reason.Builder.newInstance().language("en").value(ex.getLocalizedMessage()).build()))
                  .description(Collections.singletonList(Description.Builder.newInstance().language("en").value(ex.getLocalizedMessage()).build())).build();
    	  
    	  return handleExceptionInternal(ex, Serializer.serializeProtocolJsonNode(errorMessage), new HttpHeaders(), HttpStatus.BAD_REQUEST, request);
    }
    

    @ExceptionHandler(value = {ContractNegotiationExistsException.class})
    protected ResponseEntity<Object> handleContractExists(ContractNegotiationExistsException ex, WebRequest request) {

        ContractNegotiationErrorMessage errorMessage = ContractNegotiationErrorMessage.Builder.newInstance()
                .providerPid(ex.getProviderPid())
                .code(HttpStatus.CONFLICT.getReasonPhrase())
                .reason(Collections.singletonList(Reason.Builder.newInstance().language("en").value(ex.getLocalizedMessage()).build()))
                .description(Collections.singletonList(Description.Builder.newInstance().language("en").value(ex.getLocalizedMessage()).build())).build();

        return handleExceptionInternal(ex, Serializer.serializeProtocolJsonNode(errorMessage), new HttpHeaders(), HttpStatus.CONFLICT, request);
    }
    
    private String createNewId() {
    	return "urn:uuid:" + UUID.randomUUID();
    }
}
