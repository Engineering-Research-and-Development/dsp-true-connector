package it.eng.datatransfer.exceptions;

import java.util.Collections;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import it.eng.datatransfer.model.TransferError;
import it.eng.datatransfer.rest.protocol.ProviderDataTransferController;
import it.eng.datatransfer.serializer.TransferSerializer;
import it.eng.datatransfer.service.DataTransferService;
import jakarta.validation.ValidationException;

@RestControllerAdvice(basePackageClasses = { ProviderDataTransferController.class, DataTransferService.class})
public class DataTransferExceptionAdvice extends ResponseEntityExceptionHandler {

	@ExceptionHandler(value = { ValidationException.class })
	protected ResponseEntity<Object> handleValidationException(ValidationException ex, WebRequest request) {
		TransferError errorMessage = TransferError.Builder.newInstance()
				.consumerPid("COULD_NOT_PROCESS")
				.providerPid("COULD_NOT_PROCESS")
				.code(HttpStatus.BAD_REQUEST.getReasonPhrase())
				.reason(Collections.singletonList(ex.getLocalizedMessage()))
				.build();
		return handleExceptionInternal(ex, TransferSerializer.serializeProtocolJsonNode(errorMessage), new HttpHeaders(),
				HttpStatus.BAD_REQUEST, request);
	}
	
	@ExceptionHandler(value = { TransferProcessNotFoundException.class })
	protected ResponseEntity<Object> handleTransferProcessNotFoundException(TransferProcessNotFoundException ex, WebRequest request) {
		TransferError errorMessage = TransferError.Builder.newInstance()
				.consumerPid("COULD_NOT_PROCESS")
				.providerPid("COULD_NOT_PROCESS")
				.code(HttpStatus.BAD_REQUEST.getReasonPhrase())
				.reason(Collections.singletonList(ex.getLocalizedMessage()))
				.build();
		return handleExceptionInternal(ex, TransferSerializer.serializeProtocolJsonNode(errorMessage), new HttpHeaders(),
				HttpStatus.BAD_REQUEST, request);
	}

	@ExceptionHandler(value = { TransferProcessExistsException.class })
	protected ResponseEntity<Object> handleTransferProcessExistsException(TransferProcessExistsException ex,
			WebRequest request) {
		TransferError errorMessage = TransferError.Builder.newInstance()
				.consumerPid(ex.getConsumerPid())
				.providerPid("COULD_NOT_PROCESS")
				.code(HttpStatus.BAD_REQUEST.getReasonPhrase())
				.reason(Collections.singletonList(ex.getLocalizedMessage()))
				.build();
		return handleExceptionInternal(ex, TransferSerializer.serializeProtocolJsonNode(errorMessage), new HttpHeaders(),
				HttpStatus.BAD_REQUEST, request);
	}
	
	@ExceptionHandler(value = { TransferProcessInvalidStateException.class })
	protected ResponseEntity<Object> handleTransferProcessInvalidStateException(TransferProcessInvalidStateException ex,
			WebRequest request) {
		TransferError errorMessage = TransferError.Builder.newInstance()
				.consumerPid(ex.getConsumerPid())
				.providerPid(ex.getProviderPid())
				.code(HttpStatus.BAD_REQUEST.getReasonPhrase())
				.reason(Collections.singletonList(ex.getLocalizedMessage()))
				.build();
		return handleExceptionInternal(ex, TransferSerializer.serializeProtocolJsonNode(errorMessage), new HttpHeaders(),
				HttpStatus.BAD_REQUEST, request);
	}
	
	@ExceptionHandler(value = { TransferProcessInvalidFormatException.class })
	protected ResponseEntity<Object> handleTransferProcessInvalidFormatException(TransferProcessInvalidFormatException ex,
			WebRequest request) {
		TransferError errorMessage = TransferError.Builder.newInstance()
				.consumerPid(ex.getConsumerPid())
				.providerPid(ex.getProviderPid())
				.code(HttpStatus.BAD_REQUEST.getReasonPhrase())
				.reason(Collections.singletonList(ex.getLocalizedMessage()))
				.build();
		return handleExceptionInternal(ex, TransferSerializer.serializeProtocolJsonNode(errorMessage), new HttpHeaders(),
				HttpStatus.BAD_REQUEST, request);
	}
	
	@ExceptionHandler(value = { AgreementNotFoundException.class })
	protected ResponseEntity<Object> handleAgreementNotFoundException(AgreementNotFoundException ex,
			WebRequest request) {
		TransferError errorMessage = TransferError.Builder.newInstance()
				.consumerPid(ex.getConsumerPid())
				.providerPid(ex.getProviderPid())
				.code(HttpStatus.BAD_REQUEST.getReasonPhrase())
				.reason(Collections.singletonList(ex.getLocalizedMessage()))
				.build();
		return handleExceptionInternal(ex, TransferSerializer.serializeProtocolJsonNode(errorMessage), new HttpHeaders(),
				HttpStatus.BAD_REQUEST, request);
	}
}
