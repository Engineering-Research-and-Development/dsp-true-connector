package it.eng.catalog.model;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import it.eng.tools.model.DSpaceConstants;

@JsonFormat(shape = JsonFormat.Shape.OBJECT)
public enum PolicyType {

	SET("set"),
	OFFER("offer"),
	CONTRACT("contract");

	@JsonProperty(DSpaceConstants.POLICY_TYPE)
	private String type;

	PolicyType(@JsonProperty(DSpaceConstants.POLICY_TYPE) String type) {
		this.type = type;
	}

	@JsonCreator
	public static PolicyType fromObject(Map<String, Object> object) {
		if (SET.type.equals(object.get(DSpaceConstants.POLICY_TYPE))) {
			return SET;
		} else if (OFFER.type.equals(object.get(DSpaceConstants.POLICY_TYPE))) {
			return OFFER;
		} else if (CONTRACT.type.equals(object.get(DSpaceConstants.POLICY_TYPE))) {
			return CONTRACT;
		}
		throw new IllegalArgumentException("Invalid policy type");
	}

	public String getType() {
		return type;
	}
}