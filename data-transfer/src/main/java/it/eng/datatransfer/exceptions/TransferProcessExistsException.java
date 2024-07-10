package it.eng.datatransfer.exceptions;

public class TransferProcessExistsException extends RuntimeException {

	private static final long serialVersionUID = -3292458375700970326L;
	private String consumerPid;

	public TransferProcessExistsException( ) {
		super();
	}
	 public TransferProcessExistsException(String message, String consumerPid) {
		 super(message);
		 this.consumerPid = consumerPid;
	 }
	public String getConsumerPid() {
		return consumerPid;
	}
	 
}
