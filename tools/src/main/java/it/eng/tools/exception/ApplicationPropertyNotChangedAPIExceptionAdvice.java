package it.eng.tools.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import it.eng.tools.response.GenericApiResponse;
import it.eng.tools.rest.api.ApplicationPropertiesAPIController;

@RestControllerAdvice(basePackageClasses = {ApplicationPropertiesAPIController.class})
public class ApplicationPropertyNotChangedAPIExceptionAdvice extends ResponseEntityExceptionHandler {

	@ExceptionHandler(value = {ApplicationPropertyNotChangedAPIException.class})
	public ResponseEntity<Object> handleApplicationPropertyAPIException(ApplicationPropertyNotChangedAPIException ex) {
		return new ResponseEntity<>(GenericApiResponse.error(ex.getLocalizedMessage(), HttpStatus.BAD_REQUEST.value()), HttpStatus.BAD_REQUEST);
	}

}
