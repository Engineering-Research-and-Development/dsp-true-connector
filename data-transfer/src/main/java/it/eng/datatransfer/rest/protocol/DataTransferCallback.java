package it.eng.datatransfer.rest.protocol;

public class DataTransferCallback {

	private static final String CONSUMER_DATA_TRANSFER_REQUEST= "/transfers/request";
	private static final String CONSUMER_DATA_TRANSFER_START= ":callback:/transfers/:consumerPid:/start";
	private static final String CONSUMER_DATA_TRANSFER_COMPLETION = ":callback:/transfers/:consumerPid:/completion";
	private static final String CONSUMER_DATA_TRANSFER_TERMINATION = ":callback:/transfers/:consumerPid:/termination";
	private static final String CONSUMER_DATA_TRANSFER_SUSPENSION = ":callback:/transfers/:consumerPid:/suspension";
	private static final String PROVIDER_DATA_TRANSFER_START= "/transfers/:providerPid:/start";
	private static final String PROVIDER_DATA_TRANSFER_COMPLETION = "/transfers/:providerPid:/completion";
	private static final String PROVIDER_DATA_TRANSFER_TERMINATION = "/transfers/:providerPid:/termination";
	private static final String PROVIDER_DATA_TRANSFER_SUSPENSION = "/transfers/:providerPid:/suspension";
	
	public static String getConsumerDataTransferRequest(String callback) {
		return getValidCallback(callback) + CONSUMER_DATA_TRANSFER_REQUEST;
	}
	public static String getConsumerDataTransferStart(String callback, String consumerPid) {
		return CONSUMER_DATA_TRANSFER_START.replace(":callback:", getValidCallback(callback))
				.replace(":consumerPid:", consumerPid);
	}
	public static String getConsumerDataTransferCompletion(String callback, String consumerPid) {
		return CONSUMER_DATA_TRANSFER_COMPLETION.replace(":callback:", getValidCallback(callback))
				.replace(":consumerPid:", consumerPid);
	}
	public static String getConsumerDataTransferTermination(String callback, String consumerPid) {
		return CONSUMER_DATA_TRANSFER_TERMINATION.replace(":callback:", getValidCallback(callback))
				.replace(":consumerPid:", consumerPid);
	}
	public static String getConsumerDataTransferSuspension(String callback, String consumerPid) {
		return CONSUMER_DATA_TRANSFER_SUSPENSION.replace(":callback:", getValidCallback(callback))
				.replace(":consumerPid:", consumerPid);
	}
	
	public static String getProviderDataTransferStart(String callback, String providerPid) {
		return getValidCallback(callback) + PROVIDER_DATA_TRANSFER_START.replace(":providerPid:", providerPid);
	}
	public static String getProviderDataTransferCompletion(String callback, String providerPid) {
		return getValidCallback(callback) + PROVIDER_DATA_TRANSFER_COMPLETION.replace(":providerPid:", providerPid);
	}
	public static String getProviderDataTransferTermination(String callback, String providerPid) {
		return getValidCallback(callback) + PROVIDER_DATA_TRANSFER_TERMINATION.replace(":providerPid:", providerPid);
	}
	public static String getProviderDataTransferSuspension(String callback, String providerPid) {
		return getValidCallback(callback) + PROVIDER_DATA_TRANSFER_SUSPENSION.replace(":providerPid:", providerPid);
	}
	
	public static String getValidCallback(String callback) {
		return callback.endsWith("/") ? callback.substring(0, callback.length() - 1) : callback;
	} 
}
