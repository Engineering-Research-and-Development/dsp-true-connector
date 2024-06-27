package it.eng.datatransfer.rest.protocol;

public class DataTransferCallback {

	private static final String CONSUMER_DATA_TRANSFER_START= ":callback:/transfers/:consumerPid:/start";
	private static final String CONSUMER_DATA_TRANSFER_COMPLETION = ":callback:/transfers/:consumerPid:/completion";
	private static final String CONSUMER_DATA_TRANSFER_TERMINATION = ":callback:/transfers/:consumerPid:/termination";
	private static final String CONSUMER_DATA_TRANSFER_SUSPENSION = ":callback:/transfers/:consumerPid:/suspension";
	
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
	
	private static String getValidCallback(String callback) {
		return callback.endsWith("/") ? callback.substring(0, callback.length() - 1) : callback;
	} 
}
