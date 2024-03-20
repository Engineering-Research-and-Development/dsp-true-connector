package it.eng.catalog.model;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.annotation.JsonValue;

public enum Action {
	DELETE("odrl:delete"),
	EXECUTE("odrl:execute"),
	SOURCE_CODE("cc:SourceCode"),
	ANONYMIZE("odrl:anonymize"),
	EXTRACT("odrl:extract"),
	READ("odrl:read"),
	INDEX("odrl:index"),
	COMPENSATE("odrl:compensate"),
	SELL("odrl:sell"),
	DERIVE("odrl:derive"),
	ENSURE_EXCLUSIVITY("odrl:ensureExclusivity"),
	ANNOTATE("odrl:annotate"),
	REPRODUCTION("cc:Reproduction"),
	TRANSLATE("odrl:translate"),
	INCLUDE("odrl:include"),
	DERIVATIVE_WORKS("cc:DerivativeWorks"),
	DISTRIBUTION("cc:Distribution"),
	TEXT_TO_SPREACH("odrl:textToSpeech"),
	INFORM("odrl:inform"),
	GRANT_USE("odrl:grantUse"),
	ARCHIVE("odrl:archive"),
	MODIFY("odrl:modify"),
	AGGREGATE("odrl:aggregate"),
	ATTRIBUTE("odrl:attribute"),
	NEXT_POLICY("odrl:nextPolicy"),
	DIGITALIZE("odrl:digitize"),
	ATTRIBUTION("cc:Attribution"),
	INSTALL("odrl:install"),
	CONCURRENT_USE("odrl:concurrentUse"),
	DISTRIBUTE("odrl:distribute"),
	SYNCHRONIZE("odrl:synchronize"),
	MOVE("odrl:move"),
	OBTAIN_CONSENT("odrl:obtainConsent"),
	PRINT("odrl:print"),
	NOTICE("cc:Notice"),
	GIVE("odrl:give"),
	UNINSTALL("odrl:uninstall"),
	CC_SHARING("cc:Sharing"),
	REVIEW_POLICY("odrl:reviewPolicy"),
	WATERMARK("odrl:watermark"),
	PLAY("odrl:play"),
	REPRODUCE("odrl:reproduce"),
	TRANSFORM("odrl:transform"),
	DISPLAY("odrl:display"),
	STREAM("odrl:stream"),
	SHARE_ALIKE_CC("cc:ShareAlike"),
	ACCEPT_TRACKING("odrl:acceptTracking"),
	COMMERICAL_USE_CC("cc:CommericalUse"),
	PRESENT("odrl:present"),
	USE("odrl:use");
	
    static {
        Map<String,Action> map = new ConcurrentHashMap<String, Action>();
        for (Action instance : Action.values()) {
            map.put(instance.toString().toLowerCase(),instance);
        }
        ENUM_MAP = Collections.unmodifiableMap(map);
    }

	private final String action;
	
	private static final Map<String,Action> ENUM_MAP;

	Action(final String action) {
	        this.action = action;
	    }

	/**
	 * Returns the enum whose value is the same as the string
	 * Eg.  input:"odrl:use" = output:Action.USE
	 * @param name
	 * @return
	 */
	public static Action getEnum(String name) {
		return ENUM_MAP.get(name.toLowerCase());
	}
	
	@Override
	@JsonValue
    public String toString() {
        return action;
    }
	
}
