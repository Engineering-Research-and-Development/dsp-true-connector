package it.eng.tools.auth.keycloak;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class KeycloakPropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(KeycloakConfiguration.class);

    private ApplicationContextRunner keycloakMode() {
        return runner.withPropertyValues("application.auth.provider=KEYCLOAK");
    }

    private ApplicationContextRunner fullConfig() {
        return keycloakMode().withPropertyValues(
                "application.keycloak.base-url=http://unreachable.invalid:1",
                "application.keycloak.platform-realm=platform",
                "application.keycloak.client-id=dsp-client",
                "application.keycloak.audience=dsp-audience",
                "application.keycloak.platform-client-secret=top-secret");
    }

    @Test
    @DisplayName("Valid configuration binds all fields and tolerates an unreachable Keycloak")
    void validConfigBinds() {
        fullConfig().run(ctx -> {
            assertTrue(ctx.isRunning());
            KeycloakProperties props = ctx.getBean(KeycloakProperties.class);
            assertEquals("http://unreachable.invalid:1", props.baseUrl());
            assertEquals("platform", props.platformRealm());
            assertEquals("dsp-client", props.clientId());
            assertEquals("dsp-audience", props.audience());
            assertEquals("top-secret", props.platformClientSecret());
            assertFalse(props.userAdmin().enabled());
            assertEquals("http://unreachable.invalid:1/realms/platform/protocol/openid-connect/token",
                    props.tokenUrl("platform"));
        });
    }

    @Test
    @DisplayName("toString never exposes the platform client secret")
    void toStringOmitsSecret() {
        fullConfig().run(ctx -> assertFalse(ctx.getBean(KeycloakProperties.class).toString().contains("top-secret")));
    }

    @Test
    @DisplayName("Startup fails naming each missing required property in KEYCLOAK mode")
    void missingPropertyFailsStartup() {
        String[] required = {"base-url", "platform-realm", "client-id", "audience", "platform-client-secret"};
        for (String missing : required) {
            String[] values = java.util.Arrays.stream(required)
                    .filter(name -> !name.equals(missing))
                    .map(name -> "application.keycloak." + name + "=x")
                    .toArray(String[]::new);
            keycloakMode().withPropertyValues(values).run(ctx -> {
                assertTrue(ctx.getStartupFailure() != null, "expected failure without " + missing);
                assertTrue(rootMessages(ctx.getStartupFailure()).contains("application.keycloak." + missing),
                        "message should name " + missing);
            });
        }
    }

    @Test
    @DisplayName("No bean and no validation outside KEYCLOAK mode")
    void noBeanInInternalMode() {
        runner.withPropertyValues("application.auth.provider=INTERNAL").run(ctx -> {
            assertTrue(ctx.isRunning());
            assertTrue(ctx.getBeansOfType(KeycloakProperties.class).isEmpty());
        });
    }

    private static String rootMessages(Throwable failure) {
        StringBuilder sb = new StringBuilder();
        for (Throwable t = failure; t != null; t = t.getCause()) {
            sb.append(t.getMessage()).append('\n');
        }
        return sb.toString();
    }
}
