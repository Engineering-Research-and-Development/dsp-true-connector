package it.eng.negotiation.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


public enum Action {
    DELETE("delete"),
    EXECUTE("execute"),
    SOURCE_CODE("SourceCode"),
    ANONYMIZE("anonymize"),
    EXTRACT("extract"),
    READ("read"),
    INDEX("index"),
    COMPENSATE("compensate"),
    SELL("sell"),
    DERIVE("derive"),
    ENSURE_EXCLUSIVITY("ensureExclusivity"),
    ANNOTATE("annotate"),
    REPRODUCTION("Reproduction"),
    TRANSLATE("translate"),
    INCLUDE("include"),
    DERIVATIVE_WORKS("DerivativeWorks"),
    DISTRIBUTION("Distribution"),
    TEXT_TO_SPEECH("textToSpeech"),
    INFORM("inform"),
    GRANT_USE("grantUse"),
    ARCHIVE("archive"),
    MODIFY("modify"),
    AGGREGATE("aggregate"),
    ATTRIBUTE("attribute"),
    NEXT_POLICY("nextPolicy"),
    DIGITALIZE("digitize"),
    ATTRIBUTION("Attribution"),
    INSTALL("install"),
    CONCURRENT_USE("concurrentUse"),
    DISTRIBUTE("distribute"),
    SYNCHRONIZE("synchronize"),
    MOVE("move"),
    OBTAIN_CONSENT("obtainConsent"),
    PRINT("print"),
    NOTICE("Notice"),
    GIVE("give"),
    UNINSTALL("uninstall"),
    CC_SHARING("Sharing"),
    REVIEW_POLICY("reviewPolicy"),
    WATERMARK("watermark"),
    PLAY("play"),
    REPRODUCE("reproduce"),
    TRANSFORM("transform"),
    DISPLAY("display"),
    STREAM("stream"),
    SHARE_ALIKE_CC("ShareAlike"),
    ACCEPT_TRACKING("acceptTracking"),
    COMMERCIAL_USE_CC("CommercialUse"),
    PRESENT("present"),
    USE("use");

    private final String action;
    private static final Map<String, Action> BY_LABEL;

    static {
        Map<String, Action> map = new ConcurrentHashMap<>();
        for (Action instance : Action.values()) {
            map.put(instance.toString(), instance);
            map.put(instance.name(), instance);
        }
        BY_LABEL = Collections.unmodifiableMap(map);
    }

    public static Action fromAction(String action) {
        return BY_LABEL.get(action);
    }

    Action(final String action) {
        this.action = action;
    }

    @Override
    @JsonValue
    public String toString() {
        return action;
    }

    @JsonCreator
    public static Action fromString(String string) {
        Action action = BY_LABEL.get(string);
        if (action == null) {
            throw new IllegalArgumentException(string + " has no corresponding value");
        }
        return action;
    }
}
