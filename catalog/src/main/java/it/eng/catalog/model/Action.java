package it.eng.catalog.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import it.eng.tools.model.DSpaceConstants;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public enum Action {
    DELETE("delete"),
    EXECUTE("execute"),
    SOURCE_CODE("cc:SourceCode"),
    ANONYMIZE("anonymize"),
    EXTRACT("extract"),
    READ("read"),
    INDEX("index"),
    COMPENSATE("compensate"),
    SELL("sell"),
    DERIVE("derive"),
    ENSURE_EXCLUSIVITY("ensureExclusivity"),
    ANNOTATE("annotate"),
    REPRODUCTION("cc:Reproduction"),
    TRANSLATE("translate"),
    INCLUDE("include"),
    DERIVATIVE_WORKS("cc:DerivativeWorks"),
    DISTRIBUTION("cc:Distribution"),
    TEXT_TO_SPREACH("textToSpeech"),
    INFORM("inform"),
    GRANT_USE("grantUse"),
    ARCHIVE("archive"),
    MODIFY("modify"),
    AGGREGATE("aggregate"),
    ATTRIBUTE("attribute"),
    NEXT_POLICY("nextPolicy"),
    DIGITALIZE("digitize"),
    ATTRIBUTION("cc:Attribution"),
    INSTALL(DSpaceConstants.ODRL + "install"),
    CONCURRENT_USE(DSpaceConstants.ODRL + "concurrentUse"),
    DISTRIBUTE(DSpaceConstants.ODRL + "distribute"),
    SYNCHRONIZE(DSpaceConstants.ODRL + "synchronize"),
    MOVE(DSpaceConstants.ODRL + "move"),
    OBTAIN_CONSENT(DSpaceConstants.ODRL + "obtainConsent"),
    PRINT(DSpaceConstants.ODRL + "print"),
    NOTICE("cc:Notice"),
    GIVE(DSpaceConstants.ODRL + "give"),
    UNINSTALL(DSpaceConstants.ODRL + "uninstall"),
    CC_SHARING("cc:Sharing"),
    REVIEW_POLICY(DSpaceConstants.ODRL + "reviewPolicy"),
    WATERMARK(DSpaceConstants.ODRL + "watermark"),
    PLAY(DSpaceConstants.ODRL + "play"),
    REPRODUCE(DSpaceConstants.ODRL + "reproduce"),
    TRANSFORM(DSpaceConstants.ODRL + "transform"),
    DISPLAY(DSpaceConstants.ODRL + "display"),
    STREAM(DSpaceConstants.ODRL + "stream"),
    SHARE_ALIKE_CC("cc:ShareAlike"),
    ACCEPT_TRACKING(DSpaceConstants.ODRL + "acceptTracking"),
    COMMERCIAL_USE_CC("cc:CommercialUse"),
    PRESENT(DSpaceConstants.ODRL + "present"),
    USE("use");

    private final String action;
    private static final Map<String, Action> BY_LABEL;

    static {
        Map<String, Action> map = new ConcurrentHashMap<String, Action>();
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
