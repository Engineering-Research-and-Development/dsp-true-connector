package it.eng.catalog.exceptions;

import it.eng.catalog.model.*;
import it.eng.catalog.rest.protocol.CatalogProtocolController;
import it.eng.catalog.service.CatalogService;
import jakarta.validation.ValidationException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.Arrays;

@RestControllerAdvice(basePackageClasses = {CatalogProtocolController.class, CatalogService.class})
public class CatalogExceptionAdvice extends ResponseEntityExceptionHandler {

    @ExceptionHandler(value = {CatalogErrorException.class})
    protected ResponseEntity<String> handleCatalogErrorException(CatalogErrorException ex) {
        CatalogError catalogError = CatalogError.Builder.newInstance()
        		.code(HttpStatus.NOT_FOUND.getReasonPhrase())
                .reason(Arrays.asList(Reason.Builder.newInstance().language("en").value(ex.getLocalizedMessage()).build()))
                .build();
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Serializer.serializeProtocol(catalogError));
    }

    @ExceptionHandler(value = {CatalogNotFoundException.class})
    protected ResponseEntity<?> handleCatalogNotFoundException(CatalogNotFoundException ex) {
        return new ResponseEntity<>(GenericApiResponse.error(ex.getLocalizedMessage()), HttpStatus.NOT_FOUND);
    }
    
    @ExceptionHandler(value = {ValidationException.class})
    protected ResponseEntity<?> handleValidationException(ValidationException ex) {
    	  CatalogError catalogError = CatalogError.Builder.newInstance()
    			  .code(HttpStatus.BAD_REQUEST.getReasonPhrase())
                  .reason(Arrays.asList(Reason.Builder.newInstance().language("en").value(ex.getLocalizedMessage()).build()))
                  .build();
    	  return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Serializer.serializeProtocol(catalogError));
    }
}
