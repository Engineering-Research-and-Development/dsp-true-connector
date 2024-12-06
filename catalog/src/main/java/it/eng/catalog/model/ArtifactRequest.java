package it.eng.catalog.model;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonProperty;

import it.eng.tools.model.ArtifactType;
import it.eng.tools.model.IConstants;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@AllArgsConstructor
public class ArtifactRequest implements Serializable{
	
	private static final long serialVersionUID = -5597273148259411039L;
	@NotNull
	@JsonProperty(IConstants.ARTIFACT_TYPE)
	private ArtifactType artifactType;
	@JsonProperty(IConstants.VALUE)
	private String value;

}
