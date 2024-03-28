package it.eng.negotiation.model;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.annotation.JsonValue;

public enum Operator {
	
	EQ("odrl:eq"),
    GT("odrl:gt"),
    GTEQ("odrl:gteq"),
    HAS_PARENT("odrl:hasPart"),
    IS_A("odrl:isA"),
    IS_ALL_OF("odrl:isAllOf"),
    IS_ANY_OF("odrl:isAnyOf"),
    IS_NONE_OF("odrl:isNoneOf"),
    IS_PART_OF("odrl:isPartOf"),
    LT("odrl:lt"),
    TERM_LTEQ("odrl:term-lteq"),
    NEQ("odrl:neq");

	private final String operator;
	private static final Map<String, Operator> BY_LABEL;

	static {
		Map<String, Operator> map = new ConcurrentHashMap<String, Operator>();
		for (Operator instance : Operator.values()) {
			map.put(instance.toString().toLowerCase(), instance);
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
}
