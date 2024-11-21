package it.eng.datatransfer.model;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

import it.eng.tools.model.DSpaceConstants;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@AllArgsConstructor
public class DataTransferRequest implements Serializable {

	private static final long serialVersionUID = 4451699406021801277L;

	@NotNull
	@JsonProperty("transferProcessId")
	private String transferProcessId;
	@JsonProperty(DSpaceConstants.FORMAT)
	private String format;
	@JsonProperty(DSpaceConstants.DATA_ADDRESS)
	private JsonNode dataAddress;
}
