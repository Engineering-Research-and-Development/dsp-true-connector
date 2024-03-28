package it.eng.catalog.util;

import java.util.Arrays;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

import it.eng.catalog.model.Action;
import it.eng.catalog.model.Catalog;
import it.eng.catalog.model.Constraint;
import it.eng.catalog.model.DataService;
import it.eng.catalog.model.Dataset;
import it.eng.catalog.model.Distribution;
import it.eng.catalog.model.LeftOperand;
import it.eng.catalog.model.LiteralExpression;
import it.eng.catalog.model.Multilanguage;
import it.eng.catalog.model.Offer;
import it.eng.catalog.model.Operator;
import it.eng.catalog.model.Permission;
import it.eng.catalog.model.Reference;

public class MockObjectUtil {
	
	public static final LiteralExpression LEFT_EXPRESSION = new LiteralExpression("spatial");
	public static final String RIGHT_EXPRESSION = "EU";
	public static final String USE = "use";
	public static final String INCLUDED_IN = "includedInAction";
	public static final String ASSIGNEE = "assignee";
	public static final String ASSIGNER = "assigner";
	public static final String TARGET = "target";
	public static final String CONFORMSTO = "conformsToSomething";
	public static final String CREATOR = "Chuck Norris";
	public static final String IDENTIFIER = "Uniwue identifier for tests";
	public static final String ISSUED = "yesterday";
	public static final String MODIFIED = "today";
	public static final String TITLE = "Title for test";
	public static final String ENDPOINT_URL = "https://provider-a.com/connector";
	
	
	public static Catalog createCatalog() {
		
		DataService service = DataService.Builder.newInstance()
				.id(UUID.randomUUID().toString())
				.endpointURL("http://dataservice.com")
				.endpointDescription("endpoint description")
				.build();
		
		Distribution distribution = Distribution.Builder.newInstance()
				.dataService(Arrays.asList(service))
				.format(Reference.Builder.newInstance().id("pdf").build())
				.build();
		
		Permission permission = Permission.Builder.newInstance()
				.action(Action.USE)
				.constraint(Arrays.asList(createConstraint()))
				.build();
		
		Offer offer = Offer.Builder.newInstance()
				.assignee(ASSIGNEE)
				.assigner(ASSIGNER)
				.target(TARGET)
				.permission(Arrays.asList(permission))
				.build();
		
		Multilanguage multilanguage = Multilanguage.Builder.newInstance()
				.language("en")
				.value("For test")
				.build();
		
		Dataset dataset = Dataset.Builder.newInstance()
				.conformsTo(CONFORMSTO)
				.creator(CREATOR)
				.distribution(Arrays.asList(distribution))
				.description(Arrays.asList(multilanguage))
				.issued(ISSUED)
				.keyword(Arrays.asList("keyword1", "keyword2"))
				.modified(MODIFIED)
				.theme(Arrays.asList("white", "blue", "aqua"))
				.title(TITLE)
				.hasPolicy(Arrays.asList(offer))
				.build();
		
		 return Catalog.Builder.newInstance()
					.conformsTo(CONFORMSTO)
					.creator(CREATOR)
					.description(Arrays.asList(Multilanguage.Builder.newInstance().language("en").value("Catalog description").build()))
					.identifier(IDENTIFIER)
					.issued(ISSUED)
					.keyword(Arrays.asList("keyword1", "keyword2"))
					.modified(MODIFIED)
					.theme(Arrays.asList("white", "blue", "aqua"))
					.title(TITLE)
					.participantId("urn:example:DataProviderA")
					.service(Arrays.asList(service))
					.dataset(Arrays.asList(dataset))
					.distribution(Arrays.asList(distribution))
					.build();
	}


//	public static Constraint createConstraint(LiteralExpression leftExpression, LiteralExpression rightExpression) {
//		Constraint constraint = Constraint.Builder.newInstance()
//				.leftOperand(LeftOperand.ABSOLUTE_POSITION)
//				.rightOperand(RIGHT_EXPRESSION)
//				.operator(Operator.EQ)
//				.build();
//		return constraint;
//	}
	
	public static Constraint createConstraint() {
		Constraint constraint = Constraint.Builder.newInstance()
				.leftOperand(LeftOperand.ABSOLUTE_POSITION)
				.rightOperand(RIGHT_EXPRESSION)
				.operator(Operator.EQ)
				.build();
		return constraint;
	}
	

	public static void getAllKeysUsingJsonNodeFieldNames(JsonNode jsonNode, Set<String> keys) {
	    if (jsonNode.isObject()) {
	        Iterator<Entry<String, JsonNode>> fields = jsonNode.fields();
	        fields.forEachRemaining(field -> {
	        	String key = field.getKey();
	        	if(key.contains(":")) {
	        		keys.add(key.split(":")[0]);
	        		getAllKeysUsingJsonNodeFieldNames((JsonNode) field.getValue(), keys);
	        	}
	        });
	    } else if (jsonNode.isArray()) {
	        ArrayNode arrayField = (ArrayNode) jsonNode;
	        arrayField.forEach(node -> {
	            getAllKeysUsingJsonNodeFieldNames(node, keys);
	        });
	    }
	}
	
}
