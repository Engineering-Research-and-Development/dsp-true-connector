package it.eng.tools.event;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public enum AuditEventType {

    APPLICATION_START("Application start"),
    APPLICATION_STOP("Application stop"),
    APPLICATION_LOGIN("Login"),
    APPLICATION_LOGIN_FAILED("Login failed"),
    APPLICATION_LOGOUT("Logout"),
    APPLICATION_LOGOUT_FAILED("Logout failed"),
    APPLICATION_TOKEN_REFRESHED("Token refreshed"),
    APPLICATION_TOKEN_REFRESH_FAILED("Token refresh failed"),
    M2M_TOKEN_ISSUED("Machine-to-machine token issued"),
    M2M_TOKEN_ISSUE_FAILED("Machine-to-machine token issue failed"),
    PROTOCOL_CATALOG_CATALOG_NOT_FOUND("Catalog not found"),
    PROTOCOL_CATALOG_DATASET_NOT_FOUND("Dataset not found"),
    PROTOCOL_NEGOTIATION_CONTRACT_NEGOTIATION("Contract negotiation"),
    PROTOCOL_NEGOTIATION_NOT_FOUND("Contract negotiation not found"),
    PROTOCOL_NEGOTIATION_STATE_TRANSITION_ERROR("State transition invalid"),
    PROTOCOL_NEGOTIATION_REQUESTED("Protocol negotiation requested"),
    PROTOCOL_NEGOTIATION_OFFERED("Protocol negotiation offered"),
    PROTOCOL_NEGOTIATION_ACCEPTED("Protocol negotiation accepted"),
    PROTOCOL_NEGOTIATION_AGREED("Protocol negotiation agreed"),
    PROTOCOL_NEGOTIATION_VERIFIED("Protocol negotiation verified"),
    PROTOCOL_NEGOTIATION_FINALIZED("Protocol negotiation finalized"),
    PROTOCOL_NEGOTIATION_TERMINATED("Protocol negotiation terminated"),
    PROTOCOL_NEGOTIATION_REJECTED("Protocol negotiation rejected"),
    PROTOCOL_NEGOTIATION_POLICY_EVALUATION_DISABLED("Policy evaluation disabled"),
    PROTOCOL_NEGOTIATION_POLICY_EVALUATION_APPROVE("Policy evaluation approved"),
    PROTOCOL_NEGOTIATION_POLICY_EVALUATION_DENIED("Policy evaluation denied"),
    PROTOCOL_NEGOTIATION_INVALID_OFFER("Protocol negotiation offer not valid"),
    PROTOCOL_TRANSFER_NOT_FOUND("Transfer not found"),
    PROTOCOL_TRANSFER_STATE_TRANSITION_ERROR("State transition invalid"),
    PROTOCOL_TRANSFER_REQUESTED("Transfer requested"),
    PROTOCOL_TRANSFER_STARTED("Transfer started"),
    PROTOCOL_TRANSFER_COMPLETED("Transfer completed"),
    PROTOCOL_TRANSFER_SUSPENDED("Transfer suspended"),
    PROTOCOL_TRANSFER_TERMINATED("Transfer terminated"),
    TRANSFER_VIEW("Transfer completed"),
    TRANSFER_COMPLETED("Transfer completed"),
    TRANSFER_FAILED("Transfer failed"),

    NEGOTIATION_ACCESS_COUNT_INCREASE("Access count increase"),

    TENANT_CREATED("Tenant created"),
    TENANT_DELETED("Tenant deleted"),
    TENANT_ENABLED("Tenant enabled"),
    TENANT_DISABLED("Tenant disabled"),
    TENANT_UPDATED("Tenant updated"),
    TENANT_NOT_FOUND("Tenant not found"),

    DATAPLANE_REGISTERED("Data Plane registered"),
    DATAPLANE_REGISTRATION_UPDATED("Data Plane registration updated"),
    DATAPLANE_DEREGISTERED("Data Plane deregistered"),
    DATAPLANE_REGISTRATION_NOT_FOUND("Data Plane registration not found"),

    USER_CREATED("User created"),
    USER_UPDATED("User updated"),
    USER_PASSWORD_CHANGED("User password changed"),

    CATALOG_CREATED("Catalog created"),
    CATALOG_UPDATED("Catalog updated"),
    CATALOG_DELETED("Catalog deleted"),
    DATASET_CREATED("Dataset created"),
    DATASET_UPDATED("Dataset updated"),
    DATASET_DELETED("Dataset deleted"),
    ARTIFACT_UPLOADED("Artifact uploaded"),
    ARTIFACT_DELETED("Artifact deleted"),
    DATA_SERVICE_CREATED("Data service created"),
    DATA_SERVICE_UPDATED("Data service updated"),
    DATA_SERVICE_DELETED("Data service deleted"),
    DISTRIBUTION_CREATED("Distribution created"),
    DISTRIBUTION_UPDATED("Distribution updated"),
    DISTRIBUTION_DELETED("Distribution deleted");

    private final String auditEventType;
    private static final Map<String, AuditEventType> BY_LABEL;

    static {
        Map<String, AuditEventType> map = new ConcurrentHashMap<String, AuditEventType>();
        for (AuditEventType instance : AuditEventType.values()) {
            map.put(instance.toString(), instance);
            map.put(instance.name(), instance);
        }
        BY_LABEL = Collections.unmodifiableMap(map);
    }

    AuditEventType(String auditEventType) {
        this.auditEventType = auditEventType;
    }

    public static AuditEventType fromAuditEventType(String auditEventType) {
        return BY_LABEL.get(auditEventType);
    }

    @Override
    @JsonValue
    public String toString() {
        return auditEventType;
    }
}
