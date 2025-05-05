package it.eng.tools.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import it.eng.tools.serializer.ToolsSerializer;
import it.eng.tools.util.ToolsMockObjectUtil;

public class ArtifactTest {
	
	@Test
	@DisplayName("Plain serialize/deserialize")
	public void equalsTestPlain() {
		String ss = ToolsSerializer.serializePlain(ToolsMockObjectUtil.ARTIFACT_FILE);
		Artifact obj = ToolsSerializer.deserializePlain(ss, Artifact.class);
		assertEquals(obj, ToolsMockObjectUtil.ARTIFACT_FILE);
	}

}
