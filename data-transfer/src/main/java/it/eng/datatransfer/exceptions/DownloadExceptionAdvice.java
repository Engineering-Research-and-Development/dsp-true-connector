package it.eng.datatransfer.exceptions;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import it.eng.datatransfer.rest.api.RestArtifactController;
import it.eng.datatransfer.service.api.RestArtifactService;

@RestControllerAdvice(basePackageClasses = {RestArtifactController.class, RestArtifactService.class})
public class DownloadExceptionAdvice extends ResponseEntityExceptionHandler{
	
	@ExceptionHandler(value = {DownloadException.class })
	protected ResponseEntity<Object> handleTransferProcessArtifactNotFoundException(DownloadException ex, WebRequest request) {
		return handleExceptionInternal(ex, ex.getLocalizedMessage(), new HttpHeaders(),
				ex.getHttpStatus(), request);
	}

}
