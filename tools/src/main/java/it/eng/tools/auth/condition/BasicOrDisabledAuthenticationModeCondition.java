package it.eng.tools.auth.condition;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

import it.eng.tools.auth.AuthenticationMode;
import it.eng.tools.auth.AuthenticationModeResolver;

/**
 * Matches when the application is running in Basic or Disabled authentication mode.
 * Used to conditionally load MongoDB-based user management which is not available in Keycloak mode.
 */
public class BasicOrDisabledAuthenticationModeCondition implements Condition {

    /**
     * Evaluates whether the current environment resolves to Basic or Disabled mode.
     *
     * @param context the condition context
     * @param metadata the annotated type metadata
     * @return {@code true} when Basic or Disabled mode is active
     */
    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        AuthenticationMode mode = AuthenticationModeResolver.resolve(context.getEnvironment());
        return mode == AuthenticationMode.BASIC || mode == AuthenticationMode.DISABLED;
    }
}
