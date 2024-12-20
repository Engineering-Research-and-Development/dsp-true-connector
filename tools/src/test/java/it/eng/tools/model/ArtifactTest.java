package it.eng.tools.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.UUID;

import org.bson.types.ObjectId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

public class ArtifactTest {
	
	 public static final String CREATOR = "Chuck_Norris";
	 public static final Instant NOW = Instant.now();
	 
	 public static final Artifact ARTIFACT_FILE = Artifact.Builder.newInstance()
				.id("urn:uuid:" + UUID.randomUUID())
				.artifactType(ArtifactType.FILE)
				.contentType(MediaType.APPLICATION_JSON.getType())
				.createdBy(CREATOR)
				.created(NOW)
				.lastModifiedDate(NOW)
				.filename("Employees.txt")
				.lastModifiedBy(CREATOR)
				.value(new ObjectId().toHexString())
				.version(0L)
				.build();
		
		public static final Artifact ARTIFACT_EXTERNAL = Artifact.Builder.newInstance()
				.id("urn:uuid:" + UUID.randomUUID())
				.artifactType(ArtifactType.EXTERNAL)
				.createdBy(CREATOR)
				.created(NOW)
				.lastModifiedDate(NOW)
				.lastModifiedBy(CREATOR)
				.value("https://example.com/employees")
				.version(0L)
				.build();
		
	
	@Test
	@DisplayName("Plain serialize/deserialize")
	public void equalsTestPlain() {
		String ss = Serializer.serializePlain(ARTIFACT_FILE);
		Artifact obj = Serializer.deserializePlain(ss, Artifact.class);
		assertEquals(ARTIFACT_FILE.getArtifactType(), obj.getArtifactType());
		assertEquals(ARTIFACT_FILE.getContentType(), obj.getContentType());
		assertEquals(ARTIFACT_FILE.getValue(), obj.getValue());
	}

}
