package it.eng.catalog.model;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.annotation.JsonValue;

import it.eng.tools.model.DSpaceConstants;

/**
 * The set of supported expression operators. Not all operators may be supported for particular expression types.
 */
public enum Operator {

    /**
     * Indicating that a given value equals the right operand of the Constraint.
     */
    EQ(DSpaceConstants.ODRL + "eq"),

    /**
     * Indicating that a given value is not equal to the right operand of the Constraint.
     */
    NEQ(DSpaceConstants.ODRL + "neq"),

    /**
     * Indicating that a given value is greater than the right operand of the Constraint.
     */
    GT(DSpaceConstants.ODRL + "gt"),

    /**
     * Indicating that a given value is greater than or equal to the right operand of the Constraint.
     */
    GEQ(DSpaceConstants.ODRL + "gteq"),

    /**
     * Indicating that a given value is less than the right operand of the Constraint.
     */
    LT(DSpaceConstants.ODRL + "lt"),

    /**
     * Indicating that a given value is less than or equal to the right operand of the Constraint.
     */
    LEQ(DSpaceConstants.ODRL + "lteq"),

    /**
     * A set-based operator indicating that a given value is contained by the right operand of the Constraint.
     */
    IN(DSpaceConstants.ODRL + "isPartOf"),

    /**
     * A set-based operator indicating that a given value contains the right operand of the Constraint.
     */
    HAS_PART(DSpaceConstants.ODRL + "hasPart"),

    /**
     * A set-based operator indicating that a given value is contained by the right operand of the Constraint.
     */
    IS_A(DSpaceConstants.ODRL + "isA"),

    /**
     * A set-based operator indicating that a given value is all of the right operand of the Constraint.
     */
    IS_ALL_OF(DSpaceConstants.ODRL + "isAllOf"),

    /**
     * A set-based operator indicating that a given value is any of the right operand of the Constraint.
     */
    IS_ANY_OF(DSpaceConstants.ODRL + "isAnyOf"),

    /**
     * A set-based operator indicating that a given value is none of the right operand of the Constraint.
     */
    IS_NONE_OF(DSpaceConstants.ODRL + "isNoneOf");
	
	static {
        Map<String,Operator> map = new ConcurrentHashMap<String, Operator>();
        for (Operator instance : Operator.values()) {
            map.put(instance.toString().toLowerCase(),instance);
        }
        ENUM_MAP = Collections.unmodifiableMap(map);
    }

    private final String operator;
    
	private static final Map<String,Operator> ENUM_MAP;

    Operator(String odrlRepresentation) {
        this.operator = odrlRepresentation;
    }
    
	/**
	 * Returns the enum whose value is the same as the string
	 * Eg.  input:"odrl:use" = output:Action.USE
	 * @param name
	 * @return
	 */
	public static Operator getEnum(String name) {
		return ENUM_MAP.get(name.toLowerCase());
	}

	@Override
	@JsonValue
    public String toString() {
        return operator;
    }

}
