package it.eng.connector.configuration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

class MongoConfigTest {

    @Test
    @DisplayName("transactionManager exposes a Mongo transaction manager bean")
    void transactionManagerExposesMongoTransactionManagerBean() {
        MongoConfig mongoConfig = new MongoConfig();
        MongoDatabaseFactory mongoDatabaseFactory = mock(MongoDatabaseFactory.class);

        MongoTransactionManager transactionManager = mongoConfig.transactionManager(mongoDatabaseFactory);

        assertNotNull(transactionManager);
        assertInstanceOf(MongoTransactionManager.class, transactionManager);
    }
}
