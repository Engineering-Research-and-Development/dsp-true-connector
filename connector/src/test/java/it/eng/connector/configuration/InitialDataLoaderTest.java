package it.eng.connector.configuration;

import it.eng.tools.event.AuditEventType;
import it.eng.tools.s3.properties.S3Properties;
import it.eng.tools.s3.service.S3BucketProvisionService;
import it.eng.tools.s3.service.S3ClientService;
import it.eng.tools.service.AuditEventPublisher;
import org.bson.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.env.Environment;
import org.springframework.data.mongodb.core.MongoTemplate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InitialDataLoaderTest {

    @Mock
    private MongoTemplate mongoTemplate;

    @Mock
    private Environment environment;

    @Mock
    private S3ClientService s3ClientService;

    @Mock
    private S3BucketProvisionService s3BucketProvisionService;

    @Mock
    private S3Properties s3Properties;

    @Mock
    private AuditEventPublisher publisher;

    @InjectMocks
    private InitialDataLoader initialDataLoader;

    @Test
    @DisplayName("Missing data file — application does not throw and mongo is untouched")
    void loadInitialData_missingFile_skipsLoadSilently() throws Exception {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"nonexistent-profile"});

        CommandLineRunner runner = initialDataLoader.loadInitialData();

        assertDoesNotThrow(() -> runner.run());
        verify(mongoTemplate, never()).save(any(Document.class), anyString());
    }

    @Test
    @DisplayName("File exists with new documents — all documents are saved")
    void loadInitialData_fileExists_insertsNewDocuments() throws Exception {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"unittest"});
        // No existing document — every document with an _id should be saved
        when(mongoTemplate.findById(any(), eq(Document.class), anyString())).thenReturn(null);

        CommandLineRunner runner = initialDataLoader.loadInitialData();

        assertDoesNotThrow(() -> runner.run());
        // 2 documents have an _id (saved after findById check) + 1 has no _id (saved directly) = 3 saves
        verify(mongoTemplate, times(3)).save(any(Document.class), eq("test_collection"));
    }

    @Test
    @DisplayName("File exists with already-persisted documents — duplicates are skipped")
    void loadInitialData_fileExists_skipsExistingDocuments() throws Exception {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"unittest"});
        // All documents with _id already exist
        when(mongoTemplate.findById(any(), eq(Document.class), anyString())).thenReturn(new Document());

        CommandLineRunner runner = initialDataLoader.loadInitialData();

        assertDoesNotThrow(() -> runner.run());
        // Only the document without an _id is saved; the 2 with existing _ids are skipped
        verify(mongoTemplate, times(1)).save(any(Document.class), eq("test_collection"));
    }

    @Test
    @DisplayName("No active profile — default initial_data.json is used without throwing")
    void loadInitialData_noActiveProfile_usesDefaultFile() throws Exception {
        when(environment.getActiveProfiles()).thenReturn(new String[]{});
        when(mongoTemplate.findById(any(), eq(Document.class), anyString())).thenReturn(null);

        CommandLineRunner runner = initialDataLoader.loadInitialData();

        // initial_data.json exists on the main classpath — must not throw
        assertDoesNotThrow(() -> runner.run());
        verify(mongoTemplate, atLeastOnce()).save(any(Document.class), anyString());
    }

    @Test
    @DisplayName("MongoTemplate throws during save — application does not shut down")
    void loadInitialData_mongoSaveThrows_doesNotCrash() throws Exception {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"unittest"});
        when(mongoTemplate.findById(any(), eq(Document.class), anyString())).thenReturn(null);
        when(mongoTemplate.save(any(Document.class), anyString()))
                .thenThrow(new RuntimeException("Simulated MongoDB failure"));

        CommandLineRunner runner = initialDataLoader.loadInitialData();

        assertDoesNotThrow(() -> runner.run());
    }

    @Test
    @DisplayName("Publisher is called with APPLICATION_START on ApplicationReadyEvent")
    void loadMockData_publishesApplicationStartEvent() {
        when(s3Properties.getBucketName()).thenReturn("test-bucket");
        doThrow(new RuntimeException("S3 not available")).when(s3BucketProvisionService)
                .ensureBucketCredentials(anyString());

        // Even if S3 fails, the method must not propagate the exception
        assertDoesNotThrow(() -> initialDataLoader.loadMockData());

        verify(publisher).publishEvent(argThat(event ->
                event.getEventType() == AuditEventType.APPLICATION_START));
    }
}
