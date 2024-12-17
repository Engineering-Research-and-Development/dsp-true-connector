package it.eng.tools.util;

import java.time.Instant;
import java.util.UUID;

import org.bson.types.ObjectId;
import org.springframework.http.MediaType;

import it.eng.tools.model.ApplicationProperty;
import it.eng.tools.model.Artifact;
import it.eng.tools.model.ArtifactType;

public class MockObjectUtil {

	public static final String CREATOR = "admin@mail.com";
	public static final Instant NOW = Instant.now();

	public static final ApplicationProperty PROPERTY = ApplicationProperty.Builder.newInstance()
			//.createdBy(CREATOR)
			//.issued(NOW)
			.key("Sample key")
			//.lastModifiedBy(CREATOR)
			.mandatory(false)
			//.modified(NOW)
			.sampleValue("Sample samplevalue")
			.value("Sample value")
			//.version(0L)
			.build();
	
	public static final ApplicationProperty APPLICATION_PROPERTY_FOR_UPDATE = ApplicationProperty.Builder.newInstance()
			.createdBy(CREATOR)
			.issued(NOW)
			.key("Sample key")
			.lastModifiedBy(CREATOR)
			.mandatory(false)
			.modified(NOW)
			.sampleValue("Sample samplevalue")
			.value("Sample value")
			.version(0L)
			.build();
	
	public static final ApplicationProperty OLD_APPLICATION_PROPERTY_FOR_UPDATE = ApplicationProperty.Builder.newInstance()
			.createdBy(CREATOR)
			.issued(NOW)
			.key("Sample key")
			.lastModifiedBy(CREATOR)
			.mandatory(false)
			.modified(NOW)
			.sampleValue("Sample samplevalue")
			.value("Old sample value")
			.version(0L)
			.build();
	
	public static final Artifact ARTIFACT_FILE = Artifact.Builder.newInstance()
//			.id("urn:uuid:" + UUID.randomUUID())
			.id("pera file")
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
//			.id("urn:uuid:" + UUID.randomUUID())
			.id("pera externals")
			.artifactType(ArtifactType.EXTERNAL)
			.createdBy(CREATOR)
			.created(NOW)
			.lastModifiedDate(NOW)
			.lastModifiedBy(CREATOR)
			.value("https://example.com/employees")
			.version(0L)
			.build();
	
}
