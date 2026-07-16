package it.eng.tools.auth.condition;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

import it.eng.tools.auth.AuthenticationMode;
import it.eng.tools.auth.AuthenticationModeResolver;

/**
 * Matches when the application is running in Internal or Disabled authentication mode.
 * Used to conditionally load MongoDB-based user management which is not available in Keycloak mode.
 */
public class InternalOrDisabledAuthenticationModeCondition implements Condition {

    /**
     * Evaluates whether the current environment resolves to Internal or Disabled mode.
     *
     * @param context the condition context
     * @param metadata the annotated type metadata
     * @return {@code true} when Internal or Disabled mode is active
     */
    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        AuthenticationMode mode = AuthenticationModeResolver.resolve(context.getEnvironment());
        return mode == AuthenticationMode.INTERNAL || mode == AuthenticationMode.DISABLED;
    }
}
