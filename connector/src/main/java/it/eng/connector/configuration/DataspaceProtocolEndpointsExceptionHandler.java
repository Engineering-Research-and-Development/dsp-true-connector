package it.eng.connector.configuration;

import java.util.Arrays;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.fasterxml.jackson.databind.JsonNode;

import it.eng.catalog.model.CatalogError;
import it.eng.catalog.serializer.Serializer;
import it.eng.datatransfer.model.TransferError;
import it.eng.negotiation.model.ContractNegotiationErrorMessage;

@ControllerAdvice
public class DataspaceProtocolEndpointsExceptionHandler extends ResponseEntityExceptionHandler {

	private static final String EN = "en";
	private static final String NOT_AUTH = "Not authorized";
	private static final String NOT_AUTH_CODE = String.valueOf(HttpStatus.UNAUTHORIZED.value());
	
	@ExceptionHandler({ AuthenticationException.class })
    @ResponseBody
    public ResponseEntity<JsonNode> handleAuthenticationException(Exception ex, WebRequest request) {
		String uri = ((ServletWebRequest)request).getRequest().getRequestURI().toString();
		JsonNode error = null;
		if(uri.contains("api/")) {
			ErrorResponse errorResponse = new ErrorResponse() {
				
				@Override
				public HttpStatusCode getStatusCode() {
					return HttpStatus.UNAUTHORIZED;
				}
				
				@Override
				public ProblemDetail getBody() {
					return createProblemDetail(ex, getStatusCode(), uri, NOT_AUTH, null, request);
				}
			};
			error = Serializer.serializeProtocolJsonNode(errorResponse);
		} else {
			if(uri.contains("catalog")) {
				CatalogError catalogError = CatalogError.Builder.newInstance()
						.code(NOT_AUTH_CODE)
						.reason(Arrays.asList(it.eng.catalog.model.Reason.Builder.newInstance().language(EN).value(NOT_AUTH).build()))
						.build();
				error = Serializer.serializeProtocolJsonNode(catalogError);
			} else if(uri.contains("negotiations")) {
				ContractNegotiationErrorMessage negotationError = ContractNegotiationErrorMessage.Builder.newInstance()
						.consumerPid("NA")
						.providerPid("NA")
						.code(NOT_AUTH_CODE)
						.reason(Arrays.asList(it.eng.negotiation.model.Reason.Builder.newInstance().language(EN).value(NOT_AUTH).build()))
						.build();
				error = Serializer.serializeProtocolJsonNode(negotationError);
			} else if(uri.contains("transfers")) {
				TransferError transferError = TransferError.Builder.newInstance()
						.consumerPid("NA")
						.providerPid("NA")
						.code(NOT_AUTH_CODE)
						.reason(Arrays.asList(it.eng.datatransfer.model.Reason.Builder.newInstance().language(EN).value(NOT_AUTH).build()))
						.build();
				error = Serializer.serializeProtocolJsonNode(transferError);
			}
		}
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED).contentType(MediaType.APPLICATION_JSON).body(error);
    }
}
