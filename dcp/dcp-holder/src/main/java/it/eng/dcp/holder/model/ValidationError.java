package it.eng.dcp.holder.model;

public record ValidationError(String code, String message, ValidationError.Severity severity) {

    public enum Severity {INFO, WARNING, ERROR}

}
