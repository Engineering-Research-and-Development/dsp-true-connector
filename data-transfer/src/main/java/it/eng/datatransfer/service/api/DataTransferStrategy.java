package it.eng.datatransfer.service.api;

import it.eng.datatransfer.model.EndpointProperty;
import it.eng.datatransfer.model.TransferProcess;
import it.eng.tools.model.IConstants;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public abstract class DataTransferStrategy {
    public abstract CompletableFuture<Void> transfer(TransferProcess transferProcess);

    protected String extractAuthorization(TransferProcess transferProcess) {
        if (transferProcess.getDataAddress().getEndpointProperties() != null) {
            List<EndpointProperty> properties = transferProcess.getDataAddress().getEndpointProperties();
            String authType = properties.stream()
                    .filter(prop -> StringUtils.equals(prop.getName(), IConstants.AUTH_TYPE))
                    .findFirst()
                    .map(EndpointProperty::getValue)
                    .orElse(null);
            String token = properties.stream()
                    .filter(prop -> StringUtils.equals(prop.getName(), IConstants.AUTHORIZATION))
                    .findFirst()
                    .map(EndpointProperty::getValue)
                    .orElse(null);

            if (authType != null && token != null) {
                return authType + " " + token;
            }
        }
        return null;
    }
}
