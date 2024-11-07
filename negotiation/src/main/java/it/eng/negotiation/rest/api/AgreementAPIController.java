package it.eng.negotiation.rest.api;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import it.eng.negotiation.service.AgreementAPIService;
import it.eng.tools.controller.ApiEndpoints;
import it.eng.tools.response.GenericApiResponse;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE, path = ApiEndpoints.NEGOTIATION_AGREEMENTS_V1)
@Slf4j
public class AgreementAPIController {

	private AgreementAPIService agreementAPIService;

	public AgreementAPIController(AgreementAPIService agreementAPIService) {
		super();
		this.agreementAPIService = agreementAPIService;
	}
	
	 @PostMapping(path = "/{agreementId}/enforce")
	    public ResponseEntity<GenericApiResponse<String>> enforceAgreement(@PathVariable String agreementId) {
	        log.info("Enforcing agreement");
	        agreementAPIService.enforceAgreement(agreementId);
	        return ResponseEntity.ok()
	        		.contentType(MediaType.APPLICATION_JSON)
	        		.body(GenericApiResponse.success("Agreement enforcement is valid", "Agreement enforcement is ok"));
	 }

}
