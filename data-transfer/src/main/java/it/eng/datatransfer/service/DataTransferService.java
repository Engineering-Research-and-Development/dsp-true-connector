package it.eng.datatransfer.service;

import it.eng.datatransfer.model.TCKRequest;
import it.eng.datatransfer.model.TransferProcess;
import it.eng.datatransfer.model.TransferState;
import it.eng.datatransfer.repository.TransferProcessRepository;
import it.eng.datatransfer.repository.TransferRequestMessageRepository;
import it.eng.tools.client.rest.OkHttpRestClient;
import it.eng.tools.service.AuditEventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@Profile("!tck")
public class DataTransferService extends AbstractDataTransferService {

    public DataTransferService(TransferProcessRepository transferProcessRepository,
                               TransferRequestMessageRepository transferRequestMessageRepository,
                               AuditEventPublisher publisher,
                               OkHttpRestClient okHttpRestClient) {
        super(transferProcessRepository, publisher, okHttpRestClient, transferRequestMessageRepository);
    }

    /**
     * If TransferProcess for given consumerPid and providerPid exists and state is STARTED.<br>
     * Note: those 2 Pid's are not to be mixed with Contract Negotiation ones. They are unique
     *
     * @param consumerPid consumerPid to search by
     * @param providerPid providerPid to search by
     * @return true if there is transferProcess with state STARTED for consumerPid and providerPid
     */
    public boolean isDataTransferStarted(String consumerPid, String providerPid) {
        return findByConsumerPidAndProviderPid(consumerPid, providerPid).getState().equals(TransferState.STARTED);
    }

    @Override
    public TransferProcess requestTransfer(TCKRequest tckRequest) {
        throw new UnsupportedOperationException("Not supported!");
    }

}
