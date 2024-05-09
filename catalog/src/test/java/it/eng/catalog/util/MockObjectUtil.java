package it.eng.catalog.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import it.eng.catalog.model.*;

import java.time.Instant;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

public class MockObjectUtil {

    public static final String RIGHT_EXPRESSION = "EU";
    public static final String USE = "use";
    public static final String INCLUDED_IN = "includedInAction";
    public static final String ASSIGNEE = "assignee";
    public static final String ASSIGNER = "assigner";
    public static final String TARGET = "target";
    public static final String CONFORMSTO = "conformsToSomething";
    public static final String CREATOR = "Chuck Norris";
    public static final String IDENTIFIER = "Uniwue identifier for tests";
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

    public static final Permission PERMISSION = Permission.Builder.newInstance()
            .action(Action.USE)
            .constraint(Arrays.asList(CONSTRAINT))
            .build();

    public static final Offer OFFER = Offer.Builder.newInstance()
            .target(TARGET)
            .permission(Arrays.asList(PERMISSION))
            .build();

    public static final Distribution DISTRIBUTION = Distribution.Builder.newInstance()
            .title(MockObjectUtil.TITLE)
            .description(Arrays.asList(MockObjectUtil.MULTILANGUAGE))
            .issued(MockObjectUtil.ISSUED)
            .modified(MockObjectUtil.MODIFIED)
            .hasPolicy(Arrays.asList(MockObjectUtil.OFFER))
            .accessService(Arrays.asList(DataServiceUtil.DATA_SERVICE))
            .build();

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
            .hasPolicy(Arrays.asList(OFFER))
            .build();

    public static final List<Dataset> DATASETS = Arrays.asList(DATASET);
    public static final Catalog CATALOG = Catalog.Builder.newInstance()
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
            .service(Arrays.asList(DataServiceUtil.DATA_SERVICE))
            .dataset(Arrays.asList(DATASET))
            .distribution(Arrays.asList(DISTRIBUTION))
            .build();

    public static final CatalogError CATALOG_ERROR = CatalogError.Builder.newInstance().build();

    public static final CatalogRequestMessage CATALOG_REQUEST_MESSAGE = CatalogRequestMessage.Builder.newInstance()
            .filter(List.of("some-filter"))
            .build();

    public static final DatasetRequestMessage DATASET_REQUEST_MESSAGE = DatasetRequestMessage.Builder.newInstance()
            .dataset(Serializer.serializeProtocol(MockObjectUtil.DATASET))
            .build();

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
