package it.eng.catalog.util;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

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
import it.eng.catalog.serializer.Serializer;

public class MockObjectUtil {

	public static final String CONSUMER_PID = "urn:uuid:CONSUMER_PID";
	public static final String PROVIDER_PID = "urn:uuid:PROVIDER_PID";
    public static final String RIGHT_EXPRESSION = "EU";
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

    public static final Constraint CONSTRAINT = Constraint.Builder.newInstance()
            .leftOperand(LeftOperand.ABSOLUTE_POSITION)
            .rightOperand(RIGHT_EXPRESSION)
            .operator(Operator.EQ)
            .build();
    
    public static final Constraint CONSTRAINT_COUNT_5_TIMES = Constraint.Builder.newInstance()
            .leftOperand(LeftOperand.COUNT)
            .rightOperand("5")
            .operator(Operator.EQ)
            .build();

    public static final Permission PERMISSION = Permission.Builder.newInstance()
            .action(Action.USE)
            .constraint(new HashSet<Constraint>(Arrays.asList(CONSTRAINT)))
            .build();
    
    public static final Permission PERMISSION_ANONYMIZE = Permission.Builder.newInstance()
            .action(Action.ANONYMIZE)
            .constraint(new HashSet<Constraint>(Arrays.asList(CONSTRAINT_COUNT_5_TIMES)))
            .build();

    public static final Offer OFFER = Offer.Builder.newInstance()
    		.id("urn:offer_id")
//            .target(TARGET)
            .permission(new HashSet<Permission>(Arrays.asList(PERMISSION)))
            .build();
    
    public static final Offer OFFER_WITH_TARGET = Offer.Builder.newInstance()
    		.id("urn:offer_id")
            .target(TARGET)
            .permission(new HashSet<Permission>(Arrays.asList(PERMISSION)))
            .build();

    public static final Distribution DISTRIBUTION = Distribution.Builder.newInstance()
            .title(TITLE)
            .description(Arrays.asList(MULTILANGUAGE))
            .issued(ISSUED)
            .modified(MODIFIED)
            .hasPolicy(Arrays.asList(OFFER))
            .accessService(Arrays.asList(DataServiceUtil.DATA_SERVICE))
            .build();

    public static final Distribution DISTRIBUTION_FOR_UPDATE = Distribution.Builder.newInstance()
            .title(TITLE)
            .description(Arrays.asList(MULTILANGUAGE))
            .issued(ISSUED)
            .modified(MODIFIED)
            .hasPolicy(Arrays.asList(OFFER))
            .accessService(Arrays.asList(DataServiceUtil.DATA_SERVICE))
            .version(0L)
            .createdBy("admin@mail.com")
            .lastModifiedBy("admin@mail.com")
            .build();

    public static final Collection<Distribution> DISTRIBUTIONS = Arrays.asList(DISTRIBUTION);
    public static final Dataset DATASET = Dataset.Builder.newInstance()
            .conformsTo(CONFORMSTO)
            .creator(CREATOR)
            .distribution(Arrays.asList(DISTRIBUTION))
            .description(Arrays.asList(MULTILANGUAGE))
            .issued(ISSUED)
            .keyword(Arrays.asList("keyword1", "keyword2"))
            .identifier(IDENTIFIER)
            .modified(MODIFIED)
            .theme(Arrays.asList("white", "blue", "aqua"))
            .title(TITLE)
            .hasPolicy(new HashSet<Offer>(Arrays.asList(OFFER)))
            .build();

    public static final Dataset DATASET_FOR_UPDATE = Dataset.Builder.newInstance()
            .conformsTo(CONFORMSTO)
            .creator(CREATOR)
            .distribution(Arrays.asList(DISTRIBUTION))
            .description(Arrays.asList(MULTILANGUAGE))
            .issued(ISSUED)
            .keyword(Arrays.asList("keyword1", "keyword2"))
            .identifier(IDENTIFIER)
            .modified(MODIFIED)
            .theme(Arrays.asList("white", "blue", "aqua"))
            .title(TITLE)
            .hasPolicy(new HashSet<Offer>(Arrays.asList(OFFER)))
            .version(0L)
            .createdBy("admin@mail.com")
            .lastModifiedBy("admin@mail.com")
            .issued(ISSUED)
            .modified(MODIFIED)
            .build();


    public static final Collection<Dataset> DATASETS = Arrays.asList(DATASET);
    public static final Catalog CATALOG = Catalog.Builder.newInstance()
            .conformsTo(CONFORMSTO)
            .creator(CREATOR)
            .description(Arrays.asList(Multilanguage.Builder.newInstance().language("en").value("Catalog description").build()))
            .identifier(IDENTIFIER)
            .issued(ISSUED)
            .keyword(new HashSet<String>(Arrays.asList("keyword1", "keyword2")))
            .modified(MODIFIED)
            .theme(Arrays.asList("white", "blue", "aqua"))
            .title(TITLE)
            .participantId("urn:example:DataProviderA")
            .service(Arrays.asList(DataServiceUtil.DATA_SERVICE))
            .dataset(Arrays.asList(DATASET))
            .distribution(Arrays.asList(DISTRIBUTION))
            .hasPolicy(Arrays.asList(OFFER))
            .homepage(ENDPOINT_URL)
            .build();

    public static final Catalog CATALOG_FOR_UPDATE = Catalog.Builder.newInstance()
            .conformsTo(CONFORMSTO)
            .creator(CREATOR)
            .description(Arrays.asList(Multilanguage.Builder.newInstance().language("en").value("Catalog description").build()))
            .identifier(IDENTIFIER)
            .keyword(new HashSet<String>(Arrays.asList("keyword1", "keyword2")))
            .theme(Arrays.asList("white", "blue", "aqua"))
            .title(TITLE)
            .participantId("urn:example:DataProviderA")
            .service(Arrays.asList(DataServiceUtil.DATA_SERVICE))
            .dataset(Arrays.asList(DATASET))
            .distribution(Arrays.asList(DISTRIBUTION))
            .hasPolicy(Arrays.asList(OFFER))
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
            .keyword(Arrays.asList("keyword1", "keyword2"))
            .theme(Arrays.asList("white", "blue", "aqua"))
            .conformsTo(CONFORMSTO)
            .creator(CREATOR)
            .description(Arrays.asList(MULTILANGUAGE))
            .identifier(IDENTIFIER)
            .issued(ISSUED)
            .modified(MODIFIED)
            .title(TITLE)
            .endpointDescription("Description for test")
            .endpointURL(ENDPOINT_URL)
            .servesDataset(DATASETS)
            .build();

    public static final DataService DATA_SERVICE_FOR_UPDATE = DataService.Builder.newInstance()
            .keyword(Arrays.asList("keyword1", "keyword2"))
            .theme(Arrays.asList("white", "blue", "aqua"))
            .conformsTo(CONFORMSTO)
            .creator(CREATOR)
            .description(Arrays.asList(MULTILANGUAGE))
            .identifier(IDENTIFIER)
            .issued(ISSUED)
            .modified(MODIFIED)
            .title(TITLE)
            .endpointDescription("Description for test")
            .endpointURL(ENDPOINT_URL)
            .servesDataset(DATASETS)
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
