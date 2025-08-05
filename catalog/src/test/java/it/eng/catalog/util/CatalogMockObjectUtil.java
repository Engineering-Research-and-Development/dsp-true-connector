package it.eng.catalog.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import it.eng.catalog.model.*;
import it.eng.catalog.serializer.CatalogSerializer;
import it.eng.tools.model.Artifact;
import it.eng.tools.model.ArtifactType;
import it.eng.tools.util.ToolsUtil;
import org.apache.commons.lang3.StringUtils;
import org.bson.types.ObjectId;
import org.springframework.http.MediaType;

import java.time.Instant;
import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;

public class CatalogMockObjectUtil {

	public static final String CONSUMER_PID = "urn:uuid:CONSUMER_PID";
	public static final String PROVIDER_PID = "urn:uuid:PROVIDER_PID";
	public static final String DATASET_ID = "dataset_uuid_test";
    public static final String RIGHT_EXPRESSION_COUNT = "5";
    public static final String USE = "use";
    public static final String INCLUDED_IN = "includedInAction";
    public static final String ASSIGNEE = "assignee";
    public static final String ASSIGNER = "assigner";
    public static final String TARGET = "target";
    public static final String CONFORMSTO = "conformsToSomething";
    public static final String CREATOR = "Chuck_Norris";
    public static final String IDENTIFIER = "Unique_identifier_for_tests";
    public static final Instant ISSUED = Instant.parse("2024-04-23T16:26:00Z");
    public static final Instant MODIFIED = Instant.parse("2024-04-23T16:26:00Z");
    public static final String TITLE = "Title_for_test";
    public static final String ENDPOINT_URL = "https://provider-a.com/connector";
    public static final String FILE_ID = "some_file";
    public static final Instant NOW = Instant.now();


    public static final Multilanguage MULTILANGUAGE =
            Multilanguage.Builder.newInstance().language("en").value("For test").build();

    public static final Multilanguage MULTILANGUAGE_UPDATE =
            Multilanguage.Builder.newInstance().language("en").value("For test update").build();

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
            .endpointURL(ENDPOINT_URL + " update")
            .createdBy("admin@mail.com")
            .lastModifiedBy("admin@mail.com")
            .build();

    public static final DataService DATA_SERVICE = DataService.Builder.newInstance()
            .id(UUID.randomUUID().toString())
            .keyword(Arrays.asList("DataService keyword1", "DataService keyword2").stream().collect(Collectors.toCollection(HashSet::new)))
            .theme(Arrays.asList("DataService theme1", "DataService theme2").stream().collect(Collectors.toCollection(HashSet::new)))
            .conformsTo(CONFORMSTO)
            .creator(CREATOR)
            .description(Arrays.asList(MULTILANGUAGE).stream().collect(Collectors.toCollection(HashSet::new)))
            .identifier(IDENTIFIER)
            .issued(ISSUED)
            .modified(MODIFIED)
            .title(TITLE)
            .endpointURL("http://dataservice.com")
            .endpointDescription("endpoint description")
            .build();

    public static final Constraint CONSTRAINT = Constraint.Builder.newInstance()
            .leftOperand(LeftOperand.COUNT)
            .operator(Operator.LTEQ)
            .rightOperand(RIGHT_EXPRESSION_COUNT)
            .build();
    
    public static final Constraint CONSTRAINT_COUNT_5_TIMES = Constraint.Builder.newInstance()
            .leftOperand(LeftOperand.COUNT)
            .rightOperand("5")
            .operator(Operator.EQ)
            .build();

    public static final Artifact ARTIFACT_FILE = Artifact.Builder.newInstance()
			.artifactType(ArtifactType.FILE)
			.contentType(MediaType.APPLICATION_JSON.getType())
			.createdBy(CREATOR)
			.created(NOW)
			.lastModifiedDate(NOW)
			.filename("Employees.txt")
			.lastModifiedBy(CREATOR)
			.value(new ObjectId().toHexString())
			.build();
    
	public static final Artifact ARTIFACT_EXTERNAL = Artifact.Builder.newInstance()
			.artifactType(ArtifactType.EXTERNAL)
			.createdBy(CREATOR)
			.created(NOW)
			.lastModifiedDate(NOW)
			.lastModifiedBy(CREATOR)
			.value("https://example.com/employees")
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
            .target(DATASET_ID)
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
            .accessService(Arrays.asList(DATA_SERVICE).stream().collect(Collectors.toCollection(HashSet::new)))
            .build();

    public static final Distribution DISTRIBUTION_FOR_UPDATE = Distribution.Builder.newInstance()
            .title(TITLE + " update")
            .description(Arrays.asList(MULTILANGUAGE).stream().collect(Collectors.toCollection(HashSet::new)))
            .issued(ISSUED)
            .modified(MODIFIED)
            .hasPolicy(Arrays.asList(OFFER_UPDATE).stream().collect(Collectors.toCollection(HashSet::new)))
            .accessService(Arrays.asList(DATA_SERVICE).stream().collect(Collectors.toCollection(HashSet::new)))
            .createdBy("admin@mail.com")
            .lastModifiedBy("admin@mail.com")
            .build();

    public static final Collection<Distribution> DISTRIBUTIONS = Arrays.asList(DISTRIBUTION);
    public static final Dataset DATASET = Dataset.Builder.newInstance()
    		.id(DATASET_ID)
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
    
    public static final Dataset DATASET_WITH_ARTIFACT = Dataset.Builder.newInstance()
    		.id(DATASET_ID)
            .conformsTo(CONFORMSTO)
            .creator(CREATOR)
            .artifact(ARTIFACT_FILE)
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
            .service(Arrays.asList(DATA_SERVICE).stream().collect(Collectors.toCollection(HashSet::new)))
            .dataset(Arrays.asList(DATASET).stream().collect(Collectors.toCollection(HashSet::new)))
            .distribution(Arrays.asList(DISTRIBUTION).stream().collect(Collectors.toCollection(HashSet::new)))
            .hasPolicy(Arrays.asList(OFFER).stream().collect(Collectors.toCollection(HashSet::new)))
            .homepage(ENDPOINT_URL)
            .build();

    /**
     * Creates a new Catalog instance with predefined values for testing purposes.
     *
     * @return A new Catalog instance.
     */
    public static Catalog createNewCatalog() {
//        the Dataset has all the necessary data, and with this we unsure that all nested fields are the same, with createNewMethods some fields would not match
//        e.g. catalog.getDistribution and catalog.getDataset.getDistribution are equal since the same Distribution is set in both places
        Dataset dataset = createNewDataset();
        return Catalog.Builder.newInstance()
                .conformsTo(CONFORMSTO)
                .creator(CREATOR)
                .description(dataset.getDescription())
                .identifier(IDENTIFIER)
                .issued(ISSUED)
                .keyword(new HashSet<>(Arrays.asList("keyword1", "keyword2")))
                .modified(MODIFIED)
                .theme(new HashSet<>(Arrays.asList("white", "blue", "aqua")))
                .title(TITLE)
                .participantId("urn:example:DataProviderA")
                .service(dataset.getDistribution().stream().findFirst().get().getAccessService())
                .dataset(new HashSet<>(Collections.singletonList(dataset)))
                .distribution(dataset.getDistribution())
                .hasPolicy(dataset.getHasPolicy())
                .homepage(ENDPOINT_URL)
                .build();
    }

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
            .service(Arrays.asList(DATA_SERVICE_FOR_UPDATE).stream().collect(Collectors.toCollection(HashSet::new)))
            .dataset(Arrays.asList(DATASET_FOR_UPDATE).stream().collect(Collectors.toCollection(HashSet::new)))
            .distribution(Arrays.asList(DISTRIBUTION_FOR_UPDATE).stream().collect(Collectors.toCollection(HashSet::new)))
            .hasPolicy(Arrays.asList(OFFER).stream().collect(Collectors.toCollection(HashSet::new)))
            .homepage(ENDPOINT_URL)
            .issued(ISSUED)
            .modified(MODIFIED)
            .build();

    public static final CatalogError CATALOG_ERROR = CatalogError.Builder.newInstance().build();

    public static final CatalogRequestMessage CATALOG_REQUEST_MESSAGE = CatalogRequestMessage.Builder.newInstance()
            .filter(List.of("some-filter"))
            .build();

    public static final Collection<Catalog> CATALOGS = Arrays.asList(CATALOG);

    public static final DatasetRequestMessage DATASET_REQUEST_MESSAGE = DatasetRequestMessage.Builder.newInstance()
            .dataset(CatalogSerializer.serializeProtocol(DATASET))
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

    /**
     * Creates a new Multilanguage instance for catalog description.
     *
     * @return A new Multilanguage instance.
     */
    public static final Multilanguage createNewMultilanguage() {
        return Multilanguage.Builder.newInstance()
                .language("en")
                .value("Catalog description")
                .build();
    }

    /**
     * Creates a new DataService instance.
     *
     * @return A new DataService instance.
     */
    public static final DataService createNewDataService() {
        return DataService.Builder.newInstance()
                .keyword(Arrays.asList("DataService keyword1", "DataService keyword2").stream().collect(Collectors.toCollection(HashSet::new)))
                .theme(Arrays.asList("DataService theme1", "DataService theme2").stream().collect(Collectors.toCollection(HashSet::new)))
                .conformsTo(CONFORMSTO)
                .creator(CREATOR)
                .description(Arrays.asList(MULTILANGUAGE).stream().collect(Collectors.toCollection(HashSet::new)))
                .identifier(IDENTIFIER)
                .issued(ISSUED)
                .modified(MODIFIED)
                .title(TITLE)
                .endpointURL("http://dataservice.com")
                .endpointDescription("endpoint description")
                .build();
    }

    /**
     * Creates a new Distribution instance.
     *
     * @return A new Distribution instance.
     */
    public static final Distribution createNewDistribution() {
        return Distribution.Builder.newInstance()
                .title(TITLE)
                .description(Arrays.asList(MULTILANGUAGE).stream().collect(Collectors.toCollection(HashSet::new)))
                .issued(ISSUED)
                .modified(MODIFIED)
                .format(Reference.Builder.newInstance().id("HTTP:PULL").build())
                .hasPolicy(Arrays.asList(createNewOffer()).stream().collect(Collectors.toCollection(HashSet::new)))
                .accessService(Arrays.asList(createNewDataService()).stream().collect(Collectors.toCollection(HashSet::new)))
                .build();
    }

    /**
     * Creates a new Permission instance.
     *
     * @return A new Permission instance.
     */
    public static final Permission createNewPermission() {
        return Permission.Builder.newInstance()
                .action(Action.USE)
                .constraint(Arrays.asList(CONSTRAINT).stream().collect(Collectors.toCollection(HashSet::new)))
                .build();
    }

    /**
     * Creates a new Offer instance.
     *
     * @return A new Offer instance.
     */
    public static final Offer createNewOffer() {
        return Offer.Builder.newInstance()
                .permission(Arrays.asList(createNewPermission()).stream().collect(Collectors.toCollection(HashSet::new)))
                .build();
    }

    /**
     * Creates a new Dataset instance.
     *
     * @return A new Dataset instance.
     */
    public static final Dataset createNewDataset() {
        String datasetId = ToolsUtil.generateUniqueId();
        return Dataset.Builder.newInstance()
                .id(datasetId)
                .conformsTo(CONFORMSTO)
                .creator(CREATOR)
                .distribution(Arrays.asList(createNewDistribution()).stream().collect(Collectors.toCollection(HashSet::new)))
                .description(Arrays.asList(createNewMultilanguage()).stream().collect(Collectors.toCollection(HashSet::new)))
                .issued(ISSUED)
                .keyword(Arrays.asList("keyword1", "keyword2").stream().collect(Collectors.toCollection(HashSet::new)))
                .identifier(IDENTIFIER)
                .modified(MODIFIED)
                .theme(Arrays.asList("white", "blue", "aqua").stream().collect(Collectors.toCollection(HashSet::new)))
                .title(TITLE)
                .hasPolicy(Arrays.asList(createNewOffer()).stream().collect(Collectors.toCollection(HashSet::new)))
                .artifact(createNewArtifact(datasetId))
                .build();
    }

    /**
     * Creates a new Artifact instance.
     *
     * @param datasetId The ID of the dataset associated with the artifact.
     * @return A new Artifact instance.
     */
    private static Artifact createNewArtifact(String datasetId) {
        return Artifact.Builder.newInstance()
                .artifactType(ArtifactType.FILE)
                .contentType(MediaType.APPLICATION_JSON.getType())
                .createdBy(CREATOR)
                .created(NOW)
                .lastModifiedDate(NOW)
                .filename("Employees.txt")
                .lastModifiedBy(CREATOR)
                .value(StringUtils.isNotBlank(datasetId) ? datasetId :DATASET_ID)
                .build();
    }

}
