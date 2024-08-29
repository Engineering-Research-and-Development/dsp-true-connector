package it.eng.catalog.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import it.eng.tools.model.DSpaceConstants;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.ValidationException;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Set;
import java.util.stream.Collectors;

/*
 * {
  "@context":  "https://w3id.org/dspace/2024/1/context.json",
  "@type": "dspace:DatasetRequestMessage",
  "dspace:dataset": "urn:uuid:3dd1add8-4d2d-569e-d634-8394a8836a88"
}
 *
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@JsonDeserialize(builder = DatasetRequestMessage.Builder.class)
@JsonPropertyOrder(value = {"@context", "@type", "@id"}, alphabetic =  true)
public class DatasetRequestMessage extends AbstractCatalogObject {

	private static final long serialVersionUID = 8681130342830944384L;
	
	@NotNull
	@JsonProperty(DSpaceConstants.DSPACE_DATASET)
	private String dataset;
	
	@JsonPOJOBuilder(withPrefix = "")
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class Builder {
		private final DatasetRequestMessage datasetRequestMessage;

		private Builder() {
			datasetRequestMessage = new DatasetRequestMessage();
		}
		
		@JsonCreator
		public static DatasetRequestMessage.Builder newInstance() {
			return new DatasetRequestMessage.Builder();
		}
		
		@JsonProperty(DSpaceConstants.DSPACE_DATASET)
		public Builder dataset(String dataset) {
			datasetRequestMessage.dataset = dataset;
			return this;
		}
		
		public DatasetRequestMessage build() {
			Set<ConstraintViolation<DatasetRequestMessage>> violations
				= Validation.buildDefaultValidatorFactory().getValidator().validate(datasetRequestMessage);
			if(violations.isEmpty()) {
				return datasetRequestMessage;
			}
			throw new ValidationException("DatasetRequestMessage - " +
					violations
						.stream()
						.map(v -> v.getPropertyPath() + " " + v.getMessage())
						.collect(Collectors.joining(",")));
			}
	}
	
	@Override
	public String getType() {
		return DSpaceConstants.DSPACE + DatasetRequestMessage.class.getSimpleName();
	}

}
