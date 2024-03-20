package it.eng.negotiation.model;

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
	
	private final String operand;

	LeftOperand(final String operand) {
	        this.operand = operand;
	    }

	@Override
	@JsonValue
    public String toString() {
        return operand;
    }
}
