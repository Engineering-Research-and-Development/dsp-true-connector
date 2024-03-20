package it.eng.negotiation.exception;

import java.util.Arrays;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.fasterxml.jackson.databind.JsonNode;

import it.eng.negotiation.model.ContractNegotiationErrorMessage;
import it.eng.negotiation.model.Description;
import it.eng.negotiation.model.Reason;
import it.eng.negotiation.transformer.from.JsonFromContractNegotiationErrorMessageTrasformer;

@RestControllerAdvice
public class ContractNegotiationExceptionAdvice extends ResponseEntityExceptionHandler {
	
	@Value("${application.connectorid}")
	private String connectroId;
	
	@Value("${application.isconsumer}")
	private boolean isConsumer;

	@ExceptionHandler(value = { ContractNegotiationNotFoundException.class })
	protected ResponseEntity<Object> handleContractNotFound(RuntimeException ex, WebRequest request) {
		JsonFromContractNegotiationErrorMessageTrasformer transformer = new JsonFromContractNegotiationErrorMessageTrasformer();
		ContractNegotiationErrorMessage.Builder errorMessageBuilder = ContractNegotiationErrorMessage.Builder.newInstance()
			.code(HttpStatus.NOT_FOUND.getReasonPhrase())
			.reason(Arrays.asList(Reason.Builder.newInstance().language("en").value(ex.getLocalizedMessage()).build()))
			.description(Arrays.asList(Description.Builder.newInstance().language("en").value(ex.getLocalizedMessage()).build()));
		if(isConsumer) {
			errorMessageBuilder.consumerPid(connectroId);
		} else {
			errorMessageBuilder.providerPid(connectroId);
		}
		JsonNode bodyOfResponse = transformer.transform(errorMessageBuilder.build());

		return handleExceptionInternal(ex, bodyOfResponse, new HttpHeaders(), HttpStatus.NOT_FOUND, request);
	}
}
