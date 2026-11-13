package it.eng.datatransfer.service;

import it.eng.datatransfer.exceptions.AgreementNotFoundException;
import it.eng.datatransfer.model.TransferProcess;
import it.eng.datatransfer.repository.TransferProcessRepository;
import it.eng.tools.client.rest.OkHttpRestClient;
import it.eng.tools.controller.ApiEndpoints;
import it.eng.tools.response.GenericApiResponse;
import it.eng.tools.service.TenantContextHolder;
import it.eng.tools.usagecontrol.UsageControlProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class AgreementService {
	
	private final TransferProcessRepository transferProcessRepository;
	private final UsageControlProperties usageControlProperties;
	private final OkHttpRestClient okHttpRestClient;
	
	public AgreementService(TransferProcessRepository transferProcessRepository, UsageControlProperties usageControlProperties,
			OkHttpRestClient okHttpRestClient) {
		super();
		this.transferProcessRepository = transferProcessRepository;
		this.usageControlProperties = usageControlProperties;
		this.okHttpRestClient = okHttpRestClient;
	}

	public boolean isAgreementValid(String consumerPid, String providerPid) {
		if(usageControlProperties.usageControlEnabled()) {
			String tenantId = TenantContextHolder.getTenantId();
			TransferProcess transferProcess = (tenantId != null
					? transferProcessRepository.findByConsumerPidAndProviderPidAndTenantId(consumerPid, providerPid, tenantId)
					: transferProcessRepository.findByConsumerPidAndProviderPid(consumerPid, providerPid))
					.orElseThrow(() -> new AgreementNotFoundException("Agreement for consumerPid '"+ consumerPid +
							"' and providerPid '" + providerPid + "' not found", consumerPid, providerPid));
			String agreementId = transferProcess.getAgreementId();
			
			GenericApiResponse<String> response = okHttpRestClient.sendInternalPostRequest(
					ApiEndpoints.INTERNAL_AGREEMENTS_V1 + "/" + agreementId + "/enforce", null);
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
