package it.eng.tools.auth.keycloak.realm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import it.eng.tools.auth.keycloak.KeycloakProperties;
import java.io.IOException;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

class RealmCredentialsValidatorTest {

    private final KeycloakProperties props = new KeycloakProperties("http://kc:8080", "platform", "dsp-client",
            "aud", "platform-secret", new KeycloakProperties.UserAdmin(false));

    @SuppressWarnings("unchecked")
    private RealmCredentialsValidator validator(KeycloakProperties properties, OkHttpClient client) {
        ObjectProvider<KeycloakProperties> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(properties);
        return new RealmCredentialsValidator(provider, client);
    }

    private Response response(Request request, int code) {
        return new Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(code).message("m")
                .body(ResponseBody.create("{}", null)).build();
    }

    @Test
    @DisplayName("Valid secret is accepted and the realm token endpoint is called with the client id")
    void validSecret() throws IOException {
        OkHttpClient client = mock(OkHttpClient.class);
        Call call = mock(Call.class);
        ArgumentCaptor<Request> captor = ArgumentCaptor.forClass(Request.class);
        when(client.newCall(captor.capture())).thenReturn(call);
        when(call.execute()).thenAnswer(inv -> response(captor.getValue(), 200));

        assertTrue(validator(props, client).isValid("tenant-realm", "s3cret"));
        assertEquals("http://kc:8080/realms/tenant-realm/protocol/openid-connect/token",
                captor.getValue().url().toString());
    }

    @Test
    @DisplayName("Rejected secret and IO failures are both reported as invalid")
    void invalidSecret() throws IOException {
        OkHttpClient client = mock(OkHttpClient.class);
        Call call = mock(Call.class);
        ArgumentCaptor<Request> captor = ArgumentCaptor.forClass(Request.class);
        when(client.newCall(captor.capture())).thenReturn(call);
        when(call.execute()).thenAnswer(inv -> response(captor.getValue(), 401)).thenThrow(new IOException("down"));

        RealmCredentialsValidator validator = validator(props, client);
        assertFalse(validator.isValid("r", "bad"));
        assertFalse(validator.isValid("r", "bad"));
    }

    @Test
    @DisplayName("Validation is unavailable when no Keycloak properties exist")
    void unavailableWithoutProperties() {
        RealmCredentialsValidator validator = validator(null, mock(OkHttpClient.class));
        assertFalse(validator.isAvailable());
        assertFalse(validator.isValid("r", "s"));
    }
}
