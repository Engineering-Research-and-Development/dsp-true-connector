package it.eng.catalog.util;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

import it.eng.catalog.model.Action;
import it.eng.catalog.model.Catalog;
import it.eng.catalog.model.CatalogError;
import it.eng.catalog.model.CatalogRequestMessage;
import it.eng.catalog.model.Constraint;
import it.eng.catalog.model.DataService;
import it.eng.catalog.model.Dataset;
import it.eng.catalog.model.DatasetRequestMessage;
import it.eng.catalog.model.Distribution;
import it.eng.catalog.model.LeftOperand;
import it.eng.catalog.model.Multilanguage;
import it.eng.catalog.model.Offer;
import it.eng.catalog.model.Operator;
import it.eng.catalog.model.Permission;
import it.eng.catalog.model.Reference;
import it.eng.catalog.serializer.Serializer;

public class MockObjectUtil {

	public static final String CONSUMER_PID = "urn:uuid:CONSUMER_PID";
	public static final String PROVIDER_PID = "urn:uuid:PROVIDER_PID";
    public static final String RIGHT_EXPRESSION_COUNT = "5";
    public static final String USE = "use";
    public static final String INCLUDED_IN = "includedInAction";
    public static final String ASSIGNEE = "assignee";
    public static final String ASSIGNER = "assigner";
    public static final String TARGET = "target";
    public static final String CONFORMSTO = "conformsToSomething";
    public static final String CREATOR = "Chuck Norris";
    public static final String IDENTIFIER = "Unique identifier for tests";
    public static final Instant ISSUED = Instant.parse("2024-04-23T16:26:00Z");
    public static final Instant MODIFIED = Instant.parse("2024-04-23T16:26:00Z");
    public static final String TITLE = "Title for test";
    public static final String ENDPOINT_URL = "https://provider-a.com/connector";

    public static final Multilanguage MULTILANGUAGE =
            Multilanguage.Builder.newInstance().language("en").value("For test").build();
    
    public static final Multilanguage MULTILANGUAGE_UPDATE =
            Multilanguage.Builder.newInstance().language("en").value("For test update").build();


    public static final Constraint CONSTRAINT = Constraint.Builder.newInstance()
            .leftOperand(LeftOperand.COUNT)
            .operator(Operator.EQ)
            .rightOperand(RIGHT_EXPRESSION_COUNT)
            .build();
    
    public static final Constraint CONSTRAINT_COUNT_5_TIMES = Constraint.Builder.newInstance()
            .leftOperand(LeftOperand.COUNT)
            .rightOperand("5")
            .operator(Operator.EQ)
            .build();

    public static final Permission PERMISSION = Permission.Builder.newInstance()
            .action(Action.USE)
            .constraint(Arrays.asList(CONSTRAINT).stream().collect(Collectors.toCollection(HashSet::new)))
            .build();
    
    public static final Permission PERMISSION_UPDATE = Permission.Builder.newInstance()
            .action(Action.USE)
            .constraint(Arrays.asList(CONSTRAINT_COUNT_5_TIMES).stream().collect(Collectors.toCollection(HashSet::new)))
            .build();
    
    public static final Permission PERMISSION_ANONYMIZE = Permission.Builder.newInstance()
            .action(Action.ANONYMIZE)
            .constraint(Arrays.asList(CONSTRAINT_COUNT_5_TIMES).stream().collect(Collectors.toCollection(HashSet::new)))
            .build();

    public static final Offer OFFER = Offer.Builder.newInstance()
    		.id("urn:offer_id")
//            .target(TARGET)
            .permission(Arrays.asList(PERMISSION).stream().collect(Collectors.toCollection(HashSet::new)))
            .build();
    
    public static final Offer OFFER_WITH_TARGET = Offer.Builder.newInstance()
    		.id("urn:offer_id")
            .target(TARGET)
            .permission(Arrays.asList(PERMISSION).stream().collect(Collectors.toCollection(HashSet::new)))
            .build();
    
    public static final Offer OFFER_UPDATE = Offer.Builder.newInstance()
    		.id("urn:offer_id_update")
            .target(TARGET)
            .permission(Arrays.asList(PERMISSION_UPDATE).stream().collect(Collectors.toCollection(HashSet::new)))
            .build();

    public static final Distribution DISTRIBUTION = Distribution.Builder.newInstance()
            .title(TITLE)
            .description(Arrays.asList(MULTILANGUAGE).stream().collect(Collectors.toCollection(HashSet::new)))
            .issued(ISSUED)
            .modified(MODIFIED)
            .format(Reference.Builder.newInstance().id("HTTP:PULL").build())
            .hasPolicy(Arrays.asList(OFFER).stream().collect(Collectors.toCollection(HashSet::new)))
            .accessService(Arrays.asList(DataServiceUtil.DATA_SERVICE).stream().collect(Collectors.toCollection(HashSet::new)))
            .build();

    public static final Distribution DISTRIBUTION_FOR_UPDATE = Distribution.Builder.newInstance()
            .title(TITLE + " update")
            .description(Arrays.asList(MULTILANGUAGE).stream().collect(Collectors.toCollection(HashSet::new)))
            .issued(ISSUED)
            .modified(MODIFIED)
            .hasPolicy(Arrays.asList(OFFER_UPDATE).stream().collect(Collectors.toCollection(HashSet::new)))
            .accessService(Arrays.asList(DataServiceUtil.DATA_SERVICE).stream().collect(Collectors.toCollection(HashSet::new)))
            .version(0L)
            .createdBy("admin@mail.com")
            .lastModifiedBy("admin@mail.com")
            .build();

    public static final Collection<Distribution> DISTRIBUTIONS = Arrays.asList(DISTRIBUTION);
    public static final Dataset DATASET = Dataset.Builder.newInstance()
            .conformsTo(CONFORMSTO)
            .creator(CREATOR)
            .distribution(Arrays.asList(DISTRIBUTION).stream().collect(Collectors.toCollection(HashSet::new)))
            .description(Arrays.asList(MULTILANGUAGE).stream().collect(Collectors.toCollection(HashSet::new)))
            .issued(ISSUED)
            .keyword(Arrays.asList("keyword1", "keyword2").stream().collect(Collectors.toCollection(HashSet::new)))
            .identifier(IDENTIFIER)
            .modified(MODIFIED)
            .theme(Arrays.asList("white", "blue", "aqua").stream().collect(Collectors.toCollection(HashSet::new)))
            .title(TITLE)
            .hasPolicy(Arrays.asList(OFFER).stream().collect(Collectors.toCollection(HashSet::new)))
            .build();

    public static final Dataset DATASET_FOR_UPDATE = Dataset.Builder.newInstance()
            .conformsTo(CONFORMSTO)
            .creator(CREATOR + " update")
            .distribution(Arrays.asList(DISTRIBUTION_FOR_UPDATE).stream().collect(Collectors.toCollection(HashSet::new)))
            .description(Arrays.asList(MULTILANGUAGE_UPDATE).stream().collect(Collectors.toCollection(HashSet::new)))
            .issued(ISSUED)
            .keyword(Arrays.asList("keyword1 update", "keyword2").stream().collect(Collectors.toCollection(HashSet::new)))
            .identifier(IDENTIFIER)
            .modified(MODIFIED)
            .theme(Arrays.asList("white", "blue", "aqua").stream().collect(Collectors.toCollection(HashSet::new)))
            .title(TITLE + " update")
            .hasPolicy(Arrays.asList(OFFER_UPDATE).stream().collect(Collectors.toCollection(HashSet::new)))
            .version(0L)
            .createdBy("admin@mail.com")
            .lastModifiedBy("admin@mail.com")
            .issued(ISSUED)
            .modified(MODIFIED)
            .build();

    public static final Set<Dataset> DATASETS = Arrays.asList(DATASET).stream().collect(Collectors.toCollection(HashSet::new));
    
    public static final Catalog CATALOG = Catalog.Builder.newInstance()
            .conformsTo(CONFORMSTO)
            .creator(CREATOR)
            .description(Arrays.asList(Multilanguage.Builder.newInstance().language("en").value("Catalog description").build()).stream().collect(Collectors.toCollection(HashSet::new)))
            .identifier(IDENTIFIER)
            .issued(ISSUED)
            .keyword(Arrays.asList("keyword1", "keyword2").stream().collect(Collectors.toCollection(HashSet::new)))
            .modified(MODIFIED)
            .theme(Arrays.asList("white", "blue", "aqua").stream().collect(Collectors.toCollection(HashSet::new)))
            .title(TITLE)
            .participantId("urn:example:DataProviderA")
            .service(Arrays.asList(DataServiceUtil.DATA_SERVICE).stream().collect(Collectors.toCollection(HashSet::new)))
            .dataset(Arrays.asList(DATASET).stream().collect(Collectors.toCollection(HashSet::new)))
            .distribution(Arrays.asList(DISTRIBUTION).stream().collect(Collectors.toCollection(HashSet::new)))
            .hasPolicy(Arrays.asList(OFFER).stream().collect(Collectors.toCollection(HashSet::new)))
            .homepage(ENDPOINT_URL)
            .build();

    public static final Catalog CATALOG_FOR_UPDATE = Catalog.Builder.newInstance()
            .conformsTo(CONFORMSTO)
            .creator(CREATOR)
            .description(Arrays.asList(Multilanguage.Builder.newInstance().language("en").value("Catalog description update").build())
            		.stream().collect(Collectors.toCollection(HashSet::new)))
            .identifier(IDENTIFIER)
            .keyword(Arrays.asList("keyword1", "keyword2").stream().collect(Collectors.toCollection(HashSet::new)))
            .theme(Arrays.asList("white", "blue", "aqua").stream().collect(Collectors.toCollection(HashSet::new)))
            .title(TITLE)
            .participantId("urn:example:DataProviderA")
            .service(Arrays.asList(DataServiceUtil.DATA_SERVICE_UPDATE).stream().collect(Collectors.toCollection(HashSet::new)))
            .dataset(Arrays.asList(DATASET).stream().collect(Collectors.toCollection(HashSet::new)))
            .distribution(Arrays.asList(DISTRIBUTION_FOR_UPDATE).stream().collect(Collectors.toCollection(HashSet::new)))
            .hasPolicy(Arrays.asList(OFFER_WITH_TARGET).stream().collect(Collectors.toCollection(HashSet::new)))
            .homepage(ENDPOINT_URL)
            .version(0L)
            .createdBy("admin@mail.com")
            .lastModifiedBy("admin@mail.com")
            .issued(ISSUED)
            .modified(MODIFIED)
            .build();

    public static final CatalogError CATALOG_ERROR = CatalogError.Builder.newInstance().build();

    public static final CatalogRequestMessage CATALOG_REQUEST_MESSAGE = CatalogRequestMessage.Builder.newInstance()
            .filter(List.of("some-filter"))
            .build();

    public static final Collection<Catalog> CATALOGS = Arrays.asList(CATALOG);

    public static final DatasetRequestMessage DATASET_REQUEST_MESSAGE = DatasetRequestMessage.Builder.newInstance()
            .dataset(Serializer.serializeProtocol(DATASET))
            .build();

    public static final DataService DATA_SERVICE = DataService.Builder.newInstance()
            .keyword(Arrays.asList("keyword1", "keyword2").stream().collect(Collectors.toCollection(HashSet::new)))
            .theme(Arrays.asList("white", "blue", "aqua").stream().collect(Collectors.toCollection(HashSet::new)))
            .conformsTo(CONFORMSTO)
            .creator(CREATOR)
            .description(Arrays.asList(MULTILANGUAGE).stream().collect(Collectors.toCollection(HashSet::new)))
            .identifier(IDENTIFIER)
            .issued(ISSUED)
            .modified(MODIFIED)
            .title(TITLE)
            .endpointDescription("Description for test")
            .endpointURL(ENDPOINT_URL)
            .build();

    public static final DataService DATA_SERVICE_FOR_UPDATE = DataService.Builder.newInstance()
            .keyword(Arrays.asList("keyword1", "keyword2").stream().collect(Collectors.toCollection(HashSet::new)))
            .theme(Arrays.asList("white", "blue", "aqua").stream().collect(Collectors.toCollection(HashSet::new)))
            .conformsTo(CONFORMSTO)
            .creator(CREATOR + " update")
            .description(Arrays.asList(MULTILANGUAGE_UPDATE).stream().collect(Collectors.toCollection(HashSet::new)))
            .identifier(IDENTIFIER)
            .issued(ISSUED)
            .modified(MODIFIED)
            .title(TITLE + " update")
            .endpointDescription("Description for test update")
            .endpointURL(ENDPOINT_URL)
            .version(0L)
            .createdBy("admin@mail.com")
            .lastModifiedBy("admin@mail.com")
            .build();

    public static final Collection<DataService> DATA_SERVICES = Arrays.asList(DATA_SERVICE);


    public static void getAllKeysUsingJsonNodeFieldNames(JsonNode jsonNode, Set<String> keys) {
        if (jsonNode.isObject()) {
            Iterator<Entry<String, JsonNode>> fields = jsonNode.fields();
            fields.forEachRemaining(field -> {
                String key = field.getKey();
                if (key.contains(":")) {
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
