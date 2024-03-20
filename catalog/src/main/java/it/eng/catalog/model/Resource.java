package it.eng.catalog.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import it.eng.tools.model.DSpaceConstants;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@JsonDeserialize(builder = Resource.Builder.class)
@JsonPropertyOrder(value = {"@context", "@type", "@id"}, alphabetic =  true)
public class Resource {

	 @JsonProperty(DSpaceConstants.DCAT_KEYWORD)
	 private List<String> keyword;
     @JsonProperty(DSpaceConstants.DCAT_THEME)
     private List<String> theme;
     @JsonProperty(DSpaceConstants.DCT_CONFORMSTO)
     private String conformsTo;
     @JsonProperty(DSpaceConstants.DCT_CREATOR)
     private String creator;
     @JsonProperty(DSpaceConstants.DCT_DESCRIPTION)
     private List<Multilanguage> description;
     @JsonProperty(DSpaceConstants.DCT_IDENTIFIER)
     private String identifier;
     @JsonProperty(DSpaceConstants.DCT_ISSUED) 
     private String issued;
     @JsonProperty(DSpaceConstants.DCT_MODIFIED) 
     private String modified;
     @JsonProperty(DSpaceConstants.DCT_TITLE)  
     private String title;
     
    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
 	public static class Builder {
     
    	private final Resource resource;

		private Builder() {
			resource = new Resource();
		}

		@JsonCreator
		public static Builder newInstance() {
			return new Builder();
		}
		@JsonProperty(DSpaceConstants.DCAT_KEYWORD)
		public Builder keyword(List<String> keyword) {
			resource.keyword = keyword;
			return this;
		}
		@JsonProperty(DSpaceConstants.DCAT_THEME)
		public Builder theme(List<String> theme) {
			resource.theme = theme;
			return this;
		}
		
		@JsonProperty(DSpaceConstants.DCT_CONFORMSTO)
		public Builder conformsTo(String conformsTo) {
			resource.conformsTo = conformsTo;
			return this;
		}
	
		@JsonProperty(DSpaceConstants.DCT_CREATOR)
		public Builder creator( String creator) {
			resource.creator = creator;
			return this;
		}
		
		@JsonProperty(DSpaceConstants.DCT_DESCRIPTION)
		public Builder description(List<Multilanguage> description) {
			resource.description = description;
			return this;
		}
		
		@JsonProperty(DSpaceConstants.DCT_IDENTIFIER)
		public Builder identifier(String identifier) {
			resource.identifier = identifier;
			return this;
		}
		
		@JsonProperty(DSpaceConstants.DCT_ISSUED)
		public Builder issued(String issued) {
			resource.issued = issued;
			return this;
		}
		
		@JsonProperty(DSpaceConstants.DCT_MODIFIED)
		public Builder modified(  String modified) {
			resource.modified = modified;
			return this;
		}
		
		@JsonProperty(DSpaceConstants.DCT_TITLE)
		public Builder title( String title) {
			resource.title = title;
			return this;
		}
		
		public Resource build() {
			return resource;
		}
    }
}
