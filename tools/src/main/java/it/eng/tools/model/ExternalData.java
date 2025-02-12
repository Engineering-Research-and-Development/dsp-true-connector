package it.eng.tools.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import okhttp3.MediaType;

@Data
@NoArgsConstructor
public class ExternalData {
	
	private MediaType contentType;
	private byte[] data;
	private String contentDisposition;

}
