package it.eng.catalog.service;

import it.eng.catalog.exceptions.CatalogErrorAPIException;
import it.eng.catalog.model.Catalog;
import it.eng.catalog.model.CatalogError;
import it.eng.catalog.model.CatalogRequestMessage;
import it.eng.catalog.model.Distribution;
import it.eng.catalog.serializer.CatalogSerializer;
import it.eng.tools.client.rest.OkHttpRestClient;
import it.eng.tools.response.GenericApiResponse;
import it.eng.tools.util.CredentialUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

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
        return catalog.getDataset().stream()
                .filter(ds -> ds.getId().equals(datasetId))
                .flatMap(ds -> ds.getDistribution().stream())
                .map(Distribution::getFormat)
                .collect(Collectors.toList());
    }

    public Catalog getCatalog(String forwardTo) {
        CatalogRequestMessage catalogRequestMessage = CatalogRequestMessage.Builder.newInstance().build();
        GenericApiResponse<String> catalogResponse = okHttpClient.sendRequestProtocol(forwardTo + "/catalog/request",
                CatalogSerializer.serializeProtocolJsonNode(catalogRequestMessage),
                //TODO add credentials management for VC - pass forwardTo - add check if vc enabled
                credentialUtils.getConnectorCredentials());
        if (catalogResponse.isSuccess()) {
            return CatalogSerializer.deserializeProtocol(catalogResponse.getData(), Catalog.class);
        } else {
            CatalogError catalogError = CatalogSerializer.deserializeProtocol(catalogResponse.getData(), CatalogError.class);
            log.error("No valid Catalog response received from  {}, : {} ", forwardTo, catalogError.getReason());
            throw new CatalogErrorAPIException("Catalog response not received from  " + forwardTo
                    + " : " + catalogError.getReason());
        }
    }

}
