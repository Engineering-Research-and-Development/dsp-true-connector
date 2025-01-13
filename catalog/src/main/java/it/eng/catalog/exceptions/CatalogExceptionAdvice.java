package it.eng.catalog.exceptions;

import it.eng.catalog.model.CatalogError;
import it.eng.catalog.model.Reason;
import it.eng.catalog.rest.protocol.CatalogController;
import it.eng.catalog.serializer.CatalogSerializer;
import jakarta.validation.ValidationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.Arrays;

@RestControllerAdvice(basePackageClasses = CatalogController.class)
public class CatalogExceptionAdvice extends ResponseEntityExceptionHandler {

    @ExceptionHandler(value = {CatalogErrorException.class})
    protected ResponseEntity<Object> handleCatalogErrorException(CatalogErrorException ex, WebRequest request) {
        CatalogError catalogError = CatalogError.Builder.newInstance()
                .code(HttpStatus.NOT_FOUND.getReasonPhrase())
                .reason(Arrays.asList(Reason.Builder.newInstance().language("en").value(ex.getLocalizedMessage()).build()))
                .build();

        return handleExceptionInternal(ex, CatalogSerializer.serializeProtocolJsonNode(catalogError), new HttpHeaders(), HttpStatus.NOT_FOUND, request);
    }

    @ExceptionHandler(value = {ValidationException.class})
    protected ResponseEntity<Object> handleValidationException(ValidationException ex, WebRequest request) {
        CatalogError catalogError = CatalogError.Builder.newInstance()
                .code(HttpStatus.BAD_REQUEST.getReasonPhrase())
                .reason(Arrays.asList(Reason.Builder.newInstance().language("en").value(ex.getLocalizedMessage()).build()))
                .build();

        return handleExceptionInternal(ex, CatalogSerializer.serializeProtocolJsonNode(catalogError), new HttpHeaders(), HttpStatus.BAD_REQUEST, request);
    }
}
