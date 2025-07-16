package it.eng.tools.event;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public enum AuditEventType {

    APPLICATION_START("Application start"),
    APPLICATION_STOP("Application stop"),
    APPLICATION_LOGIN("Login"),
    APPLICATION_LOGOUT("Logout"),
    PROTOCOL_NEGOTIATION_CONTRACT_NEGOTIATION("Contract negotiation"),
    PROTOCOL_NEGOTIATION_NOT_FOUND("Contract negotiation not found"),
    PROTOCOL_NEGOTIATION_STATE_TRANSITION_ERROR("State transition invalid"),
    PROTOCOL_NEGOTIATION_REQUESTED("Protocol negotiation requested"),
    PROTOCOL_NEGOTIATION_ACCEPTED("Protocol negotiation accepted"),
    PROTOCOL_NEGOTIATION_AGREED("Protocol negotiation agreed"),
    PROTOCOL_NEGOTIATION_VERIFIED("Protocol negotiation verified"),
    PROTOCOL_NEGOTIATION_FINALIZED("Protocol negotiation finalized"),
    PROTOCOL_NEGOTIATION_TERMINATED("Protocol negotiation terminated"),
    PROTOCOL_NEGOTIATION_REJECTED("Protocol negotiation rejected"),
    PROTOCOL_NEGOTIATION_POLICY_EVALUATION_DISABLED("Policy evaluation disabled"),
    PROTOCOL_NEGOTIATION_POLICY_EVALUATION_APPROVE("Policy evaluation approved"),
    PROTOCOL_NEGOTIATION_POLICY_EVALUATION_DENIED("Policy evaluation denied"),
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

    NEGOTIATION_ACCESS_COUNT_INCREASE("Access count increase");

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

    public static AuditEventType fromaAuditEventType(String auditEventType) {
        return BY_LABEL.get(auditEventType);
    }

    @Override
    @JsonValue
    public String toString() {
        return auditEventType;
    }
}
