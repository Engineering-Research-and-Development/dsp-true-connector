package it.eng.connector.calculator;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.springframework.stereotype.Service;

import lombok.extern.java.Log;

@Service
@Log
public class AsyncCalculatror {

	public Future<String> calculateAsync() throws InterruptedException {
	    CompletableFuture<String> completableFuture = new CompletableFuture<>();

	    Executors.newCachedThreadPool().submit(() -> {
	    	log.info("Thread sleep 1000ms");
	        Thread.sleep(1000);
	        completableFuture.complete("Hello world");
	        return null;
	    });

	    return completableFuture;
	}
}
