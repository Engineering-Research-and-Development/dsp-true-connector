package it.eng.catalog.model;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.annotation.JsonValue;

public enum LeftOperand {

	ABSOLUTE_POSITION("odrl:absolutePosition"),
	ABSOLUTE_SIZE("odrl:absoluteSize"),
	ABSOLUTE_SPATIAL_POSITION("odrl:absoluteSpatialPosition"),
	ABSOLUTE_TEMPORAL_POSITION("odrl:absoluteTemporalPosition"),
    COUNT("odrl:count"),
    DATE_TIME("odrl:dateTime"),
    DELAY_PERIOD("odrl:delayPeriod"),
    DELIVERY_CHANNEL("odrl:deliveryChannel"),
    DEVICE("odrl:device"),
    ELAPSED_TIME("odrl:elapsedTime"),
    EVENT("odrl:event"),
    FILE_FOMRAT("odrl:fileFormat"),
    INDUSTRY("odrl:industry"),
    LANGUAGE("odrl:language"),
    MEDIA("odrl:media"),
    METERED_TIME("odrl:meteredTime"),
    PAY_AMOUNT("odrl:payAmount"),
    PERCENTAGE("odrl:percentage"),
    PRODUCT("odrl:product"),
    PURPOSE("odrl:purpose"),
    RECIPIENT("odrl:recipient"),
    RELATIVE_POSITION("odrl:relativePosition"),
    RELATIVE_SIZE("odrl:relativeSize"),
    RELATIVE_SPATIAL_POSITION("odrl:relativeSpatialPosition"),
    RELATIVE_TEMPORAL_POSITION("odrl:relativeTemporalPosition"),
    RESOLUTION("odrl:resolution"),
    SPATIAL("odrl:spatial"),
    SPATIAL_COORDINATES("odrl:spatialCoordinates"),
    SYSTEM("odrl:system"),
    SYSTEM_DEVICE("odrl:systemDevice"),
    TIME_INTERVAL("odrl:timeInterval"),
    UNIT_OF_COUNT("odrl:unitOfCount"),
    VERSION("odrl:version"),
	VIRTUAL_LOCATION("odrl:virtualLocation");
	
	static {
        Map<String,LeftOperand> map = new ConcurrentHashMap<String, LeftOperand>();
        for (LeftOperand instance : LeftOperand.values()) {
            map.put(instance.toString().toLowerCase(),instance);
        }
        ENUM_MAP = Collections.unmodifiableMap(map);
    }
	
	private final String operand;
	
	private static final Map<String,LeftOperand> ENUM_MAP;

	LeftOperand(final String operand) {
	        this.operand = operand;
	    }
	
    /**
     * Returns the enum whose value is the same as the string
     * Eg.  input:"odrl:use" = output:Action.USE
     * @param name
     * @return
     */
    public static LeftOperand getEnum(String name) {
        return ENUM_MAP.get(name.toLowerCase());
    }

	@Override
	@JsonValue
    public String toString() {
        return operand;
    }
}
