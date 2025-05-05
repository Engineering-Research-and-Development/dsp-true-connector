package it.eng.datatransfer.exceptions;

import org.springframework.http.HttpStatus;

public class DownloadException extends RuntimeException {

	private static final long serialVersionUID = -8645532222117927114L;

	private HttpStatus httpStatus;

	public DownloadException(String message, HttpStatus httpStatus) {
		super(message);
		this.httpStatus = httpStatus;
	}
	
	public HttpStatus getHttpStatus() {
		return httpStatus;
	}
	
}
