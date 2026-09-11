package it.eng.tools.auth.condition;

import it.eng.tools.auth.AuthenticationMode;
import it.eng.tools.auth.AuthenticationModeResolver;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Matches when the application is running in Internal or Keycloak authentication mode.
 * Used to conditionally load components that are compatible with both Internal and Keycloak modes.
 */
public class InternalOrKeycloakAuthenticationModeCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        AuthenticationMode mode = AuthenticationModeResolver.resolve(context.getEnvironment());
        return mode == AuthenticationMode.INTERNAL || mode == AuthenticationMode.KEYCLOAK;
    }
}
