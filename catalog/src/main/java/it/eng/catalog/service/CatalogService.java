package it.eng.catalog.service;

import java.util.Arrays;
import java.util.UUID;

import org.springframework.stereotype.Service;

import it.eng.catalog.entity.CatalogEntity;
import it.eng.catalog.mapper.CatalogMapper;
import it.eng.catalog.model.Action;
import it.eng.catalog.model.Catalog;
import it.eng.catalog.model.Constraint;
import it.eng.catalog.model.DataService;
import it.eng.catalog.model.Dataset;
import it.eng.catalog.model.Distribution;
import it.eng.catalog.model.LeftOperand;
import it.eng.catalog.model.Multilanguage;
import it.eng.catalog.model.Offer;
import it.eng.catalog.model.Operator;
import it.eng.catalog.model.Permission;
import it.eng.catalog.model.Reference;
import it.eng.catalog.model.Serializer;
import it.eng.catalog.repository.CatalogRepository;

@Service
public class CatalogService {
	
	private CatalogRepository catalogRepository;
	
	public CatalogService(CatalogRepository catalogRepository) {
		super();
		this.catalogRepository = catalogRepository;
	}

	public Catalog findAll() {
		return new CatalogMapper().entityToModel(catalogRepository.findAll().get(0));
	}

	public Catalog findById(String id) {
		return getDemoCatalog();
	}

	public void save(Catalog catalog) {
		// TODO Auto-generated method stub
		
	}

	private Catalog getDemoCatalog() {
		final String CONFORMSTO = "conformsToSomething";
		final String CREATOR = "Chuck Norris";
		final String IDENTIFIER = "Uniwue identifier for tests";
		final String ISSUED = "yesterday";
		final String MODIFIED = "today";
		final String TITLE = "Title for test";
		final String ENDPOINT_URL = "https://provider-a.com/connector";
		
		CatalogEntity cc = catalogRepository.findById("1dc45797-3333-4955-8baf-ab7fd66ac4d5").get();
		System.out.println(Serializer.serializePlain(cc));
		System.out.println("---------------------");
//		System.out.println(Serializer.serializeProtocol(cc));
		
		String dataServiceId = UUID.randomUUID().toString();
		 DataService dataService = DataService.Builder.newInstance()
				.id(dataServiceId)
				.endpointURL(ENDPOINT_URL)
				.endpointDescription("endpoint description")
				.build();
		
		 Constraint constraint = Constraint.Builder.newInstance()
				.leftOperand(LeftOperand.COUNT)
				.operator(Operator.EQ)
				.rightOperand("5")
				.build();
		
		 Permission permission = Permission.Builder.newInstance()
				.action(Action.USE)
				.constraint(Arrays.asList(constraint))
				.build();
		
		 Offer offer = Offer.Builder.newInstance()
//				.target(TARGET)
//				.assignee(CatalogUtil.ASSIGNEE)
//				.assigner(CatalogUtil.ASSIGNER)
				.permission(Arrays.asList(permission))
				.build();
		
		 Dataset dataset = Dataset.Builder.newInstance()
				.conformsTo(CONFORMSTO)
				.creator(CREATOR)
				.description(Arrays.asList(Multilanguage.Builder.newInstance().language("en").value("For test").build(),
						Multilanguage.Builder.newInstance().language("it").value("For test but in Italian").build()))
				.distribution(Arrays.asList(Distribution.Builder.newInstance()
						.dataService(null)
//						.description(Arrays.asList(Multilanguage.Builder.newInstance().language("en").value("DS descr for test").build()))
//						.issued(CatalogUtil.ISSUED)
//						.modified(CatalogUtil.MODIFIED)
//						.title("Distribution title for tests")
						.format(Reference.Builder.newInstance().id("dspace:s3+push").build())
						.build()))
				.identifier(IDENTIFIER)
				.issued(ISSUED)
				.keyword(Arrays.asList("keyword1", "keyword2"))
				.modified(MODIFIED)
				.theme(Arrays.asList("white", "blue", "aqua"))
				.title(TITLE)
				.hasPolicy(Arrays.asList(offer))
				.build();
		
		Distribution distribution = Distribution.Builder.newInstance()
			.dataService(null)
			.format(Reference.Builder.newInstance().id("dspace:s3+push").build())
			.dataService(Arrays.asList(dataService))
			.build();
		
		 Catalog catalog = Catalog.Builder.newInstance()
				.conformsTo(CONFORMSTO)
				.creator(CREATOR)
				.description(Arrays.asList(Multilanguage.Builder.newInstance().language("en").value("Catalog test").build(),
						Multilanguage.Builder.newInstance().language("it").value("Catalog test but in Italian").build()))
				.distribution(Arrays.asList(Distribution.Builder.newInstance()
						.dataService(null)
//						.description(Arrays.asList(Multilanguage.Builder.newInstance().language("en").value("DS descr for test").build()))
//						.issued(CatalogUtil.ISSUED)
//						.modified(CatalogUtil.MODIFIED)
//						.title("Distribution title for tests")
						.format(Reference.Builder.newInstance().id("dspace:s3+push").build())
						.build()))
				.identifier(IDENTIFIER)
				.issued(ISSUED)
				.keyword(Arrays.asList("keyword1", "keyword2"))
				.modified(MODIFIED)
				.theme(Arrays.asList("white", "blue", "aqua"))
				.title(TITLE)
				.participantId("urn:example:DataProviderA")
				.service(Arrays.asList(dataService))
				.dataset(Arrays.asList(dataset))
				.distribution(Arrays.asList(distribution))
				.build();
		 return catalog;
	}
}
