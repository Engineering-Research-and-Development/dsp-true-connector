package it.eng.catalog.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import it.eng.tools.model.DSpaceConstants;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * {
  "@context": "https://w3id.org/dspace/2024/1/context.json",
  "@type": "dspace:CatalogRequestMessage",
  "dspace:filter": [
    "some-filter"
  ]
}
 *
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@JsonDeserialize(builder = CatalogRequestMessage.Builder.class)
public class CatalogRequestMessage extends AbstractCatalogMessage {
	
//	private String callbackAddress;
	
	@JsonProperty(DSpaceConstants.DSPACE_FILTER)
	private List<String> filter;

	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class Builder {
		private final CatalogRequestMessage message;

		private Builder() {
			message = new CatalogRequestMessage();
		}

		@JsonCreator
		public static CatalogRequestMessage.Builder newInstance() {
			return new CatalogRequestMessage.Builder();
		}
		
//		public Builder callbackAddress(String callbackAddress) {
//			message.callbackAddress = callbackAddress;
//			return this;
//		}
		@JsonProperty(DSpaceConstants.DSPACE_FILTER)
		public Builder filter(List<String> filter) {
			message.filter = filter;
			return this;
		}
		
		public CatalogRequestMessage build() {
			return message;
		}
	}

	@Override
	public String getType() {
		return DSpaceConstants.DSPACE + CatalogRequestMessage.class.getSimpleName();
	}
}
