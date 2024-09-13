package it.eng.datatransfer.model;

public class ModelUtil {

	public static final String CONSUMER_PID = "urn:uuid:CONSUMER_PID";
	public static final String PROVIDER_PID = "urn:uuid:PROVIDER_PID";
	public static final String CALLBACK_ADDRESS = "https://callback.address/callback";
	public static final String DATASET_ID = "urn:uuid:DATASET_ID";
	public static final String ASSIGNEE = "urn:uuid:ASSIGNEE";
	public static final String ASSIGNER = "urn:uuid:ASSIGNER";
	
	public static final String AGREEMENT_ID = "urn:uuid:AGREEMENT_ID";
	public static final String FORMAT = "example:HTTP_PUSH";
	public static final String ENDPOINT = "http://example.com";
	public static final String ENDPOINT_TYPE = "https://w3id.org/idsa/v4.1/HTTP";
	
	public static final String TARGET = "urn:uuid:TARGET";
	
	public static TransferProcess TRANSFER_PROCESS_REQUESTED = TransferProcess.Builder.newInstance()
			.agreementId(AGREEMENT_ID)
			.consumerPid(CONSUMER_PID)
			.providerPid(PROVIDER_PID)
			.state(TransferState.REQUESTED)
			.build();
	
	public static TransferProcess TRANSFER_PROCESS_STARTED = TransferProcess.Builder.newInstance()
			.agreementId(AGREEMENT_ID)
			.consumerPid(CONSUMER_PID)
			.providerPid(PROVIDER_PID)
			.state(TransferState.STARTED)
			.build();
	
	public static TransferProcess TRANSFER_PROCESS_SUSPENDED = TransferProcess.Builder.newInstance()
			.agreementId(AGREEMENT_ID)
			.consumerPid(CONSUMER_PID)
			.providerPid(PROVIDER_PID)
			.state(TransferState.SUSPENDED)
			.build();
}
