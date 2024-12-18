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
			/*
			 * Removed due to following test failing, reason still unknown:
			 * Error: DatasetTest.equalsTestProtocol:97 Expecting actual:
			 * it.eng.catalog.model.Dataset@4cecc15a to be equal to:
			 * it.eng.catalog.model.Dataset@574ffd63 when recursively comparing field by
			 * field, but found the following difference:
			 * 
			 * field/property 'artifact' differ: - actual value :
			 * "urn:uuid:4a8b93c9-1b0e-46a6-a78d-3fa7ace4404f" - expected value: null
			 * 
			 * The recursive comparison was performed with this configuration: - no
			 * overridden equals methods were used in the comparison (except for java types)
			 * - these types were compared with the following comparators: -
			 * java.lang.Double -> DoubleComparator[precision=1.0E-15] - java.lang.Float ->
			 * FloatComparator[precision=1.0E-6] - java.nio.file.Path -> lexicographic
			 * comparator (Path natural order) - actual and expected objects and their
			 * fields were compared field by field recursively even if they were not of the
			 * same type, this allows for example to compare a Person to a PersonDto (call
			 * strictTypeChecking(true) to change that behavior). - the introspection
			 * strategy used was: DefaultRecursiveComparisonIntrospectionStrategy
			 */
//			.id("urn:uuid:" + UUID.randomUUID())
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
			.artifactType(ArtifactType.EXTERNAL)
			.createdBy(CREATOR)
			.created(NOW)
			.lastModifiedDate(NOW)
			.lastModifiedBy(CREATOR)
			.value("https://example.com/employees")
			.version(0L)
			.build();
	
}
