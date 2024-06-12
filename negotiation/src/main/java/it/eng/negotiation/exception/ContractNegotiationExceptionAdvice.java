package it.eng.negotiation.exception;

import java.util.Collections;

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
import it.eng.negotiation.rest.protocol.ConsumerContractNegotiationCallbackController;
import it.eng.negotiation.rest.protocol.ProviderContractNegotiationController;
import it.eng.negotiation.serializer.Serializer;
import jakarta.validation.ValidationException;

@RestControllerAdvice(basePackageClasses = {ProviderContractNegotiationController.class, ConsumerContractNegotiationCallbackController.class})
public class ContractNegotiationExceptionAdvice extends ResponseEntityExceptionHandler {
	
	private String PID_NOT_FOUND = "PID_NOT_FOUND";

    @ExceptionHandler(value = {ContractNegotiationNotFoundException.class})
    protected ResponseEntity<Object> handleContractNegotiationNotFoundException(ContractNegotiationNotFoundException ex, WebRequest request) {

        ContractNegotiationErrorMessage errorMessage = ContractNegotiationErrorMessage.Builder.newInstance()
        		.consumerPid(ex.getConsumerPid() != null ?  ex.getConsumerPid() : PID_NOT_FOUND)
                .providerPid(ex.getProviderPid() != null ?  ex.getProviderPid() : PID_NOT_FOUND)
                .code(HttpStatus.BAD_REQUEST.getReasonPhrase())
                .reason(Collections.singletonList(Reason.Builder.newInstance().language("en").value(ex.getLocalizedMessage()).build()))
                .description(Collections.singletonList(Description.Builder.newInstance().language("en").value(ex.getLocalizedMessage()).build())).build();

        return handleExceptionInternal(ex, Serializer.serializeProtocolJsonNode(errorMessage), new HttpHeaders(), HttpStatus.BAD_REQUEST, request);
    }
    
    @ExceptionHandler(value = {ValidationException.class})
    protected ResponseEntity<Object> handleValidationException(ValidationException ex, WebRequest request) {
    	  ContractNegotiationErrorMessage errorMessage = ContractNegotiationErrorMessage.Builder.newInstance()
    			  .consumerPid("COULD_NOT_PROCESS")
                  .providerPid("COULD_NOT_PROCESS")
                  .code(HttpStatus.BAD_REQUEST.getReasonPhrase())
                  .reason(Collections.singletonList(Reason.Builder.newInstance().language("en").value(ex.getLocalizedMessage()).build()))
                  .description(Collections.singletonList(Description.Builder.newInstance().language("en").value(ex.getLocalizedMessage()).build())).build();
    	  
    	  return handleExceptionInternal(ex, Serializer.serializeProtocolJsonNode(errorMessage), new HttpHeaders(), HttpStatus.BAD_REQUEST, request);
    }
    

    @ExceptionHandler(value = {ContractNegotiationExistsException.class})
    protected ResponseEntity<Object> ContractNegotiationExistsException(ContractNegotiationExistsException ex, WebRequest request) {

        ContractNegotiationErrorMessage errorMessage = ContractNegotiationErrorMessage.Builder.newInstance()
        		.consumerPid(ex.getConsumerPid() != null ?  ex.getConsumerPid() : PID_NOT_FOUND)
                .providerPid(ex.getProviderPid() != null ?  ex.getProviderPid() : PID_NOT_FOUND)
                .code(HttpStatus.BAD_REQUEST.getReasonPhrase())
                .reason(Collections.singletonList(Reason.Builder.newInstance().language("en").value(ex.getLocalizedMessage()).build()))
                .description(Collections.singletonList(Description.Builder.newInstance().language("en").value(ex.getLocalizedMessage()).build())).build();

        return handleExceptionInternal(ex, Serializer.serializeProtocolJsonNode(errorMessage), new HttpHeaders(), HttpStatus.BAD_REQUEST, request);
    }
    
    @ExceptionHandler(value = {OfferNotFoundException.class})
    protected ResponseEntity<Object> handleOfferNotFoundException(OfferNotFoundException ex, WebRequest request) {

        ContractNegotiationErrorMessage errorMessage = ContractNegotiationErrorMessage.Builder.newInstance()
        		.consumerPid(ex.getConsumerPid() != null ?  ex.getConsumerPid() : PID_NOT_FOUND)
                .providerPid(ex.getProviderPid() != null ?  ex.getProviderPid() : PID_NOT_FOUND)
                .code(HttpStatus.BAD_REQUEST.getReasonPhrase())
                .reason(Collections.singletonList(Reason.Builder.newInstance().language("en").value(ex.getLocalizedMessage()).build()))
                .description(Collections.singletonList(Description.Builder.newInstance().language("en").value(ex.getLocalizedMessage()).build())).build();

        return handleExceptionInternal(ex, Serializer.serializeProtocolJsonNode(errorMessage), new HttpHeaders(), HttpStatus.BAD_REQUEST, request);
    }
    
    @ExceptionHandler(value = {ProviderPidNotBlankException.class})
    protected ResponseEntity<Object> handleProviderPidNotBlankException(ProviderPidNotBlankException ex, WebRequest request) {

        ContractNegotiationErrorMessage errorMessage = ContractNegotiationErrorMessage.Builder.newInstance()
        		.consumerPid(ex.getConsumerPid() != null ?  ex.getConsumerPid() : PID_NOT_FOUND)
        		.providerPid("HAS_TO_BE_BLANK")
                .code(HttpStatus.BAD_REQUEST.getReasonPhrase())
                .reason(Collections.singletonList(Reason.Builder.newInstance().language("en").value(ex.getLocalizedMessage()).build()))
                .description(Collections.singletonList(Description.Builder.newInstance().language("en").value(ex.getLocalizedMessage()).build())).build();

        return handleExceptionInternal(ex, Serializer.serializeProtocolJsonNode(errorMessage), new HttpHeaders(), HttpStatus.BAD_REQUEST, request);
    }
    
    @ExceptionHandler(value = {OfferNotValidException.class})
    protected ResponseEntity<Object> handleOfferNotValidException(OfferNotValidException ex, WebRequest request) {

        ContractNegotiationErrorMessage errorMessage = ContractNegotiationErrorMessage.Builder.newInstance()
        		.consumerPid(ex.getConsumerPid() != null ?  ex.getConsumerPid() : PID_NOT_FOUND)
        		.providerPid("NOT_CREATED")
                .code(HttpStatus.BAD_REQUEST.getReasonPhrase())
                .reason(Collections.singletonList(Reason.Builder.newInstance().language("en").value(ex.getLocalizedMessage()).build()))
                .description(Collections.singletonList(Description.Builder.newInstance().language("en").value(ex.getLocalizedMessage()).build())).build();

        return handleExceptionInternal(ex, Serializer.serializeProtocolJsonNode(errorMessage), new HttpHeaders(), HttpStatus.BAD_REQUEST, request);
    }
}
