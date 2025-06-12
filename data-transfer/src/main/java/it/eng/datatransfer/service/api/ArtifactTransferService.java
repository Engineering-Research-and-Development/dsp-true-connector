package it.eng.datatransfer.service.api;

import com.fasterxml.jackson.core.type.TypeReference;
import it.eng.datatransfer.exceptions.DownloadException;
import it.eng.datatransfer.model.TransferProcess;
import it.eng.datatransfer.serializer.TransferSerializer;
import it.eng.tools.client.rest.OkHttpRestClient;
import it.eng.tools.controller.ApiEndpoints;
import it.eng.tools.model.Artifact;
import it.eng.tools.response.GenericApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class ArtifactTransferService {

    private final OkHttpRestClient okHttpRestClient;

    public ArtifactTransferService(OkHttpRestClient okHttpRestClient) {
        this.okHttpRestClient = okHttpRestClient;
    }

    /**
     * Finds the artifact associated with the given transfer process.
     *
     * @param transferProcess the transfer process containing the dataset ID
     * @return the artifact associated with the transfer process
     * @throws DownloadException if no artifact is found or if an error occurs during retrieval
     */
    public Artifact findArtifact(TransferProcess transferProcess) {
        TypeReference<GenericApiResponse<Artifact>> typeRef = new TypeReference<GenericApiResponse<Artifact>>() {
        };

        String response = okHttpRestClient.sendInternalRequest(ApiEndpoints.CATALOG_DATASETS_V1 + "/" + transferProcess.getDatasetId() + "/artifact", HttpMethod.GET, null);
        GenericApiResponse<Artifact> genericResponse = TransferSerializer.deserializePlain(response, typeRef);

        if (genericResponse.getData() == null) {
            throw new DownloadException("No such data exists", HttpStatus.NOT_FOUND);
        }
        return genericResponse.getData();
    }
}
