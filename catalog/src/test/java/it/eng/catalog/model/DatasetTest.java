package it.eng.catalog.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;

import it.eng.tools.model.DSpaceConstants;
import jakarta.validation.ValidationException;

public class DatasetTest {

	private Constraint constraint = Constraint.Builder.newInstance()
			.leftOperand(LeftOperand.COUNT)
			.operator(Operator.EQ)
			.rightOperand("5")
			.build();
	
	private Permission permission = Permission.Builder.newInstance()
			.action(Action.USE)
			.constraint(Arrays.asList(constraint))
			.build();
	
	private Offer offer = Offer.Builder.newInstance()
			.target(CatalogUtil.TARGET)
			.assignee(CatalogUtil.ASSIGNEE)
			.assigner(CatalogUtil.ASSIGNER)
			.permission(Arrays.asList(permission))
			.build();
	
	private Dataset dataset = Dataset.Builder.newInstance()
			.conformsTo(CatalogUtil.CONFORMSTO)
			.creator(CatalogUtil.CREATOR)
			.description(Arrays.asList(Multilanguage.Builder.newInstance().language("en").value("For test").build(),
					Multilanguage.Builder.newInstance().language("it").value("For test but in Italian").build()))
			.distribution(Arrays.asList(Distribution.Builder.newInstance()
					.dataService(null)
//					.description(Arrays.asList(Multilanguage.Builder.newInstance().language("en").value("DS descr for test").build()))
//					.issued(CatalogUtil.ISSUED)
//					.modified(CatalogUtil.MODIFIED)
//					.title("Distribution title for tests")
					.format(Reference.Builder.newInstance().id("dspace:s3+push").build())
					.build()))
			.identifier(CatalogUtil.IDENTIFIER)
			.issued(CatalogUtil.ISSUED)
			.keyword(Arrays.asList("keyword1", "keyword2"))
			.modified(CatalogUtil.MODIFIED)
			.theme(Arrays.asList("white", "blue", "aqua"))
			.title(CatalogUtil.TITLE)
			.hasPolicy(Arrays.asList(offer))
			.build();
	
	
	@Test
	@DisplayName("Verify valid plain object serialization")
	public void testPlain() {
		String result = Serializer.serializePlain(dataset);
		assertFalse(result.contains(DSpaceConstants.CONTEXT));
		assertFalse(result.contains(DSpaceConstants.TYPE));
		assertFalse(result.contains(DSpaceConstants.ID));
		assertTrue(result.contains(DSpaceConstants.KEYWORD));
		assertTrue(result.contains(DSpaceConstants.THEME));
		assertTrue(result.contains(DSpaceConstants.CONFORMSTO));
		
		assertTrue(result.contains(DSpaceConstants.CREATOR));
		assertTrue(result.contains(DSpaceConstants.DESCRIPTION));
		assertTrue(result.contains(DSpaceConstants.IDENTIFIER));
		assertTrue(result.contains(DSpaceConstants.ISSUED));
		assertTrue(result.contains(DSpaceConstants.MODIFIED));
		assertTrue(result.contains(DSpaceConstants.MODIFIED));
		assertTrue(result.contains(DSpaceConstants.DISTRIBUTION));
		
		Dataset javaObj = Serializer.deserializePlain(result, Dataset.class);
		validateDataset(javaObj);
	}

	@Test
	@DisplayName("Verify valid protocol object serialization")
	public void testProtocol() {
		JsonNode result = Serializer.serializeProtocolJsonNode(dataset);
		assertNotNull(result.get(DSpaceConstants.CONTEXT).asText());
		assertNotNull(result.get(DSpaceConstants.TYPE).asText());
		assertNotNull(result.get(DSpaceConstants.DCAT_KEYWORD).asText());
		assertNotNull(result.get(DSpaceConstants.DCAT_THEME).asText());
		assertNotNull(result.get(DSpaceConstants.DCT_CONFORMSTO).asText());
		
		assertNotNull(result.get(DSpaceConstants.DCT_CREATOR).asText());
		assertNotNull(result.get(DSpaceConstants.DCT_DESCRIPTION).asText());
		assertNotNull(result.get(DSpaceConstants.DCT_IDENTIFIER).asText());
		assertNotNull(result.get(DSpaceConstants.DCT_ISSUED).asText());
		assertNotNull(result.get(DSpaceConstants.DCT_MODIFIED).asText());
		assertNotNull(result.get(DSpaceConstants.DCT_MODIFIED).asText());
		assertNotNull(result.get(DSpaceConstants.DCAT_DISTRIBUTION).asText());
		
		Dataset javaObj = Serializer.deserializeProtocol(result, Dataset.class);
		validateDataset(javaObj);
	}

	@Test
	@DisplayName("Missing @ontext and @ype")
	public void missingContextAndType() {
		JsonNode result = Serializer.serializePlainJsonNode(dataset);
		assertThrows(ValidationException.class, () -> Serializer.deserializeProtocol(result, Dataset.class));
	}
	
	@Test
	@DisplayName("No required fields")
	public void validateInvalid() {
		assertThrows(ValidationException.class,
				() -> Dataset.Builder.newInstance()
					.build());
	}
	
	private void validateDataset(Dataset javaObj) {
		assertNotNull(javaObj);
		assertNotNull(javaObj.getConformsTo());
		assertNotNull(javaObj.getCreator());
		assertNotNull(javaObj.getDescription().get(0));
		assertNotNull(javaObj.getDistribution().get(0));
		assertNotNull(javaObj.getIdentifier());
		assertNotNull(javaObj.getIssued());
		assertNotNull(javaObj.getKeyword());
		assertEquals(2, javaObj.getKeyword().size());
		assertEquals(3, javaObj.getTheme().size());
		assertNotNull(javaObj.getModified());
		assertNotNull(javaObj.getTheme());
		assertNotNull(javaObj.getTitle());
	}
}
