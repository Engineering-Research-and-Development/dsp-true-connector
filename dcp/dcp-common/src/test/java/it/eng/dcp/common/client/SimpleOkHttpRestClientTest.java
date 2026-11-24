package it.eng.dcp.common.client;

import com.sun.net.httpserver.HttpServer;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SimpleOkHttpRestClientTest {

    @Test
    @DisplayName("Status requests do not follow redirects")
    void shouldNotFollowRedirects() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicInteger redirectedRequests = new AtomicInteger();
        server.createContext("/redirect", exchange -> {
            exchange.getResponseHeaders().add("Location", "/target");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.createContext("/target", exchange -> {
            redirectedRequests.incrementAndGet();
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();
        try {
            SimpleOkHttpRestClient client = new SimpleOkHttpRestClient(new OkHttpClient());
            Request request = new Request.Builder()
                    .url("http://127.0.0.1:" + server.getAddress().getPort() + "/redirect")
                    .build();
            try (Response response = client.executeCallWithoutRedirects(request)) {
                assertEquals(302, response.code());
            }
            assertEquals(0, redirectedRequests.get());
        } finally {
            server.stop(0);
        }
    }
}
