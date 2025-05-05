package it.eng.tools.model;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.http.HttpHeaders;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import jakarta.validation.constraints.NotNull;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@JsonDeserialize(builder = Artifact.Builder.class)
@NoArgsConstructor
@EqualsAndHashCode
@Document(collection = "artifacts")
public class Artifact implements Serializable{

	private static final long serialVersionUID = -250707162174100566L;
	
	@Id
	private String id;
    @NotNull
	@JsonProperty(IConstants.ARTIFACT_TYPE)
	private ArtifactType artifactType;
    private String filename;
    /**
     * The value depends on artifact type as following.
     * file - fileId in database
     * external - URL of the data
     */
	private String value;
	private String authorization;
	@JsonProperty(HttpHeaders.CONTENT_TYPE)
	private String contentType;
	@CreatedDate
	private Instant created;
	@LastModifiedDate
	private Instant lastModifiedDate;
    @CreatedBy
    private String createdBy;
    @LastModifiedBy
    private String lastModifiedBy;
    @Version
    @Field("version")
    private Long version;
    
    @JsonPOJOBuilder(withPrefix = "")
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class Builder {
		
		private Artifact artifact;
		
		private Builder() {
			artifact = new Artifact();
		}
		
		public static Builder newInstance() {
			return new Builder();
		}
		
		public Builder id(String id) {
        	artifact.id = id;
        	return this;
        }
		
		@JsonProperty(IConstants.ARTIFACT_TYPE)
		public Builder artifactType(ArtifactType artifactType) {
        	artifact.artifactType = artifactType;
        	return this;
        }
		
		public Builder value(String value) {
        	artifact.value = value;
        	return this;
        }
		
		public Builder authorization(String authorization) {
        	artifact.authorization = authorization;
        	return this;
        }
		
		@JsonProperty(HttpHeaders.CONTENT_TYPE)
		public Builder contentType(String contentType) {
        	artifact.contentType = contentType;
        	return this;
        }
		
		public Builder created(Instant created) {
        	artifact.created = created;
        	return this;
        }
		
		public Builder lastModifiedDate(Instant lastModifiedDate) {
        	artifact.lastModifiedDate = lastModifiedDate;
        	return this;
        }
		
		public Builder filename(String filename) {
        	artifact.filename = filename;
        	return this;
        }
		
		@JsonProperty("createdBy")
		public Builder createdBy(String createdBy) {
			artifact.createdBy = createdBy;
			return this;
		}

		@JsonProperty("lastModifiedBy")
		public Builder lastModifiedBy(String lastModifiedBy) {
			artifact.lastModifiedBy = lastModifiedBy;
			return this;
		}

		@JsonProperty("version")
		public Builder version(Long version) {
			artifact.version = version;
			return this;
		}
		
		public Artifact build() {
			if (artifact.id == null) {
				artifact.id = "urn:uuid:" + UUID.randomUUID();
			}
			return artifact;
		}
	}	
}
