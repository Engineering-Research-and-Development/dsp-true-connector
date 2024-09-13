package it.eng.datatransfer.util;

import java.time.Instant;

import it.eng.datatransfer.model.DataTransferFormat;
import it.eng.datatransfer.model.TransferCompletionMessage;
import it.eng.datatransfer.model.TransferProcess;
import it.eng.datatransfer.model.TransferRequestMessage;
import it.eng.datatransfer.model.TransferStartMessage;
import it.eng.datatransfer.model.TransferState;
import it.eng.datatransfer.model.TransferSuspensionMessage;
import it.eng.datatransfer.model.TransferTerminationMessage;

public class MockObjectUtil {

	public static final String CONSUMER_PID = "urn:uuid:CONSUMER_PID";
	public static final String PROVIDER_PID = "urn:uuid:PROVIDER_PID";
    public static final String RIGHT_EXPRESSION = "EU";
    public static final String USE = "use";
    public static final String INCLUDED_IN = "includedInAction";
    public static final String ASSIGNEE = "assignee";
    public static final String ASSIGNER = "assigner";
    public static final String AGREEMENT_ID = "urn:uuid:AGREEMENT_ID";
    public static final String TARGET = "target";
    public static final String CONFORMSTO = "conformsToSomething";
    public static final String CREATOR = "Chuck Norris";
    public static final String IDENTIFIER = "Unique identifier for tests";
    public static final Instant ISSUED = Instant.parse("2024-04-23T16:26:00Z");
    public static final Instant MODIFIED = Instant.parse("2024-04-23T16:26:00Z");
    public static final String TITLE = "Title for test";
    public static final String ENDPOINT_URL = "https://provider-a.com/connector";
    public static final String CALLBACK_ADDRESS = "https://example.com/callback";

    public static TransferProcess TRANSFER_PROCESS_REQUESTED = TransferProcess.Builder.newInstance()
    		.consumerPid(CONSUMER_PID)
    		.providerPid(PROVIDER_PID)
    		.state(TransferState.REQUESTED)
    		.build();

    public static TransferProcess TRANSFER_PROCESS_STARTED = TransferProcess.Builder.newInstance()
    		.consumerPid(CONSUMER_PID)
    		.providerPid(PROVIDER_PID)
    		.state(TransferState.STARTED)
    		.build();
    
    public static TransferProcess TRANSFER_PROCESS_COMPLETED = TransferProcess.Builder.newInstance()
    		.consumerPid(CONSUMER_PID)
    		.providerPid(PROVIDER_PID)
    		.state(TransferState.COMPLETED)
    		.build();
    
    public static TransferProcess TRANSFER_PROCESS_SUSPENDED = TransferProcess.Builder.newInstance()
    		.consumerPid(CONSUMER_PID)
    		.providerPid(PROVIDER_PID)
    		.state(TransferState.SUSPENDED)
    		.build();
    
    public static final TransferProcess TRANSFER_PROCESS_TERMINATED = TransferProcess.Builder.newInstance()
    		.consumerPid(CONSUMER_PID)
    		.providerPid(PROVIDER_PID)
    		.state(TransferState.TERMINATED)
    		.build();
    
    public static TransferRequestMessage TRANSFER_REQUEST_MESSAGE = TransferRequestMessage.Builder.newInstance()
    		.consumerPid(CONSUMER_PID)
    		.agreementId(AGREEMENT_ID)
    		.format(DataTransferFormat.HTTP_PULL.name())
    		.callbackAddress(CALLBACK_ADDRESS)
    		.build();
    
    public static TransferRequestMessage TRANSFER_REQUEST_MESSAGE_SFTP = TransferRequestMessage.Builder.newInstance()
    		.consumerPid(CONSUMER_PID)
    		.agreementId(AGREEMENT_ID)
    		.format(DataTransferFormat.SFTP.name())
    		.callbackAddress(CALLBACK_ADDRESS)
    		.build();
    
    public static TransferStartMessage TRANSFER_START_MESSAGE = TransferStartMessage.Builder.newInstance()
    		.consumerPid(CONSUMER_PID)
    		.providerPid(PROVIDER_PID)
    		.build();
    
    public static TransferCompletionMessage TRANSFER_COMPLETION_MESSAGE = TransferCompletionMessage.Builder.newInstance()
    		.consumerPid(CONSUMER_PID)
    		.providerPid(PROVIDER_PID)
    		.build();
    		
    public static TransferTerminationMessage TRANSFER_TERMINATION_MESSAGE = TransferTerminationMessage.Builder.newInstance()
    		.consumerPid(CONSUMER_PID)
    		.providerPid(PROVIDER_PID)
    		.code("123")
    		.build();
    
    public static TransferSuspensionMessage TRANSFER_SUSPENSION_MESSAGE = TransferSuspensionMessage.Builder.newInstance()
    		.consumerPid(CONSUMER_PID)
    		.providerPid(PROVIDER_PID)
    		.code("123")
    		.build();
    
}
