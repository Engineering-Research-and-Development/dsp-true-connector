package it.eng.negotiation.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


public enum Operator {

    EQ("EQ"),
    GT("GT"),
    GTEQ("GTEQ"),
    HAS_PARENT("HAS_PARENT"),
    IS_A("IS_A"),
    IS_ALL_OF("IS_ALL_OF"),
    IS_ANY_OF("IS_ANY_OF"),
    IS_NONE_OF("IS_NONE_OF"),
    IS_PART_OF("IS_PART_OF"),
    LT("LT"),
    LTEQ("LTEQ"),
    NEQ("NEQ");

    private final String operator;
    private static final Map<String, Operator> BY_LABEL;

    static {
        Map<String, Operator> map = new ConcurrentHashMap<>();
        for (Operator instance : Operator.values()) {
            map.put(instance.toString(), instance);
            map.put(instance.name(), instance);
        }
        BY_LABEL = Collections.unmodifiableMap(map);
    }

    public static Operator fromOperator(String operator) {
        return BY_LABEL.get(operator);
    }

    Operator(final String operator) {
        this.operator = operator;
    }

    @Override
    @JsonValue
    public String toString() {
        return operator;
    }

    @JsonCreator
    public static Operator fromString(String string) {
        Operator operator = BY_LABEL.get(string);
        if (operator == null) {
            throw new IllegalArgumentException(string + " has no corresponding value");
        }
        return operator;
    }
}
