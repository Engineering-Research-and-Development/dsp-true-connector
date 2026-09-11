package it.eng.tools.auth.keycloak;

import it.eng.tools.auth.condition.KeycloakAuthenticationModeCondition;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "application.keycloak.login")
@Conditional(KeycloakAuthenticationModeCondition.class)
@Getter
@Setter
public class KeycloakLoginProperties {

    private String clientId;
    private String clientSecret;
    private String tokenUrl;
    private String logoutUrl;
}
