package it.eng.dcp.model;

import lombok.Getter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Getter
public class ValidationReport {

    private final List<ValidationError> errors = new ArrayList<>();
    private final Set<String> acceptedCredentialTypes = new HashSet<>();

    public void addError(ValidationError err) {
        if (err != null) errors.add(err);
    }

    public boolean isValid() {
        return errors.stream().noneMatch(e -> e.severity() == ValidationError.Severity.ERROR);
    }

    public void addAccepted(String credentialType) {
        if (credentialType != null) acceptedCredentialTypes.add(credentialType);
    }

}

