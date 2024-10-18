package it.eng.negotiation.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import it.eng.negotiation.rest.api.ContractNegotiationAPIController;
import it.eng.tools.response.GenericApiResponse;

@RestControllerAdvice(basePackageClasses = {ContractNegotiationAPIController.class})
public class ContractNegotiationAPIExceptionAdvice extends ResponseEntityExceptionHandler {

    @ExceptionHandler(value = {ContractNegotiationAPIException.class, PolicyEnforcementException.class})
    protected ResponseEntity<Object> handleContractNegotiationAPIException(ContractNegotiationAPIException ex, WebRequest request) {
    	return new ResponseEntity<>(GenericApiResponse.error(ex.getLocalizedMessage()), HttpStatus.BAD_REQUEST);
    }
    
}
