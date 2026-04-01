package it.eng.tools.auth.condition;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

import it.eng.tools.auth.AuthenticationMode;
import it.eng.tools.auth.AuthenticationModeResolver;

/**
 * Matches when the legacy non-Keycloak security mode is active.
 */
public class LegacyAuthenticationModeCondition implements Condition {

    /**
     * Evaluates whether the current environment resolves to the legacy mode.
     *
     * @param context the condition context
     * @param metadata the annotated type metadata
     * @return {@code true} when the legacy mode is active
     */
    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return AuthenticationModeResolver.resolve(context.getEnvironment()) == AuthenticationMode.LEGACY;
    }
}
