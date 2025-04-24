package it.eng.tools.s3.provision;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SecretToken {

	private String accessKey;
	private String accessSecret;
}
