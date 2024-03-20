package it.eng.catalog.transformer.from;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

import it.eng.catalog.model.Constraint;
import it.eng.catalog.model.LeftOperand;
import it.eng.catalog.util.MockObjectUtil;
import it.eng.tools.model.DSpaceConstants;

public class JsonFromConstraintTransformerTest {

	private JsonFromConstraintTransformer transformer = new JsonFromConstraintTransformer();
	
	@Test
	public void transformToJson() {
		Constraint constraint = MockObjectUtil.createConstraint();
		var jsonNode = transformer.transform(constraint);
		
		assertNotNull(jsonNode);
		
		assertEquals(constraint.getOperator().toString(), jsonNode.get(DSpaceConstants.ODRL_OPERATOR).asText());
		assertEquals(LeftOperand.ABSOLUTE_POSITION.toString(), jsonNode.get(DSpaceConstants.ODRL_LEFT_OPERAND).asText());
		assertEquals(MockObjectUtil.RIGHT_EXPRESSION, jsonNode.get(DSpaceConstants.ODRL_RIGHT_OPERAND).asText());
	}
}
