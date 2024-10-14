package it.eng.datatransfer.service;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import it.eng.datatransfer.exceptions.AgreementNotFoundException;
import it.eng.datatransfer.model.TransferProcess;
import it.eng.datatransfer.repository.TransferProcessRepository;
import it.eng.tools.client.rest.OkHttpRestClient;
import it.eng.tools.controller.ApiEndpoints;
import it.eng.tools.response.GenericApiResponse;
import it.eng.tools.util.CredentialUtils;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class AgreementService {
	
	@Value("${server.port}")
	private String serverPort;
	
	Map<String, Boolean> agreementStorage = new HashMap<>();
	private TransferProcessRepository transferProcessRepository;
	private OkHttpRestClient okHttpRestClient;
	private CredentialUtils credentialUtils;
	
	public AgreementService(TransferProcessRepository transferProcessRepository, OkHttpRestClient okHttpRestClient,
			CredentialUtils credentialUtils) {
		super();
		this.transferProcessRepository = transferProcessRepository;
		this.okHttpRestClient = okHttpRestClient;
		this.credentialUtils = credentialUtils;
	}

	public boolean isAgreementValid(String consumerPid, String providerPid) {
		TransferProcess transferProcess = transferProcessRepository.findByConsumerPidAndProviderPid(consumerPid, providerPid)
				.orElseThrow(() -> new AgreementNotFoundException("Agreement for cosnumerPid '"+ consumerPid +
						"' and providerPid '" + providerPid + "' not found", consumerPid, providerPid));
		
		String agreementId = transferProcess.getAgreementId();
		// TODO once usage policy enforcement is done, call should be made here

		GenericApiResponse<String> response = okHttpRestClient.sendRequestProtocol("http://localhost:" + serverPort 
			+ ApiEndpoints.NEGOTIATION_AGREEMENTS_V1 + "/" + agreementId + "/enforce", 
				null, 
				credentialUtils.getAPICredentials());
        
		if (!response.isSuccess()) {
			log.info("Agreement is not valid");
			return false;
		}
		
		return true;
//		if(!agreementStorage.containsKey(agreementId)) {
//			agreementStorage.put(agreementId, isVaid);
//		}
//		log.info("Validating agreement id {}", agreementId);
//		return agreementStorage.get(agreementId);
	}

	private boolean checkIfAgreementIsValid(String agreementId) {
		/*
		 * "urn:uuid:AGREEMENT_ID".equals(agreementId) 
				|| "urn:uuid:AGREEMENT_ID_COMPLETED_TRANSFER_TEST".equals(agreementId)
				|| "urn:uuid:AGREEMENT_ID_SUSP".equals(agreementId)
		 */
		// TODO add valid logic, for now, just check if agreementId starts with, to avoid adding all use cases for integration tests
		if (agreementId.startsWith("urn:uuid:AGREEMENT_ID")) {
			   return true;
			} else {
			   return false;
			}
	}
}
