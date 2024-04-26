//package it.eng.catalog.repository;
//
//import it.eng.catalog.model.Catalog;
//import it.eng.catalog.model.DataService;
//import it.eng.catalog.model.Dataset;
//import lombok.extern.slf4j.Slf4j;
//import org.bson.Document;
//import org.springframework.data.mongodb.core.mapping.event.BeforeSaveCallback;
//import org.springframework.stereotype.Component;
//
//import java.util.Collections;
//import java.util.List;
//import java.util.stream.Collectors;
//
//@Component
//@Slf4j
//public class CatalogOnBeforeSaveCallback implements BeforeSaveCallback<Catalog> {
//    @Override
//    public Catalog onBeforeSave(Catalog catalog, Document document, String collection) {
//        List<Dataset> auditDataSets = addAuditToDataSets(catalog);
//        List<DataService> auditDataServices = addAuditToDataServices(catalog);
//
//        Catalog c = Catalog.Builder.newInstance()
//                .keyword(catalog.getKeyword())
//                .theme(catalog.getTheme())
//                .conformsTo(catalog.getConformsTo())
//                .creator(catalog.getCreator())
//                .description(catalog.getDescription())
//                .identifier(catalog.getIdentifier())
//                .issued(catalog.getIssued())
//                .modified(catalog.getModified())
//                .title(catalog.getTitle())
//                .distribution(catalog.getDistribution())
//                .hasPolicy(catalog.getHasPolicy())
//                .dataset(auditDataSets)
//                .service(auditDataServices)
//                .participantId(catalog.getParticipantId())
//                .homepage(catalog.getHomepage())
//                .version(catalog.getVersion())
//                .lastModifiedBy(catalog.getLastModifiedBy())
//                .build();
//
//        return c;
//    }
//
//
//    private List<Dataset> addAuditToDataSets(Catalog catalog) {
//        if (catalog.getDataset() != null) {
//            return catalog.getDataset().stream().map(dataset -> {
//
//                Dataset.Builder builder = Dataset.Builder.newInstance()
//                        .keyword(dataset.getKeyword())
//                        .theme(dataset.getTheme())
//                        .conformsTo(dataset.getConformsTo())
//                        .creator(catalog.getCreator())
//                        .description(dataset.getDescription())
//                        .identifier(dataset.getIdentifier())
//                        .issued(catalog.getIssued())
//                        .modified(catalog.getModified())
//                        .title(dataset.getTitle())
//                        .hasPolicy(dataset.getHasPolicy())
//                        .distribution(dataset.getDistribution());
//
//
//                return builder.build();
//            }).collect(Collectors.toList());
//        }
//        return Collections.emptyList();
//    }
//
//    private List<DataService> addAuditToDataServices(Catalog catalog) {
//        if (catalog.getService() != null) {
//            return catalog.getService().stream().map(service -> {
//
//                DataService.Builder builder = DataService.Builder.newInstance()
//                        .keyword(service.getKeyword())
//                        .theme(service.getTheme())
//                        .conformsTo(service.getConformsTo())
//                        .creator(catalog.getCreator())
//                        .description(service.getDescription())
//                        .identifier(service.getIdentifier())
//                        .issued(catalog.getIssued())
//                        .modified(catalog.getModified())
//                        .title(service.getTitle())
//                        .endpointDescription(service.getEndpointDescription())
//                        .endpointURL(service.getEndpointURL())
//                        .servesDataset(service.getServesDataset());
//
//
//                return builder.build();
//            }).collect(Collectors.toList());
//        }
//        return Collections.emptyList();
//    }
//}
//
