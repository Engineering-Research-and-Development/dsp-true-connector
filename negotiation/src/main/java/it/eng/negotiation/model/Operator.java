package it.eng.negotiation.model;

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

	Operator(final String operator) {
	        this.operator = operator;
	    }

	@Override
	@JsonValue
    public String toString() {
        return operator;
    }
}
