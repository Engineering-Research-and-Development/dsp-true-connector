package it.eng.dcp.model;

public record ValidationError(String code, String message, ValidationError.Severity severity) {

    public enum Severity {INFO, WARNING, ERROR}

    // Backwards-compatible getters used by existing tests
    public String getCode() { return code; }
    public String getMessage() { return message; }
    public Severity getSeverity() { return severity; }
}
