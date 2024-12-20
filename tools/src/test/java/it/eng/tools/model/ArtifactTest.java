package it.eng.tools.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import it.eng.tools.util.ToolsMockObjectUtil;

public class ArtifactTest {
	
	
	@Test
	@DisplayName("Plain serialize/deserialize")
	public void equalsTestPlain() {
		String ss = Serializer.serializePlain(ToolsMockObjectUtil.ARTIFACT_FILE);
		Artifact obj = Serializer.deserializePlain(ss, Artifact.class);
		assertEquals(ToolsMockObjectUtil.ARTIFACT_FILE.getArtifactType(), obj.getArtifactType());
		assertEquals(ToolsMockObjectUtil.ARTIFACT_FILE.getContentType(), obj.getContentType());
		assertEquals(ToolsMockObjectUtil.ARTIFACT_FILE.getValue(), obj.getValue());
	}

}
