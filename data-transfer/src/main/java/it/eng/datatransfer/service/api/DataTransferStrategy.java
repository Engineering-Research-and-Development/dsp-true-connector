package it.eng.datatransfer.service.api;

import it.eng.datatransfer.model.TransferProcess;

public interface DataTransferStrategy {
    void transfer(TransferProcess transferProcess);
}
