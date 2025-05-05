package it.eng.catalog.model;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import it.eng.tools.model.DSpaceConstants;
import it.eng.tools.model.DSpaceConstants.Operators;

/**
 * The set of supported expression operators. Not all operators may be supported for particular expression types.
 */
public enum Operator {

	EQ(DSpaceConstants.ODRL + Operators.EQ),
    GT(DSpaceConstants.ODRL + Operators.GT),
    GTEQ(DSpaceConstants.ODRL + Operators.GTEQ),
    HAS_PARENT(DSpaceConstants.ODRL + Operators.HAS_PARENT),
    IS_A(DSpaceConstants.ODRL + Operators.IS_A),
    IS_ALL_OF(DSpaceConstants.ODRL + Operators.IS_ALL_OF),
    IS_ANY_OF(DSpaceConstants.ODRL + Operators.IS_ANY_OF),
    IS_NONE_OF(DSpaceConstants.ODRL + Operators.IS_NONE_OF),
    IS_PART_OF(DSpaceConstants.ODRL + Operators.IS_PART_OF),
    LT(DSpaceConstants.ODRL + Operators.LT),
    LTEQ(DSpaceConstants.ODRL + Operators.LTEQ),
    NEQ(DSpaceConstants.ODRL + Operators.NEQ);
	
	private final String operator;
	private static final Map<String, Operator> BY_LABEL;

	static {
		Map<String, Operator> map = new ConcurrentHashMap<String, Operator>();
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
