package it.eng.catalog.exceptions;

import java.util.Arrays;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import it.eng.catalog.model.CatalogError;
import it.eng.catalog.model.GenericApiResponse;
import it.eng.catalog.model.Reason;
import it.eng.catalog.model.Serializer;
import it.eng.catalog.rest.protocol.CatalogProtocolController;
import jakarta.validation.ValidationException;

@RestControllerAdvice(basePackageClasses = CatalogProtocolController.class)
public class CatalogExceptionAdvice extends ResponseEntityExceptionHandler {

    @ExceptionHandler(value = {CatalogErrorException.class})
    protected ResponseEntity<Object> handleCatalogErrorException(CatalogErrorException ex) {
        CatalogError catalogError = CatalogError.Builder.newInstance()
        		.code(HttpStatus.NOT_FOUND.getReasonPhrase())
                .reason(Arrays.asList(Reason.Builder.newInstance().language("en").value(ex.getLocalizedMessage()).build()))
                .build();
//        return handleExceptionInternal(ex, response, new HttpHeaders(), HttpStatus.NOT_FOUND, request);
        
        return ResponseEntity.status(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_JSON).body(Serializer.serializeProtocol(catalogError));
    }

    @ExceptionHandler(value = {CatalogNotFoundException.class})
    protected ResponseEntity<Object> handleCatalogNotFoundException(CatalogNotFoundException ex) {
        return new ResponseEntity<>(GenericApiResponse.error(ex.getLocalizedMessage()), HttpStatus.NOT_FOUND);
    }
    
    @ExceptionHandler(value = {ValidationException.class})
    protected ResponseEntity<Object> handleValidationException(ValidationException ex) {
    	  CatalogError catalogError = CatalogError.Builder.newInstance()
    			  .code(HttpStatus.BAD_REQUEST.getReasonPhrase())
                  .reason(Arrays.asList(Reason.Builder.newInstance().language("en").value(ex.getLocalizedMessage()).build()))
                  .build();
    	  return ResponseEntity.status(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON).body(Serializer.serializeProtocol(catalogError));
    }
}
