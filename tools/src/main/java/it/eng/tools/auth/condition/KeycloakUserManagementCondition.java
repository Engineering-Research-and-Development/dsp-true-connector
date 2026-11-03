package it.eng.tools.auth.condition;

import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Matches when Keycloak authentication mode is active and the Keycloak user API is switched on with
 * {@code application.keycloak.user-management} (default {@code true}).
 */
public class KeycloakUserManagementCondition extends KeycloakAuthenticationModeCondition {

    /**
     * Name of the property that switches the Keycloak user API on or off.
     */
    public static final String PROPERTY = "application.keycloak.user-management";

    /**
     * Evaluates whether Keycloak mode is active and user management is not switched off.
     *
     * @param context the condition context
     * @param metadata the annotated type metadata
     * @return {@code true} when the Keycloak user API should be created
     */
    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return super.matches(context, metadata)
                && context.getEnvironment().getProperty(PROPERTY, Boolean.class, Boolean.TRUE);
    }
}
