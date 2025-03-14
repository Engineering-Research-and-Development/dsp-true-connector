# Testcontainers

## Prerequisite

We need Docker installed on our machine to run the MongoDB container.

## Usage - MongoDB container

This is a starting reference for adding test containers to the project (probably needed when we add DAPS to integration tests).

First add these dependencies to the connector module, since the integration tests are there:

```
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>testcontainers</artifactId>
    <version>1.20.5</version>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>junit-jupiter</artifactId>
    <version>1.20.5</version>
    <scope>test</scope>
</dependency>
<dependency>
<dependency>
	<groupId>org.testcontainers</groupId>
	<artifactId>mongodb</artifactId>
	<version>1.20.5</version>
	<scope>test</scope>
</dependency>
```

This is an example for mongoDB. There are probably some imports which are not needed. We can create abstract class and put common code related with testcontainers there:

*NOTE*: make sure to match docker image version of Mongo with the one used in [docker-compose.yml](../ci/docker/docker-compose.yml) file

```
package it.eng.connector.integration;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest
public abstract class TestContainersBase {

	static MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:7.0.12").withExposedPorts(27017);
	
	 static {
         mongoDBContainer.start();
     }

	@DynamicPropertySource
	static void containersProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.data.mongodb.host", mongoDBContainer::getHost);
		registry.add("spring.data.mongodb.port", mongoDBContainer::getFirstMappedPort);
	}
}

```


Then, when creating integration test, we should extend this abstract class and start with writing test cases, like:

```
@AutoConfigureMockMvc
public class AppTestContainerTest extends TestContainersBase {

    @Autowired
    protected MockMvc mockMvc;
	
	@Test
	@WithUserDetails(TestUtil.ADMIN_USER)
	public void getProperies() throws Exception {
		
		ResultActions result =
				mockMvc.perform(
						get(ApiEndpoints.PROPERTIES_V1 + "/")
						.contentType(MediaType.APPLICATION_JSON_VALUE)
						.accept(MediaType.APPLICATION_JSON_VALUE));

		result.andExpect(status().isOk())
			.andExpect(content().contentType(MediaType.APPLICATION_JSON));

		String json = result.andReturn().getResponse().getContentAsString();
		assertNotNull(json);
	}
}
```

For a generic container(DAPS) you should use:

```
static GenericContainer<?> container = new GenericContainer(DockerImageName.parse("jboss/wildfly:9.0.1.Final"))
```