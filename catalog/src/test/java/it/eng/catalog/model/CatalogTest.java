package it.eng.catalog.model;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import it.eng.tools.model.DSpaceConstants;

public class CatalogTest {

	private String dataServiceId = UUID.randomUUID().toString();
	private DataService dataService = DataService.Builder.newInstance()
			.id(dataServiceId)
			.endpointURL(CatalogUtil.ENDPOINT_URL)
			.endpointDescription("endpoint description")
			.build();
	
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
//			.assignee(CatalogUtil.ASSIGNEE)
//			.assigner(CatalogUtil.ASSIGNER)
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
	
	Distribution distribution = Distribution.Builder.newInstance()
		.dataService(null)
		.format(Reference.Builder.newInstance().id("dspace:s3+push").build())
		.dataService(Arrays.asList(dataService))
		.build();
	
	private Catalog catalog = Catalog.Builder.newInstance()
			.conformsTo(CatalogUtil.CONFORMSTO)
			.creator(CatalogUtil.CREATOR)
			.description(Arrays.asList(Multilanguage.Builder.newInstance().language("en").value("Catalog test").build(),
					Multilanguage.Builder.newInstance().language("it").value("Catalog test but in Italian").build()))
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
			.participantId("urn:example:DataProviderA")
			.service(Arrays.asList(dataService))
			.dataset(Arrays.asList(dataset))
			.distribution(Arrays.asList(distribution))
			.build();
	
	
	@Test
	public void testPlain() {
		String result = Serializer.serializePlain(catalog);
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
		
		Catalog javaObj = Serializer.deserializePlain(result, Catalog.class);
		validateDataset(javaObj.getDataset().get(0));
	}
	
	@Test
	public void testProtocol() {
		String result = Serializer.serializeProtocol(catalog);
		System.out.println(result);
		assertTrue(result.contains(DSpaceConstants.CONTEXT));
		assertTrue(result.contains(DSpaceConstants.TYPE));
		assertTrue(result.contains(DSpaceConstants.DCAT_KEYWORD));
		assertTrue(result.contains(DSpaceConstants.DCAT_THEME));
		assertTrue(result.contains(DSpaceConstants.DCT_CONFORMSTO));
		
		assertTrue(result.contains(DSpaceConstants.DCT_CREATOR));
		assertTrue(result.contains(DSpaceConstants.DCT_DESCRIPTION));
		assertTrue(result.contains(DSpaceConstants.DCT_IDENTIFIER));
		assertTrue(result.contains(DSpaceConstants.DCT_ISSUED));
		assertTrue(result.contains(DSpaceConstants.DCT_MODIFIED));
		assertTrue(result.contains(DSpaceConstants.DCT_MODIFIED));
		assertTrue(result.contains(DSpaceConstants.DCAT_DISTRIBUTION));
		
		Catalog javaObj = Serializer.deserializeProtocol(result, Catalog.class);
		validateDataset(javaObj.getDataset().get(0));
	}
	
	@Test
	@DisplayName("no required fields")
	public void validateInvalid() {
		assertDoesNotThrow(() -> Catalog.Builder.newInstance()
					.build());
	}
	
	public void validateDataset(Dataset javaObj) {
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
