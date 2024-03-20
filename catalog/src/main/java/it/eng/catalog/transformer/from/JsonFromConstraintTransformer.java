package it.eng.catalog.transformer.from;

import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import it.eng.catalog.model.Constraint;
import it.eng.catalog.transformer.TransformInterface;
import it.eng.tools.model.DSpaceConstants;

@Component
public class JsonFromConstraintTransformer implements TransformInterface<Constraint, JsonNode> {
	
	private final ObjectMapper mapper = new ObjectMapper();

	@Override
	public Class<Constraint> getInputType() {
		return Constraint.class;
	}

	@Override
	public Class<JsonNode> getOutputType() {
		return JsonNode.class;
	}

	@Override
	public JsonNode transform(Constraint input) {
		Map<String, Object> map = new HashMap<>();
		map.put(DSpaceConstants.ODRL_OPERATOR, input.getOperator().toString());
		map.put(DSpaceConstants.ODRL_LEFT_OPERAND, input.getLeftOperand());
		map.put(DSpaceConstants.ODRL_RIGHT_OPERAND, input.getRightOperand());
		return mapper.convertValue(map, getOutputType());
	}

}
