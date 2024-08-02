package it.eng.datatransfer.model;

public enum DataTransferFormat {

	HTTP_PULL("HTTP_PULL"), 
	SFTP("SFTP");
	
	private final String format;
	
	DataTransferFormat(String format) {
		this.format = format;
	}
}
