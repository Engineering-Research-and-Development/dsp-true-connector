package it.eng.tools.client.rest;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

@Service
@Slf4j
public class OkHttpRestClient {

	private OkHttpClient okHttpClient;
	
	public OkHttpRestClient(@Qualifier("okHttpClient") OkHttpClient okHttpClient) {
		this.okHttpClient = okHttpClient;
	}
	
	public Response executeCall(Request request) {
		try {
			return okHttpClient.newCall(request).execute();
		} catch (IOException e) {
			log.error("Error while executing rest call", e);
			//TODO add error handler for REST calls
		}
		return null;
	}
}
