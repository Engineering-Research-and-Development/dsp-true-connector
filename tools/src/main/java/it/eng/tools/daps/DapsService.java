package it.eng.tools.daps;

public interface DapsService {

	String fetchToken();
	
	boolean validateToken(String token);
}
