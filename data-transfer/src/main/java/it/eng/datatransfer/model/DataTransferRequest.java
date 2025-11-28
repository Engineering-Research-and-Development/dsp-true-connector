package it.eng.datatransfer.model;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@AllArgsConstructor
public class DataTransferRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 4451699406021801277L;

    @NotNull
    private String transferProcessId;
    private String format;
    private JsonNode dataAddress;
}
