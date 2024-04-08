package it.eng.catalog.exceptions;

import it.eng.catalog.model.*;
import jakarta.validation.ValidationException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.Arrays;

@RestControllerAdvice
public class CatalogExceptionAdvice extends ResponseEntityExceptionHandler {

    @ExceptionHandler(value = {CatalogErrorException.class})
    protected ResponseEntity<String> handleCatalogErrorException(CatalogErrorException ex) {

        CatalogError catalogError = CatalogError.Builder.newInstance().code(HttpStatus.NOT_FOUND.getReasonPhrase())
                .reason(Arrays.asList(Reason.Builder.newInstance().language("en").value(ex.getLocalizedMessage()).build())).build();

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Serializer.serializeProtocol(catalogError));
    }

    @ExceptionHandler(value = {CatalogNotFoundException.class})
    protected ResponseEntity<?> handleCatalogNotFoundException(CatalogNotFoundException ex) {

        return new ResponseEntity<>(GenericApiResponse.error(ex.getLocalizedMessage()), HttpStatus.NOT_FOUND);
    }
    
    @ExceptionHandler(value = {ValidationException.class})
    protected ResponseEntity<?> handleValidationException(CatalogNotFoundException ex) {
        return new ResponseEntity<>(GenericApiResponse.error(ex.getLocalizedMessage()), HttpStatus.BAD_REQUEST);
    }
}
