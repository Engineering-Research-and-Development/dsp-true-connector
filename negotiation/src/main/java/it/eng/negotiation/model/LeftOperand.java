package it.eng.negotiation.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


public enum LeftOperand {

    // Uncomment ones that are supported by the underlying logic
    COUNT("count"),
    DATE_TIME("dateTime"),
    SPATIAL("spatial"),
    PURPOSE("purpose");

    private final String operand;

    private static final Map<String, LeftOperand> BY_LABEL;

    static {
        Map<String, LeftOperand> map = new ConcurrentHashMap<>();
        for (LeftOperand instance : LeftOperand.values()) {
            map.put(instance.toString(), instance);
            map.put(instance.name(), instance);
        }
        BY_LABEL = Collections.unmodifiableMap(map);
    }

    public static LeftOperand fromLeftOperand(String leftOperand) {
        return BY_LABEL.get(leftOperand.toLowerCase());
    }

    LeftOperand(final String operand) {
        this.operand = operand;
    }

    @Override
    @JsonValue
    public String toString() {
        return operand;
    }

    @JsonCreator
    public static LeftOperand fromString(String string) {
        LeftOperand leftOperand = BY_LABEL.get(string);
        if (leftOperand == null) {
            throw new IllegalArgumentException(string + " has no corresponding value");
        }
        return leftOperand;
    }
}
