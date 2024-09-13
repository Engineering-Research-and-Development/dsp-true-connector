package it.eng.negotiation.model;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import it.eng.tools.model.DSpaceConstants;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@JsonDeserialize(builder = Description.Builder.class)
public class Description implements Serializable {

	private static final long serialVersionUID = 8696117607150478089L;

	/*
	 { "@language": "en", "@value": "The parsing of the input parameters failed." }
	 */
	
	@JsonProperty(DSpaceConstants.VALUE)
	private String value;
	
	@JsonProperty(DSpaceConstants.LANGUAGE)
	private String language;
	
	@JsonPOJOBuilder(withPrefix = "")
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class Builder {
		
		private Description message;
		
		private Builder() {
			message = new Description();
		}

		public static Builder newInstance() {
			return new Builder();
		}
		
		@JsonProperty(DSpaceConstants.VALUE)
		public Builder value(String value) {
			message.value = value;
			return this;
		}

		@JsonProperty(DSpaceConstants.LANGUAGE)
		public Builder language(String language) {
			message.language = language;
			return this;
		}
					
		public Description build() {
			return message;
		}
	}
}
