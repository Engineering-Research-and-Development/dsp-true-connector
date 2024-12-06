package it.eng.tools.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import it.eng.tools.model.Artifact;
import it.eng.tools.model.Serializer;
import it.eng.tools.util.MockObjectUtil;

public class ArtifactTest {
	
	
	@Test
	@DisplayName("Plain serialize/deserialize")
	public void equalsTestPlain() {
		String ss = Serializer.serializePlain(MockObjectUtil.ARTIFACT_FILE);
		Artifact obj = Serializer.deserializePlain(ss, Artifact.class);
		assertEquals(MockObjectUtil.ARTIFACT_FILE.getArtifactType(), obj.getArtifactType());
		assertEquals(MockObjectUtil.ARTIFACT_FILE.getContentType(), obj.getContentType());
		assertEquals(MockObjectUtil.ARTIFACT_FILE.getValue(), obj.getValue());
	}

}
