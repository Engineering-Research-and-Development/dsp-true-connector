package it.eng.tools.auth.condition;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

import it.eng.tools.auth.AuthenticationModeResolver;

/**
 * Matches when DCP (Decentralized Claims Protocol) authentication is enabled for protocol endpoints.
 */
public class DcpEnabledCondition implements Condition {

    /**
     * Evaluates whether DCP authentication is enabled via {@code application.auth.dcp.enabled=true}.
     *
     * @param context the condition context
     * @param metadata the annotated type metadata
     * @return {@code true} when DCP is enabled
     */
    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return AuthenticationModeResolver.isDcpEnabled(context.getEnvironment());
    }
}
