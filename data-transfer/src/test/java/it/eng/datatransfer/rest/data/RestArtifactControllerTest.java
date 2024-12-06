package it.eng.datatransfer.rest.data;

import java.nio.charset.Charset;

import org.apache.tomcat.util.codec.binary.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

public class RestArtifactControllerTest {

	public static final String CONSUMER_PID = "urn:uuid:CONSUMER_PID_TRANSFER";
	public static final String PROVIDER_PID = "urn:uuid:PROVIDER_PID_TRANSFER";
	
	@Test
	public void decodeString() {
		String encoded = Base64.encodeBase64URLSafeString((CONSUMER_PID + "|" + PROVIDER_PID).getBytes(Charset.forName("UTF-8")));
		String url = "/artifact/" + encoded + "/1";
		System.out.println(url);
		//["" , artifact, encoded, 1]
		String[] urlTokens = url.split("/");
		
		String[] tokens = new String(Base64.decodeBase64URLSafe(urlTokens[2]), Charset.forName("UTF-8")).split("\\|");
		System.out.println(tokens[0]);
		System.out.println(tokens[1]);
	}
	
//	proveri za download ako pukne da li je ov prazno
//	response.setStatus(HttpStatus.OK.value());
//	response.setHeader("Content-Disposition", "attachment;filename=\"" + attachment.getFilename() + "\"");
//	response.addHeader("Content-type", attachment.getContentType());
}
