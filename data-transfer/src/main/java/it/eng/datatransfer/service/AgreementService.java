package it.eng.datatransfer.service;

import org.springframework.stereotype.Service;

import it.eng.datatransfer.exceptions.AgreementNotFoundException;
import it.eng.datatransfer.model.TransferProcess;
import it.eng.datatransfer.repository.TransferProcessRepository;
import it.eng.tools.client.rest.OkHttpRestClient;
import it.eng.tools.controller.ApiEndpoints;
import it.eng.tools.property.ConnectorProperties;
import it.eng.tools.response.GenericApiResponse;
import it.eng.tools.usagecontrol.UsageControlProperties;
import it.eng.tools.util.CredentialUtils;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class AgreementService {
	
	private final TransferProcessRepository transferProcessRepository;
	private final UsageControlProperties usageControlProperties;
	private final OkHttpRestClient okHttpRestClient;
	private final CredentialUtils credentialUtils;
	private final ConnectorProperties connectorProperties;
	
	public AgreementService(TransferProcessRepository transferProcessRepository, UsageControlProperties usageControlProperties,
			OkHttpRestClient okHttpRestClient, CredentialUtils credentialUtils, ConnectorProperties connectorProperties) {
		super();
		this.transferProcessRepository = transferProcessRepository;
		this.usageControlProperties = usageControlProperties;
		this.okHttpRestClient = okHttpRestClient;
		this.credentialUtils = credentialUtils;
		this.connectorProperties = connectorProperties;
	}

	public boolean isAgreementValid(String consumerPid, String providerPid) {
		if(usageControlProperties.usageControlEnabled()) {
			TransferProcess transferProcess = transferProcessRepository.findByConsumerPidAndProviderPid(consumerPid, providerPid)
					.orElseThrow(() -> new AgreementNotFoundException("Agreement for consumerPid '"+ consumerPid +
							"' and providerPid '" + providerPid + "' not found", consumerPid, providerPid));
			String agreementId = transferProcess.getAgreementId();
			
			GenericApiResponse<String> response = okHttpRestClient.sendRequestProtocol(connectorProperties.getConnectorURL() 
					+ ApiEndpoints.NEGOTIATION_AGREEMENTS_V1 + "/" + agreementId + "/enforce", 
					null, 
					credentialUtils.getAPICredentials());
			if (!response.isSuccess()) {
				log.info("Agreement is not valid");
				return false;
			}
		} else {
			log.info("UsageControl DISABLED - will not check if agreement is present or valid");
		}
		return true;
	}

}
