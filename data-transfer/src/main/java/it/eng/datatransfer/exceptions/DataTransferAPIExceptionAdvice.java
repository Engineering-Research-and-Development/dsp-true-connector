package it.eng.datatransfer.exceptions;

import it.eng.datatransfer.rest.api.DataTransferAPIController;
import it.eng.tools.response.GenericApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice(basePackageClasses = {DataTransferAPIController.class})
public class DataTransferAPIExceptionAdvice extends ResponseEntityExceptionHandler {

    @ExceptionHandler(value = {DataTransferAPIException.class})
    protected ResponseEntity<Object> handleDataTransferAPIException(DataTransferAPIException ex, WebRequest request) {
        return new ResponseEntity<>(GenericApiResponse.error(ex.getTransferError(), ex.getLocalizedMessage()), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(value = {TransferProcessInvalidStateException.class})
    protected ResponseEntity<Object> handleTransferProcessInvalidStateException(TransferProcessInvalidStateException ex, WebRequest request) {
        return new ResponseEntity<>(GenericApiResponse.error(ex, ex.getLocalizedMessage()), HttpStatus.BAD_REQUEST);
    }

}
