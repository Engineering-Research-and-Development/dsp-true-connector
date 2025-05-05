package it.eng.catalog.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import it.eng.catalog.exceptions.CatalogErrorAPIException;
import it.eng.catalog.model.Catalog;
import it.eng.catalog.model.CatalogRequestMessage;
import it.eng.catalog.serializer.CatalogSerializer;
import it.eng.tools.client.rest.OkHttpRestClient;
import it.eng.tools.response.GenericApiResponse;
import it.eng.tools.util.CredentialUtils;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class ProxyAPIService {
	
	private final OkHttpRestClient okHttpClient;
	private final CredentialUtils credentialUtils;
	
	public ProxyAPIService(OkHttpRestClient okHttpClient, CredentialUtils credentialUtils) {
		super();
		this.okHttpClient = okHttpClient;
		this.credentialUtils = credentialUtils;
	}

	public List<String> getFormatsFromDataset(String datasetId, String forwardTo) {
		Catalog catalog = getCatalog(forwardTo);
		List<String> formats = catalog.getDataset().stream()
			.filter(ds -> ds.getId().equals(datasetId))
			.flatMap(ds -> ds.getDistribution().stream())
			.map(dist -> dist.getFormat().getId())
			.collect(Collectors.toList());
		return formats;
	}

	public Catalog getCatalog(String forwardTo) {
		CatalogRequestMessage catalogRequestMessage = CatalogRequestMessage.Builder.newInstance().build();
		GenericApiResponse<String> catalogResponse = okHttpClient.sendRequestProtocol(forwardTo + "/catalog/request", CatalogSerializer.serializeProtocolJsonNode(catalogRequestMessage), 
				credentialUtils.getConnectorCredentials());
		if(catalogResponse.isSuccess()) {
			Catalog catalog = CatalogSerializer.deserializeProtocol(catalogResponse.getData(), Catalog.class);
			return catalog;
		} else {
			log.error("Catalog response not received from  {}", forwardTo);
			throw new CatalogErrorAPIException("Catalog response not received from  " + forwardTo);
		}
	}

}
