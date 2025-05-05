package it.eng.catalog.model;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import it.eng.tools.model.DSpaceConstants;

public enum LeftOperand {

	// Uncomment ones that are supported by the underlying logic
	COUNT(DSpaceConstants.ODRL + "count"),
	DATE_TIME(DSpaceConstants.ODRL + "dateTime");
/*
	ABSOLUTE_POSITION(DSpaceConstants.ODRL + "absolutePosition"),
	ABSOLUTE_SIZE(DSpaceConstants.ODRL + "absoluteSize"),
	ABSOLUTE_SPATIAL_POSITION(DSpaceConstants.ODRL + "absoluteSpatialPosition"),
	ABSOLUTE_TEMPORAL_POSITION(DSpaceConstants.ODRL + "absoluteTemporalPosition"),
    DELAY_PERIOD(DSpaceConstants.ODRL + "delayPeriod"),
    DELIVERY_CHANNEL(DSpaceConstants.ODRL + "deliveryChannel"),
    DEVICE(DSpaceConstants.ODRL + "device"),
    ELAPSED_TIME(DSpaceConstants.ODRL + "elapsedTime"),
    EVENT(DSpaceConstants.ODRL + "event"),
    FILE_FOMRAT(DSpaceConstants.ODRL + "fileFormat"),
    INDUSTRY(DSpaceConstants.ODRL + "industry"),
    LANGUAGE(DSpaceConstants.ODRL + "language"),
    MEDIA(DSpaceConstants.ODRL + "media"),
    METERED_TIME(DSpaceConstants.ODRL + "meteredTime"),
    PAY_AMOUNT(DSpaceConstants.ODRL + "payAmount"),
    PERCENTAGE(DSpaceConstants.ODRL + "percentage"),
    PRODUCT(DSpaceConstants.ODRL + "product"),
    PURPOSE(DSpaceConstants.ODRL + "purpose"),
    RECIPIENT(DSpaceConstants.ODRL + "recipient"),
    RELATIVE_POSITION(DSpaceConstants.ODRL + "relativePosition"),
    RELATIVE_SIZE(DSpaceConstants.ODRL + "relativeSize"),
    RELATIVE_SPATIAL_POSITION(DSpaceConstants.ODRL + "relativeSpatialPosition"),
    RELATIVE_TEMPORAL_POSITION(DSpaceConstants.ODRL + "relativeTemporalPosition"),
    RESOLUTION(DSpaceConstants.ODRL + "resolution"),
    SPATIAL(DSpaceConstants.ODRL + "spatial"),
    SPATIAL_COORDINATES(DSpaceConstants.ODRL + "spatialCoordinates"),
    SYSTEM(DSpaceConstants.ODRL + "system"),
    SYSTEM_DEVICE(DSpaceConstants.ODRL + "systemDevice"),
    TIME_INTERVAL(DSpaceConstants.ODRL + "timeInterval"),
    UNIT_OF_COUNT(DSpaceConstants.ODRL + "unitOfCount"),
    VERSION(DSpaceConstants.ODRL + "version"),
	VIRTUAL_LOCATION(DSpaceConstants.ODRL + "virtualLocation");
	*/
	private final String operand;

	private static final Map<String,LeftOperand> BY_LABEL;
	static {
        Map<String,LeftOperand> map = new ConcurrentHashMap<String, LeftOperand>();
        for (LeftOperand instance : LeftOperand.values()) {
            map.put(instance.toString(), instance);
            map.put(instance.name(), instance);
            }
        BY_LABEL = Collections.unmodifiableMap(map);
    }
	
	public static LeftOperand fromLeftOperand(String leftOperand) {
		return BY_LABEL.get(leftOperand);
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
