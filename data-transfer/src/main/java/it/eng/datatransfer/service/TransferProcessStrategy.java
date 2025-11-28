package it.eng.datatransfer.service;

import it.eng.datatransfer.model.*;

public interface TransferProcessStrategy {
    TransferProcess findTransferProcess(String consumerPid, String providerPid);

    TransferProcess findTransferProcessByProviderPid(String providerPid);

    TransferProcess findTransferProcessByConsumerPid(String consumerPid);

    TransferProcess initiateDataTransfer(TransferRequestMessage transferRequestMessage);

    TransferProcess startDataTransfer(TransferStartMessage transferStartMessage, String consumerPid, String providerPid);

    TransferProcess completeDataTransfer(TransferCompletionMessage transferCompletionMessage, String consumerPid, String providerPid);

    TransferProcess terminateDataTransfer(TransferTerminationMessage transferTerminationMessage, String consumerPid, String providerPid);

    TransferProcess suspendDataTransfer(TransferSuspensionMessage transferSuspensionMessage, String consumerPid, String providerPid);

    boolean isDataTransferStarted(String consumerPid, String providerPid);

    TransferProcess requestTransfer(TCKRequest tckRequest);
}

