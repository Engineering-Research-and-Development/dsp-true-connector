package it.eng.datatransfer.model;

public enum DataTransferFormat {

    HTTP_PULL("HttpData-PULL"),
    SFTP("SFTP");

    private final String format;

    DataTransferFormat(String format) {
        this.format = format;
    }

    public String format() {
        return format;
    }

    public static DataTransferFormat fromString(String value) {
        for (DataTransferFormat type : DataTransferFormat.values()) {
            if (type.format.equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown endpoint type: " + value);
    }
}
