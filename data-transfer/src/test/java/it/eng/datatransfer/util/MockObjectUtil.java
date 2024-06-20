package it.eng.datatransfer.util;

import java.time.Instant;

import it.eng.datatransfer.model.TransferProcess;
import it.eng.datatransfer.model.TransferState;

public class MockObjectUtil {

	public static final String CONSUMER_PID = "urn:uuid:CONSUMER_PID";
	public static final String PROVIDER_PID = "urn:uuid:PROVIDER_PID";
    public static final String RIGHT_EXPRESSION = "EU";
    public static final String USE = "use";
    public static final String INCLUDED_IN = "includedInAction";
    public static final String ASSIGNEE = "assignee";
    public static final String ASSIGNER = "assigner";
    public static final String TARGET = "target";
    public static final String CONFORMSTO = "conformsToSomething";
    public static final String CREATOR = "Chuck Norris";
    public static final String IDENTIFIER = "Unique identifier for tests";
    public static final Instant ISSUED = Instant.parse("2024-04-23T16:26:00Z");
    public static final Instant MODIFIED = Instant.parse("2024-04-23T16:26:00Z");
    public static final String TITLE = "Title for test";
    public static final String ENDPOINT_URL = "https://provider-a.com/connector";

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
    
}
