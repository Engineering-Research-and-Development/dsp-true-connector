package it.eng.tools.auth.condition;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

import it.eng.tools.auth.AuthenticationMode;
import it.eng.tools.auth.AuthenticationModeResolver;

/**
 * Matches when Basic authentication mode is active.
 */
public class BasicAuthenticationModeCondition implements Condition {

    /**
     * Evaluates whether the current environment resolves to Basic mode.
     *
     * @param context the condition context
     * @param metadata the annotated type metadata
     * @return {@code true} when Basic mode is active
     */
    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return AuthenticationModeResolver.resolve(context.getEnvironment()) == AuthenticationMode.BASIC;
    }
}
