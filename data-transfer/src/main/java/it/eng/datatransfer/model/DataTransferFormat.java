package it.eng.datatransfer.model;

public enum DataTransferFormat {

	HTTP_PULL("HttpData-PULL"), 
	SFTP("SFTP");

	private final String format;

	DataTransferFormat(String format) {
		this.format = format;
	}

	public String format() {
		return format;
	}
}
