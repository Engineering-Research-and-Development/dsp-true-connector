package it.eng.catalog.transformer.from;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import it.eng.catalog.model.Catalog;
import it.eng.catalog.model.DataService;
import it.eng.catalog.model.Dataset;
import it.eng.catalog.transformer.TransformInterface;
import it.eng.tools.model.DSpaceConstants;

@Component
public class JsonFromCatalogTransformer implements TransformInterface<Catalog, JsonNode> {

	private final ObjectMapper mapper = new ObjectMapper();

	@Override
	public Class<Catalog> getInputType() {
		return Catalog.class;
	}

	@Override
	public Class<JsonNode> getOutputType() {
		return JsonNode.class;
	}

	@Override
	public JsonNode transform(Catalog input) {
		Map<String, Object> map = new HashMap<>();
		map.put(DSpaceConstants.CONTEXT, DSpaceConstants.DATASPACE_CONTEXT_0_8_VALUE);
		map.put(DSpaceConstants.TYPE, DSpaceConstants.DSPACE + getInputType().getSimpleName());
		map.put(DSpaceConstants.ID, input.getId());
		JsonFromDatasetTransformer jsonFromDatasetTransform = new JsonFromDatasetTransformer();
		if(input.getDataset() != null) {
			List<Object> datasets = new ArrayList<>();
			for (Dataset dataset : input.getDataset()) {
				datasets.add(jsonFromDatasetTransform.transform(dataset));
			}
			map.put(DSpaceConstants.DATASET, datasets);
		}
//		JsonFromContractOfferTransformer jsonFromContractOfferTransformer = new JsonFromContractOfferTransformer();
//		if(input.getContractOffers() != null) {
//			List<Object> contractOffers = new ArrayList<>();
//			for (ContractOffer contractOffer : input.getContractOffers()) {
//				contractOffers.add(jsonFromContractOfferTransformer.transform(contractOffer));
//			}
//			map.put(DSpaceConstants.CONTRACT_OFFER, contractOffers);
//		}
		JsonFromDataServiceTransform jsonFromDataServiceTransformer = new JsonFromDataServiceTransform();
		if(input.getService() != null) {
			List<Object> dataServices = new ArrayList<>();
			for (DataService dataService : input.getService()) {
				dataServices.add(jsonFromDataServiceTransformer.transform(dataService));
			}
			map.put(DSpaceConstants.DATA_SERVICE, dataServices);
		}
		map.put(DSpaceConstants.TITLE, input.getTitle());
		map.put(DSpaceConstants.DESCRIPTION, input.getDescription());
		map.put(DSpaceConstants.KEYWORD, input.getKeyword().stream().collect(Collectors.joining(",")));
		return mapper.convertValue(map, JsonNode.class);
	}

}
