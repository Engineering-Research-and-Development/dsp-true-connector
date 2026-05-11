# Dataplane Signaling Protocol (DPS) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the Dataplane Signaling Protocol (DPS) by splitting the current monolithic connector into a Control Plane and independently deployable, horizontally-scalable Data Plane services.

**Architecture:** Each transfer type (`HttpData-PULL`, `HttpData-PUSH`) becomes a standalone Spring Boot application that communicates with the Control Plane via HTTP using DPS message shapes. The Control Plane registers Data Planes dynamically and routes transfer requests to the appropriate DP instance. No embedded Data Plane mode exists — all communication is real REST.

**Tech Stack:** Java 21, Spring Boot 3.5.x, MongoDB, Maven multi-module, Docker Compose, JUnit 5, Mockito, WireMock (integration tests)

---

## File Map

### New modules
| Module | Type | Artifact |
|---|---|---|
| `data-plane-api` | library JAR | `data-plane-api-${revision}.jar` |
| `data-plane-core` | library JAR | `data-plane-core-${revision}.jar` |
| `data-plane-http-pull` | executable Spring Boot JAR | `data-plane-http-pull-${revision}.jar` |
| `data-plane-http-push` | executable Spring Boot JAR | `data-plane-http-push-${revision}.jar` |

### New files — `data-plane-api`
- `data-plane-api/src/main/java/it/eng/dataplane/api/DataTransferProtocol.java` — protocol SPI interface
- `data-plane-api/src/main/java/it/eng/dataplane/api/model/DataFlow.java` — DataFlow model
- `data-plane-api/src/main/java/it/eng/dataplane/api/model/DataFlowState.java` — state enum
- `data-plane-api/src/main/java/it/eng/dataplane/api/model/DataFlowResult.java` — result value object
- `data-plane-api/src/main/java/it/eng/dataplane/api/message/DataFlowStartMessage.java` — DPS start payload
- `data-plane-api/src/main/java/it/eng/dataplane/api/message/DataFlowPrepareMessage.java` — DPS prepare payload
- `data-plane-api/src/main/java/it/eng/dataplane/api/message/DataFlowStatusMessage.java` — DPS callback payload

### New files — `data-plane-core`
- `data-plane-core/src/main/java/it/eng/dataplane/core/model/DataFlowEntity.java` — MongoDB entity
- `data-plane-core/src/main/java/it/eng/dataplane/core/repository/DataFlowRepository.java` — Spring Data repo
- `data-plane-core/src/main/java/it/eng/dataplane/core/service/DataFlowService.java` — orchestrates protocol + state
- `data-plane-core/src/main/java/it/eng/dataplane/core/controller/DataFlowController.java` — receives DPS messages from CP
- `data-plane-core/src/main/java/it/eng/dataplane/core/controller/ControlPlaneRegistrationController.java` — receives `/controlplanes` PUT from CP
- `data-plane-core/src/main/java/it/eng/dataplane/core/client/ControlPlaneClient.java` — sends callbacks to CP
- `data-plane-core/src/main/java/it/eng/dataplane/core/registry/DataTransferProtocolRegistry.java` — holds `DataTransferProtocol` beans
- `data-plane-core/src/main/java/it/eng/dataplane/core/security/DataPlaneSecurityConfig.java` — Spring Security for DP endpoints
- `data-plane-core/src/main/java/it/eng/dataplane/core/config/DataPlaneProperties.java` — `@ConfigurationProperties("dataplane")`
- `data-plane-core/src/main/java/it/eng/dataplane/core/startup/ControlPlaneRegistrationBean.java` — `ApplicationListener<ApplicationReadyEvent>` for self-registration with CP

### New files — `data-plane-http-pull`
- `data-plane-http-pull/src/main/java/it/eng/dataplane/httppull/DataPlaneHttpPullApplication.java` — `@SpringBootApplication`
- `data-plane-http-pull/src/main/java/it/eng/dataplane/httppull/HttpPullTransferProtocol.java` — implements `DataTransferProtocol`
- `data-plane-http-pull/src/main/resources/application.properties`

### New files — `data-plane-http-push`
- `data-plane-http-push/src/main/java/it/eng/dataplane/httppush/DataPlaneHttpPushApplication.java` — `@SpringBootApplication`
- `data-plane-http-push/src/main/java/it/eng/dataplane/httppush/HttpPushTransferProtocol.java` — implements `DataTransferProtocol`
- `data-plane-http-push/src/main/resources/application.properties`

### Modified files — `data-transfer` module (Control Plane side)
- `data-transfer/src/main/java/it/eng/datatransfer/model/DataPlaneRegistration.java` — NEW entity for registered DPs
- `data-transfer/src/main/java/it/eng/datatransfer/repository/DataPlaneRegistrationRepository.java` — NEW MongoDB repo
- `data-transfer/src/main/java/it/eng/datatransfer/service/DataPlaneRegistrationService.java` — NEW CRUD service
- `data-transfer/src/main/java/it/eng/datatransfer/rest/api/DataPlaneRegistrationController.java` — NEW admin API `PUT/DELETE /api/v1/dataplanes`
- `data-transfer/src/main/java/it/eng/datatransfer/client/DataPlaneClient.java` — NEW HTTP client (sends DPS to DP)
- `data-transfer/src/main/java/it/eng/datatransfer/router/DataPlaneRouter.java` — NEW selects DP endpoint by transferType
- `data-transfer/src/main/java/it/eng/datatransfer/rest/protocol/DataFlowCallbackController.java` — NEW receives DP callbacks
- `data-transfer/src/main/java/it/eng/datatransfer/service/api/DataTransferAPIService.java` — MODIFY: swap strategy calls for `DataPlaneClient` calls
- `data-transfer/src/main/java/it/eng/datatransfer/configuration/DataTransferConfiguration.java` — MODIFY: remove strategy beans

### Files to delete (Phase 8 cleanup)
- `data-transfer/src/main/java/it/eng/datatransfer/service/api/strategy/HttpPullTransferStrategy.java`
- `data-transfer/src/main/java/it/eng/datatransfer/service/api/strategy/HttpPushTransferStrategy.java`
- `data-transfer/src/main/java/it/eng/datatransfer/service/api/strategy/DataTransferStrategy.java`
- `data-transfer/src/main/java/it/eng/datatransfer/service/api/DataTransferStrategyFactory.java`
- `data-transfer/src/main/java/it/eng/datatransfer/model/DataTransferFormat.java`

---
## Phase 1 — Foundation

### Task 1: Maven scaffolding

**Files:**
- Modify: `pom.xml` (root, lines ~70-76 modules section)
- Create: `data-plane-api/pom.xml`
- Create: `data-plane-core/pom.xml`
- Create: `data-plane-http-pull/pom.xml`
- Create: `data-plane-http-push/pom.xml`

- [ ] **Step 1: Add modules to root pom.xml**

In `pom.xml`, find the `<modules>` block (currently contains `connector`, `catalog`, `tools`, `negotiation`, `data-transfer`) and add:

```xml
<module>data-plane-api</module>
<module>data-plane-core</module>
<module>data-plane-http-pull</module>
<module>data-plane-http-push</module>
```

- [ ] **Step 2: Create `data-plane-api/pom.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>it.eng</groupId>
        <artifactId>dsp-true-connector</artifactId>
        <version>${revision}</version>
    </parent>
    <artifactId>data-plane-api</artifactId>
    <packaging>jar</packaging>
    <name>Data Plane API</name>
    <dependencies>
        <dependency>
            <groupId>it.eng</groupId>
            <artifactId>tools</artifactId>
            <version>${revision}</version>
        </dependency>
        <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-databind</artifactId>
        </dependency>
        <dependency>
            <groupId>jakarta.validation</groupId>
            <artifactId>jakarta.validation-api</artifactId>
        </dependency>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
        </dependency>
    </dependencies>
</project>
```

- [ ] **Step 3: Create `data-plane-core/pom.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>it.eng</groupId>
        <artifactId>dsp-true-connector</artifactId>
        <version>${revision}</version>
    </parent>
    <artifactId>data-plane-core</artifactId>
    <packaging>jar</packaging>
    <name>Data Plane Core</name>
    <dependencies>
        <dependency>
            <groupId>it.eng</groupId>
            <artifactId>data-plane-api</artifactId>
            <version>${revision}</version>
        </dependency>
        <dependency>
            <groupId>it.eng</groupId>
            <artifactId>tools</artifactId>
            <version>${revision}</version>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-mongodb</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-security</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
        </dependency>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.mockito</groupId>
            <artifactId>mockito-core</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

- [ ] **Step 4: Create `data-plane-http-pull/pom.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>it.eng</groupId>
        <artifactId>dsp-true-connector</artifactId>
        <version>${revision}</version>
    </parent>
    <artifactId>data-plane-http-pull</artifactId>
    <packaging>jar</packaging>
    <name>Data Plane HTTP Pull</name>
    <dependencies>
        <dependency>
            <groupId>it.eng</groupId>
            <artifactId>data-plane-core</artifactId>
            <version>${revision}</version>
        </dependency>
        <dependency>
            <groupId>it.eng</groupId>
            <artifactId>tools</artifactId>
            <version>${revision}</version>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 5: Create `data-plane-http-push/pom.xml`** — same structure as http-pull, artifact `data-plane-http-push`, name `Data Plane HTTP Push`.

- [ ] **Step 6: Create Maven source directories**

```bash
mkdir -p data-plane-api/src/{main,test}/java/it/eng/dataplane/api/{model,message}
mkdir -p data-plane-core/src/{main,test}/java/it/eng/dataplane/core/{model,repository,service,controller,client,registry,security,config,startup}
mkdir -p data-plane-http-pull/src/{main,test}/java/it/eng/dataplane/httppull
mkdir -p data-plane-http-pull/src/main/resources
mkdir -p data-plane-http-push/src/{main,test}/java/it/eng/dataplane/httppush
mkdir -p data-plane-http-push/src/main/resources
```

- [ ] **Step 7: Verify root build compiles**

```bash
mvn -pl data-plane-api,data-plane-core,data-plane-http-pull,data-plane-http-push validate
```

Expected: `BUILD SUCCESS` for all 4 modules (nothing to compile yet but module wiring is correct).

- [ ] **Step 8: Commit**

```bash
git add pom.xml data-plane-api/pom.xml data-plane-core/pom.xml data-plane-http-pull/pom.xml data-plane-http-push/pom.xml
git commit -m "chore: scaffold data-plane-api, core, http-pull, http-push Maven modules"
```

---

### Task 2: `data-plane-api` — interface and message models

**Files:**
- Create: `data-plane-api/src/main/java/it/eng/dataplane/api/model/DataFlowState.java`
- Create: `data-plane-api/src/main/java/it/eng/dataplane/api/model/DataFlow.java`
- Create: `data-plane-api/src/main/java/it/eng/dataplane/api/model/DataFlowResult.java`
- Create: `data-plane-api/src/main/java/it/eng/dataplane/api/message/DataFlowStartMessage.java`
- Create: `data-plane-api/src/main/java/it/eng/dataplane/api/message/DataFlowPrepareMessage.java`
- Create: `data-plane-api/src/main/java/it/eng/dataplane/api/message/DataFlowStatusMessage.java`
- Create: `data-plane-api/src/main/java/it/eng/dataplane/api/DataTransferProtocol.java`
- Test: `data-plane-api/src/test/java/it/eng/dataplane/api/DataTransferProtocolTest.java`
- Test: `data-plane-api/src/test/java/it/eng/dataplane/api/model/DataFlowTest.java`
- Test: `data-plane-api/src/test/java/it/eng/dataplane/api/message/DataFlowStartMessageTest.java`
- Test: `data-plane-api/src/test/java/it/eng/dataplane/api/message/DataFlowPrepareMessageTest.java`
- Test: `data-plane-api/src/test/java/it/eng/dataplane/api/message/DataFlowStatusMessageTest.java`

- [ ] **Step 1: Write the failing test**

```java
// data-plane-api/src/test/java/it/eng/dataplane/api/DataTransferProtocolTest.java
package it.eng.dataplane.api;

import it.eng.dataplane.api.model.DataFlow;
import it.eng.dataplane.api.model.DataFlowResult;
import it.eng.dataplane.api.model.DataFlowState;
import org.junit.jupiter.api.Test;
import java.util.concurrent.CompletableFuture;
import static org.assertj.core.api.Assertions.assertThat;

class DataTransferProtocolTest {

    private final DataTransferProtocol protocol = new DataTransferProtocol() {
        @Override
        public String transferType() { return "TestType"; }
        @Override
        public CompletableFuture<DataFlowResult> execute(DataFlow dataFlow) {
            return CompletableFuture.completedFuture(DataFlowResult.success(dataFlow.getDataFlowId()));
        }
        @Override
        public void suspend(DataFlow dataFlow) {}
        @Override
        public void resume(DataFlow dataFlow) {}
        @Override
        public void terminate(DataFlow dataFlow) {}
    };

    @Test
    void transferTypeReturnsRegisteredType() {
        assertThat(protocol.transferType()).isEqualTo("TestType");
    }

    @Test
    void executeCompletesSuccessfully() throws Exception {
        DataFlow flow = DataFlow.Builder.newInstance()
            .dataFlowId("df-1")
            .processId("tp-1")
            .transferType("TestType")
            .build();
        DataFlowResult result = protocol.execute(flow).get();
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getDataFlowId()).isEqualTo("df-1");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
mvn -pl data-plane-api test -Dtest=DataTransferProtocolTest
```

Expected: FAIL — `DataTransferProtocol`, `DataFlow`, `DataFlowResult` not found.

- [ ] **Step 3: Create `DataFlowState`**

```java
// data-plane-api/src/main/java/it/eng/dataplane/api/model/DataFlowState.java
package it.eng.dataplane.api.model;

public enum DataFlowState {
    INITIALIZED, PREPARING, PREPARED, STARTING, STARTED, SUSPENDED, COMPLETED, TERMINATED
}
```

- [ ] **Step 4: Create `DataFlow`**

```java
// data-plane-api/src/main/java/it/eng/dataplane/api/model/DataFlow.java
package it.eng.dataplane.api.model;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.ValidationException;
import jakarta.validation.constraints.NotBlank;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class DataFlow {

    @NotBlank(message = "must not be blank")
    private String dataFlowId;
    /** Correlates to TransferProcess.id on the Control Plane */
    @NotBlank(message = "must not be blank")
    private String processId;
    private String agreementId;
    private String datasetId;
    @NotBlank(message = "must not be blank")
    private String transferType;
    private String callbackAddress;
    private DataFlowState state;
    private Map<String, String> dataAddress;
    private String tenantId;
    private String participantId;
    private String counterPartyId;
    private String error;
    private Instant createdAt;
    private Instant updatedAt;

    public static class Builder {
        private final DataFlow instance = new DataFlow();

        private Builder() {}

        public static Builder newInstance() { return new Builder(); }

        public Builder dataFlowId(String id) { instance.dataFlowId = id; return this; }
        public Builder processId(String id) { instance.processId = id; return this; }
        public Builder agreementId(String id) { instance.agreementId = id; return this; }
        public Builder datasetId(String id) { instance.datasetId = id; return this; }
        public Builder transferType(String type) { instance.transferType = type; return this; }
        public Builder callbackAddress(String addr) { instance.callbackAddress = addr; return this; }
        public Builder state(DataFlowState state) { instance.state = state; return this; }
        public Builder dataAddress(Map<String, String> addr) { instance.dataAddress = addr; return this; }
        public Builder tenantId(String id) { instance.tenantId = id; return this; }
        public Builder participantId(String id) { instance.participantId = id; return this; }
        public Builder counterPartyId(String id) { instance.counterPartyId = id; return this; }
        public Builder error(String error) { instance.error = error; return this; }

        public DataFlow build() {
            if (instance.createdAt == null) instance.createdAt = Instant.now();
            if (instance.state == null) instance.state = DataFlowState.INITIALIZED;
            Set<ConstraintViolation<DataFlow>> violations =
                Validation.buildDefaultValidatorFactory().getValidator().validate(instance);
            if (violations.isEmpty()) {
                return instance;
            }
            throw new ValidationException("DataFlow - " +
                violations.stream()
                    .map(v -> v.getPropertyPath() + " " + v.getMessage())
                    .collect(Collectors.joining(",")));
        }
    }
}
```

- [ ] **Step 5: Create `DataFlowResult`**

```java
// data-plane-api/src/main/java/it/eng/dataplane/api/model/DataFlowResult.java
package it.eng.dataplane.api.model;

import lombok.Getter;

@Getter
public class DataFlowResult {

    private final String dataFlowId;
    private final boolean success;
    private final String errorMessage;
    private final java.util.Map<String, String> dataAddress;

    private DataFlowResult(String dataFlowId, boolean success, String errorMessage,
                           java.util.Map<String, String> dataAddress) {
        this.dataFlowId = dataFlowId;
        this.success = success;
        this.errorMessage = errorMessage;
        this.dataAddress = dataAddress;
    }

    /** Factory: successful transfer with optional data address (e.g. presigned URL for PULL). */
    public static DataFlowResult success(String dataFlowId) {
        return new DataFlowResult(dataFlowId, true, null, null);
    }

    public static DataFlowResult success(String dataFlowId, java.util.Map<String, String> dataAddress) {
        return new DataFlowResult(dataFlowId, true, null, dataAddress);
    }

    /** Factory: failed transfer. */
    public static DataFlowResult failure(String dataFlowId, String errorMessage) {
        return new DataFlowResult(dataFlowId, false, errorMessage, null);
    }
}
```

- [ ] **Step 5b: Add DPS constants to `DSpaceConstants`**

In `tools/src/main/java/it/eng/tools/model/DSpaceConstants.java`, add after the existing `DATA_ADDRESS` constant:

```java
public static final String AGREEMENT_ID = "agreementId";  // already exists — verify
public static final String COUNTER_PARTY_ID = "counterPartyId";
public static final String DATASET_ID = "datasetId";
public static final String DATASPACE_CONTEXT = "dataspaceContext";
public static final String DATA_FLOW_ID = "dataFlowId";
public static final String MESSAGE_ID = "messageId";
public static final String PROCESS_ID = "processId";
public static final String TRANSFER_TYPE = "transferType";
public static final String CLAIMS = "claims";
```

> Check the file before adding — `AGREEMENT_ID` may already exist. Only add missing ones.

- [ ] **Step 6: Create DPS message classes**

All three follow the same pattern as `ContractRequestMessage`:
- `@JsonDeserialize(builder = ClassName.Builder.class)` on the class
- Custom inner `Builder` with `@JsonPOJOBuilder(withPrefix = "")` + `@JsonIgnoreProperties(ignoreUnknown = true)`
- `@NotNull` on required fields
- `try (ValidatorFactory factory = ...)` in `build()`
- `getType()` method with `@JsonProperty(READ_ONLY)`
- **No Lombok `@Builder` or `@Jacksonized`**

```java
// data-plane-api/src/main/java/it/eng/dataplane/api/message/DataFlowStartMessage.java
package it.eng.dataplane.api.message;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonProperty.Access;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import it.eng.tools.model.DSpaceConstants;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.ValidationException;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@JsonDeserialize(builder = DataFlowStartMessage.Builder.class)
public class DataFlowStartMessage {

    @NotNull
    @JsonProperty(value = DSpaceConstants.CONTEXT, access = Access.READ_ONLY)
    private List<String> context = List.of(DSpaceConstants.DSPACE_2025_01_CONTEXT);

    @NotNull
    private String processId;

    @NotNull
    private String transferType;

    private String messageId;
    private String participantId;
    private String counterPartyId;
    private String dataspaceContext;
    private String agreementId;
    private String datasetId;
    private String callbackAddress;
    private Map<String, String> dataAddress;
    private Map<String, String> claims;

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Builder {

        private final DataFlowStartMessage message;

        private Builder() {
            message = new DataFlowStartMessage();
        }

        public static Builder newInstance() {
            return new Builder();
        }

        public Builder processId(String processId) {
            message.processId = processId;
            return this;
        }

        public Builder transferType(String transferType) {
            message.transferType = transferType;
            return this;
        }

        public Builder messageId(String messageId) {
            message.messageId = messageId;
            return this;
        }

        public Builder participantId(String participantId) {
            message.participantId = participantId;
            return this;
        }

        public Builder counterPartyId(String counterPartyId) {
            message.counterPartyId = counterPartyId;
            return this;
        }

        public Builder dataspaceContext(String dataspaceContext) {
            message.dataspaceContext = dataspaceContext;
            return this;
        }

        public Builder agreementId(String agreementId) {
            message.agreementId = agreementId;
            return this;
        }

        public Builder datasetId(String datasetId) {
            message.datasetId = datasetId;
            return this;
        }

        public Builder callbackAddress(String callbackAddress) {
            message.callbackAddress = callbackAddress;
            return this;
        }

        public Builder dataAddress(Map<String, String> dataAddress) {
            message.dataAddress = dataAddress;
            return this;
        }

        public Builder claims(Map<String, String> claims) {
            message.claims = claims;
            return this;
        }

        public DataFlowStartMessage build() {
            Set<ConstraintViolation<DataFlowStartMessage>> violations;
            try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
                violations = factory.getValidator().validate(message);
            }
            List<String> messages = violations.stream()
                .map(v -> v.getPropertyPath() + " " + v.getMessage())
                .collect(Collectors.toList());
            if (messages.isEmpty()) {
                return message;
            }
            throw new ValidationException("DataFlowStartMessage - " + String.join(", ", messages));
        }
    }

    @JsonProperty(value = DSpaceConstants.TYPE, access = JsonProperty.Access.READ_ONLY)
    public String getType() {
        return DataFlowStartMessage.class.getSimpleName();
    }
}
```

```java
// data-plane-api/src/main/java/it/eng/dataplane/api/message/DataFlowPrepareMessage.java
package it.eng.dataplane.api.message;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonProperty.Access;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import it.eng.tools.model.DSpaceConstants;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.ValidationException;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@JsonDeserialize(builder = DataFlowPrepareMessage.Builder.class)
public class DataFlowPrepareMessage {

    @NotNull
    @JsonProperty(value = DSpaceConstants.CONTEXT, access = Access.READ_ONLY)
    private List<String> context = List.of(DSpaceConstants.DSPACE_2025_01_CONTEXT);

    @NotNull
    private String processId;

    @NotNull
    private String transferType;

    private String messageId;
    private String participantId;
    private String counterPartyId;
    private String dataspaceContext;
    private String agreementId;
    private String datasetId;
    private String callbackAddress;
    private Map<String, String> dataAddress;
    private Map<String, String> claims;

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Builder {

        private final DataFlowPrepareMessage message;

        private Builder() {
            message = new DataFlowPrepareMessage();
        }

        public static Builder newInstance() {
            return new Builder();
        }

        public Builder processId(String processId) {
            message.processId = processId;
            return this;
        }

        public Builder transferType(String transferType) {
            message.transferType = transferType;
            return this;
        }

        public Builder messageId(String messageId) {
            message.messageId = messageId;
            return this;
        }

        public Builder participantId(String participantId) {
            message.participantId = participantId;
            return this;
        }

        public Builder counterPartyId(String counterPartyId) {
            message.counterPartyId = counterPartyId;
            return this;
        }

        public Builder dataspaceContext(String dataspaceContext) {
            message.dataspaceContext = dataspaceContext;
            return this;
        }

        public Builder agreementId(String agreementId) {
            message.agreementId = agreementId;
            return this;
        }

        public Builder datasetId(String datasetId) {
            message.datasetId = datasetId;
            return this;
        }

        public Builder callbackAddress(String callbackAddress) {
            message.callbackAddress = callbackAddress;
            return this;
        }

        public Builder dataAddress(Map<String, String> dataAddress) {
            message.dataAddress = dataAddress;
            return this;
        }

        public Builder claims(Map<String, String> claims) {
            message.claims = claims;
            return this;
        }

        public DataFlowPrepareMessage build() {
            Set<ConstraintViolation<DataFlowPrepareMessage>> violations;
            try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
                violations = factory.getValidator().validate(message);
            }
            List<String> messages = violations.stream()
                .map(v -> v.getPropertyPath() + " " + v.getMessage())
                .collect(Collectors.toList());
            if (messages.isEmpty()) {
                return message;
            }
            throw new ValidationException("DataFlowPrepareMessage - " + String.join(", ", messages));
        }
    }

    @JsonProperty(value = DSpaceConstants.TYPE, access = JsonProperty.Access.READ_ONLY)
    public String getType() {
        return DataFlowPrepareMessage.class.getSimpleName();
    }
}
```

```java
// data-plane-api/src/main/java/it/eng/dataplane/api/message/DataFlowStatusMessage.java
package it.eng.dataplane.api.message;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonProperty.Access;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import it.eng.dataplane.api.model.DataFlowState;
import it.eng.tools.model.DSpaceConstants;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.ValidationException;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@JsonDeserialize(builder = DataFlowStatusMessage.Builder.class)
public class DataFlowStatusMessage {

    @NotNull
    @JsonProperty(value = DSpaceConstants.CONTEXT, access = Access.READ_ONLY)
    private List<String> context = List.of(DSpaceConstants.DSPACE_2025_01_CONTEXT);

    @NotNull
    private String dataFlowId;

    @NotNull
    private String processId;

    @NotNull
    private DataFlowState state;

    private Map<String, String> dataAddress;
    private String error;

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Builder {

        private final DataFlowStatusMessage message;

        private Builder() {
            message = new DataFlowStatusMessage();
        }

        public static Builder newInstance() {
            return new Builder();
        }

        public Builder dataFlowId(String dataFlowId) {
            message.dataFlowId = dataFlowId;
            return this;
        }

        public Builder processId(String processId) {
            message.processId = processId;
            return this;
        }

        public Builder state(DataFlowState state) {
            message.state = state;
            return this;
        }

        public Builder dataAddress(Map<String, String> dataAddress) {
            message.dataAddress = dataAddress;
            return this;
        }

        public Builder error(String error) {
            message.error = error;
            return this;
        }

        public DataFlowStatusMessage build() {
            Set<ConstraintViolation<DataFlowStatusMessage>> violations;
            try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
                violations = factory.getValidator().validate(message);
            }
            List<String> messages = violations.stream()
                .map(v -> v.getPropertyPath() + " " + v.getMessage())
                .collect(Collectors.toList());
            if (messages.isEmpty()) {
                return message;
            }
            throw new ValidationException("DataFlowStatusMessage - " + String.join(", ", messages));
        }
    }

    @JsonProperty(value = DSpaceConstants.TYPE, access = JsonProperty.Access.READ_ONLY)
    public String getType() {
        return DataFlowStatusMessage.class.getSimpleName();
    }
}
```

- [ ] **Step 7: Create `DataTransferProtocol` interface**

```java
// data-plane-api/src/main/java/it/eng/dataplane/api/DataTransferProtocol.java
package it.eng.dataplane.api;

import it.eng.dataplane.api.model.DataFlow;
import it.eng.dataplane.api.model.DataFlowResult;
import java.util.concurrent.CompletableFuture;

/**
 * SPI for Data Plane transfer protocol implementations.
 * Each implementation handles one transfer type (e.g. "HttpData-PULL").
 * Implementations are Spring beans discovered by DataTransferProtocolRegistry.
 */
public interface DataTransferProtocol {

    /**
     * Returns the transfer type string this implementation handles (e.g. "HttpData-PULL").
     *
     * @return transfer type identifier
     */
    String transferType();

    /**
     * Executes the data transfer asynchronously.
     * Implementations must call back the Control Plane via ControlPlaneClient on completion or failure.
     *
     * @param dataFlow the data flow to execute
     * @return future completing with the transfer result
     */
    CompletableFuture<DataFlowResult> execute(DataFlow dataFlow);

    /**
     * Suspends an in-progress transfer.
     *
     * @param dataFlow the data flow to suspend
     */
    void suspend(DataFlow dataFlow);

    /**
     * Resumes a suspended transfer.
     *
     * @param dataFlow the data flow to resume
     */
    void resume(DataFlow dataFlow);

    /**
     * Terminates a transfer, releasing all resources.
     *
     * @param dataFlow the data flow to terminate
     */
    void terminate(DataFlow dataFlow);
}
```

- [ ] **Step 8: Run test to verify it passes**

```bash
mvn -pl data-plane-api test -Dtest=DataTransferProtocolTest
```

Expected: `BUILD SUCCESS`, 2 tests pass.

- [ ] **Step 8a: Write serialization, deserialization and validation tests for model and message classes**

```java
// data-plane-api/src/test/java/it/eng/dataplane/api/model/DataFlowTest.java
package it.eng.dataplane.api.model;

import jakarta.validation.ValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class DataFlowTest {

    @Test
    @DisplayName("Build with required fields succeeds")
    void buildWithRequiredFieldsSucceeds() {
        DataFlow flow = DataFlow.Builder.newInstance()
            .processId("proc-1")
            .transferType("HttpData-PULL")
            .build();
        assertThat(flow.getProcessId()).isEqualTo("proc-1");
        assertThat(flow.getTransferType()).isEqualTo("HttpData-PULL");
    }

    @Test
    @DisplayName("Missing processId throws ValidationException")
    void missingProcessIdThrowsValidation() {
        assertThrows(ValidationException.class, () ->
            DataFlow.Builder.newInstance()
                .transferType("HttpData-PULL")
                .build());
    }

    @Test
    @DisplayName("Missing transferType throws ValidationException")
    void missingTransferTypeThrowsValidation() {
        assertThrows(ValidationException.class, () ->
            DataFlow.Builder.newInstance()
                .processId("proc-1")
                .build());
    }

    @Test
    @DisplayName("Empty builder throws ValidationException")
    void emptyBuilderThrowsValidation() {
        assertThrows(ValidationException.class, () ->
            DataFlow.Builder.newInstance().build());
    }
}
```

```java
// data-plane-api/src/test/java/it/eng/dataplane/api/message/DataFlowStartMessageTest.java
package it.eng.dataplane.api.message;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.eng.tools.model.DSpaceConstants;
import jakarta.validation.ValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class DataFlowStartMessageTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    @DisplayName("Serialization includes processId, transferType, @context and @type")
    void serializationIncludesExpectedFields() throws Exception {
        DataFlowStartMessage msg = DataFlowStartMessage.Builder.newInstance()
            .processId("proc-1")
            .transferType("HttpData-PULL")
            .build();
        String json = MAPPER.writeValueAsString(msg);
        assertTrue(json.contains("proc-1"));
        assertTrue(json.contains("HttpData-PULL"));
        assertTrue(json.contains(DSpaceConstants.CONTEXT));
        assertTrue(json.contains(DSpaceConstants.DSPACE_2025_01_CONTEXT));
        assertTrue(json.contains(DSpaceConstants.TYPE));
    }

    @Test
    @DisplayName("Deserialization round-trip preserves all fields")
    void roundTripPreservesAllFields() throws Exception {
        DataFlowStartMessage original = DataFlowStartMessage.Builder.newInstance()
            .messageId("msg-1")
            .processId("proc-1")
            .transferType("HttpData-PULL")
            .agreementId("agr-1")
            .datasetId("ds-1")
            .callbackAddress("http://cp:8080/tenant1/transfers")
            .build();
        String json = MAPPER.writeValueAsString(original);
        DataFlowStartMessage restored = MAPPER.readValue(json, DataFlowStartMessage.class);
        assertThat(original).usingRecursiveComparison().isEqualTo(restored);
    }

    @Test
    @DisplayName("Missing processId throws ValidationException")
    void missingProcessIdThrowsValidation() {
        assertThrows(ValidationException.class, () ->
            DataFlowStartMessage.Builder.newInstance()
                .transferType("HttpData-PULL")
                .build());
    }

    @Test
    @DisplayName("Missing transferType throws ValidationException")
    void missingTransferTypeThrowsValidation() {
        assertThrows(ValidationException.class, () ->
            DataFlowStartMessage.Builder.newInstance()
                .processId("proc-1")
                .build());
    }

    @Test
    @DisplayName("Empty builder throws ValidationException")
    void emptyBuilderThrowsValidation() {
        assertThrows(ValidationException.class, () ->
            DataFlowStartMessage.Builder.newInstance().build());
    }
}
```

```java
// data-plane-api/src/test/java/it/eng/dataplane/api/message/DataFlowPrepareMessageTest.java
package it.eng.dataplane.api.message;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.eng.tools.model.DSpaceConstants;
import jakarta.validation.ValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class DataFlowPrepareMessageTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    @DisplayName("Serialization includes processId, transferType, @context and @type")
    void serializationIncludesExpectedFields() throws Exception {
        DataFlowPrepareMessage msg = DataFlowPrepareMessage.Builder.newInstance()
            .processId("proc-1")
            .transferType("HttpData-PUSH")
            .build();
        String json = MAPPER.writeValueAsString(msg);
        assertTrue(json.contains("proc-1"));
        assertTrue(json.contains("HttpData-PUSH"));
        assertTrue(json.contains(DSpaceConstants.CONTEXT));
        assertTrue(json.contains(DSpaceConstants.DSPACE_2025_01_CONTEXT));
        assertTrue(json.contains(DSpaceConstants.TYPE));
    }

    @Test
    @DisplayName("Deserialization round-trip preserves all fields")
    void roundTripPreservesAllFields() throws Exception {
        DataFlowPrepareMessage original = DataFlowPrepareMessage.Builder.newInstance()
            .messageId("msg-1")
            .processId("proc-1")
            .transferType("HttpData-PUSH")
            .agreementId("agr-1")
            .datasetId("ds-1")
            .callbackAddress("http://cp:8080/tenant1/transfers")
            .build();
        String json = MAPPER.writeValueAsString(original);
        DataFlowPrepareMessage restored = MAPPER.readValue(json, DataFlowPrepareMessage.class);
        assertThat(original).usingRecursiveComparison().isEqualTo(restored);
    }

    @Test
    @DisplayName("Missing processId throws ValidationException")
    void missingProcessIdThrowsValidation() {
        assertThrows(ValidationException.class, () ->
            DataFlowPrepareMessage.Builder.newInstance()
                .transferType("HttpData-PUSH")
                .build());
    }

    @Test
    @DisplayName("Missing transferType throws ValidationException")
    void missingTransferTypeThrowsValidation() {
        assertThrows(ValidationException.class, () ->
            DataFlowPrepareMessage.Builder.newInstance()
                .processId("proc-1")
                .build());
    }

    @Test
    @DisplayName("Empty builder throws ValidationException")
    void emptyBuilderThrowsValidation() {
        assertThrows(ValidationException.class, () ->
            DataFlowPrepareMessage.Builder.newInstance().build());
    }
}
```

```java
// data-plane-api/src/test/java/it/eng/dataplane/api/message/DataFlowStatusMessageTest.java
package it.eng.dataplane.api.message;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.eng.dataplane.api.model.DataFlowState;
import it.eng.tools.model.DSpaceConstants;
import jakarta.validation.ValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class DataFlowStatusMessageTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    @DisplayName("Serialization includes processId, state, @context and @type")
    void serializationIncludesExpectedFields() throws Exception {
        DataFlowStatusMessage msg = DataFlowStatusMessage.Builder.newInstance()
            .processId("proc-1")
            .state(DataFlowState.COMPLETED)
            .build();
        String json = MAPPER.writeValueAsString(msg);
        assertTrue(json.contains("proc-1"));
        assertTrue(json.contains("COMPLETED"));
        assertTrue(json.contains(DSpaceConstants.CONTEXT));
        assertTrue(json.contains(DSpaceConstants.DSPACE_2025_01_CONTEXT));
        assertTrue(json.contains(DSpaceConstants.TYPE));
    }

    @Test
    @DisplayName("Deserialization round-trip preserves all fields including dataAddress")
    void roundTripPreservesAllFields() throws Exception {
        DataFlowStatusMessage original = DataFlowStatusMessage.Builder.newInstance()
            .dataFlowId("df-1")
            .processId("proc-1")
            .state(DataFlowState.STARTED)
            .dataAddress(Map.of("endpoint", "https://example.com/file"))
            .build();
        String json = MAPPER.writeValueAsString(original);
        DataFlowStatusMessage restored = MAPPER.readValue(json, DataFlowStatusMessage.class);
        assertThat(original).usingRecursiveComparison().isEqualTo(restored);
    }

    @Test
    @DisplayName("Missing processId throws ValidationException")
    void missingProcessIdThrowsValidation() {
        assertThrows(ValidationException.class, () ->
            DataFlowStatusMessage.Builder.newInstance()
                .state(DataFlowState.COMPLETED)
                .build());
    }

    @Test
    @DisplayName("Missing state throws ValidationException")
    void missingStateThrowsValidation() {
        assertThrows(ValidationException.class, () ->
            DataFlowStatusMessage.Builder.newInstance()
                .processId("proc-1")
                .build());
    }

    @Test
    @DisplayName("Empty builder throws ValidationException")
    void emptyBuilderThrowsValidation() {
        assertThrows(ValidationException.class, () ->
            DataFlowStatusMessage.Builder.newInstance().build());
    }
}
```

- [ ] **Step 8b: Run all model and message tests**

```bash
mvn -pl data-plane-api test -Dtest="DataTransferProtocolTest,DataFlowTest,DataFlowStartMessageTest,DataFlowPrepareMessageTest,DataFlowStatusMessageTest"
```

Expected: `BUILD SUCCESS`, 17 tests pass.

- [ ] **Step 9: Commit**

```bash
git add data-plane-api/
git commit -m "feat(data-plane-api): add DataTransferProtocol SPI, DataFlow model, DPS message classes"
```

---
### Task 3: `data-plane-core` — shared runtime library

**Files (all new):**
- `data-plane-core/src/main/java/it/eng/dataplane/core/model/DataFlowEntity.java`
- `data-plane-core/src/main/java/it/eng/dataplane/core/repository/DataFlowRepository.java`
- `data-plane-core/src/main/java/it/eng/dataplane/core/service/DataFlowService.java`
- `data-plane-core/src/main/java/it/eng/dataplane/core/controller/DataFlowController.java`
- `data-plane-core/src/main/java/it/eng/dataplane/core/controller/ControlPlaneRegistrationController.java`
- `data-plane-core/src/main/java/it/eng/dataplane/core/client/ControlPlaneClient.java`
- `data-plane-core/src/main/java/it/eng/dataplane/core/registry/DataTransferProtocolRegistry.java`
- `data-plane-core/src/main/java/it/eng/dataplane/core/security/DataPlaneSecurityConfig.java`
- `data-plane-core/src/main/java/it/eng/dataplane/core/config/DataPlaneProperties.java`
- `data-plane-core/src/main/java/it/eng/dataplane/core/startup/ControlPlaneRegistrationBean.java`
- Tests: `data-plane-core/src/test/java/it/eng/dataplane/core/service/DataFlowServiceTest.java`
- Tests: `data-plane-core/src/test/java/it/eng/dataplane/core/registry/DataTransferProtocolRegistryTest.java`

- [ ] **Step 1: Write failing tests for DataFlowService**

```java
// data-plane-core/src/test/java/it/eng/dataplane/core/service/DataFlowServiceTest.java
package it.eng.dataplane.core.service;

import it.eng.dataplane.api.DataTransferProtocol;
import it.eng.dataplane.api.model.DataFlow;
import it.eng.dataplane.api.model.DataFlowResult;
import it.eng.dataplane.api.model.DataFlowState;
import it.eng.dataplane.core.model.DataFlowEntity;
import it.eng.dataplane.core.registry.DataTransferProtocolRegistry;
import it.eng.dataplane.core.repository.DataFlowRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DataFlowServiceTest {

    @Mock DataFlowRepository repository;
    @Mock DataTransferProtocolRegistry registry;
    @Mock DataTransferProtocol protocol;
    @InjectMocks DataFlowService service;

    private DataFlow dataFlow;

    @BeforeEach
    void setUp() {
        dataFlow = DataFlow.Builder.newInstance()
            .dataFlowId("df-1")
            .processId("tp-1")
            .transferType("HttpData-PULL")
            .callbackAddress("http://cp:8080")
            .build();
    }

    @Test
    void startDelegatesToProtocol() {
        when(registry.getProtocol("HttpData-PULL")).thenReturn(protocol);
        when(protocol.execute(any())).thenReturn(CompletableFuture.completedFuture(
            DataFlowResult.success("df-1")));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.start(dataFlow);

        verify(repository, atLeastOnce()).save(any());
        verify(protocol).execute(any());
    }

    @Test
    void startThrowsWhenProtocolNotFound() {
        when(registry.getProtocol("HttpData-PULL")).thenReturn(null);

        assertThatThrownBy(() -> service.start(dataFlow))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("HttpData-PULL");
    }

    @Test
    void startThrowsOnDuplicateProcessId() {
        when(repository.findByProcessId("tp-1")).thenReturn(Optional.of(new DataFlowEntity()));

        assertThatThrownBy(() -> service.start(dataFlow))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("tp-1");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
mvn -pl data-plane-core test -Dtest=DataFlowServiceTest
```

Expected: FAIL — `DataFlowService`, `DataFlowEntity`, `DataFlowRepository`, `DataTransferProtocolRegistry` not found.

- [ ] **Step 3: Create `DataFlowEntity`**

```java
// data-plane-core/src/main/java/it/eng/dataplane/core/model/DataFlowEntity.java
package it.eng.dataplane.core.model;

import it.eng.dataplane.api.model.DataFlowState;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;
import java.util.Map;

@Getter
@Setter
@Document("data_flows")
public class DataFlowEntity {

    @Id
    private String id;

    @Indexed(unique = true)
    private String processId;

    private String agreementId;
    private String datasetId;
    private String transferType;
    private String callbackAddress;
    private DataFlowState state;
    private Map<String, String> dataAddress;
    private String tenantId;
    private String participantId;
    private String counterPartyId;
    private String error;
    private Instant createdAt;
    private Instant updatedAt;
}
```

- [ ] **Step 4: Create `DataFlowRepository`**

```java
// data-plane-core/src/main/java/it/eng/dataplane/core/repository/DataFlowRepository.java
package it.eng.dataplane.core.repository;

import it.eng.dataplane.core.model.DataFlowEntity;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.Optional;

public interface DataFlowRepository extends MongoRepository<DataFlowEntity, String> {

    /**
     * Finds a data flow by its Control Plane transfer process ID.
     *
     * @param processId the transfer process ID
     * @return optional entity
     */
    Optional<DataFlowEntity> findByProcessId(String processId);
}
```

- [ ] **Step 5: Create `DataTransferProtocolRegistry`**

```java
// data-plane-core/src/main/java/it/eng/dataplane/core/registry/DataTransferProtocolRegistry.java
package it.eng.dataplane.core.registry;

import it.eng.dataplane.api.DataTransferProtocol;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Discovers all {@link DataTransferProtocol} beans in the application context
 * and provides lookup by transfer type string.
 */
@Slf4j
@Component
public class DataTransferProtocolRegistry {

    private final Map<String, DataTransferProtocol> protocols;

    public DataTransferProtocolRegistry(List<DataTransferProtocol> protocols) {
        this.protocols = protocols.stream()
            .collect(Collectors.toMap(DataTransferProtocol::transferType, Function.identity()));
        log.info("Registered transfer protocols: {}", this.protocols.keySet());
    }

    /**
     * Returns the protocol implementation for the given transfer type.
     *
     * @param transferType e.g. "HttpData-PULL"
     * @return protocol instance or null if not found
     */
    public DataTransferProtocol getProtocol(String transferType) {
        return protocols.get(transferType);
    }

    /**
     * Returns all registered transfer type strings.
     *
     * @return set of transfer type identifiers
     */
    public java.util.Set<String> getSupportedTransferTypes() {
        return protocols.keySet();
    }
}
```

- [ ] **Step 6: Create `DataFlowService`**

```java
// data-plane-core/src/main/java/it/eng/dataplane/core/service/DataFlowService.java
package it.eng.dataplane.core.service;

import it.eng.dataplane.api.DataTransferProtocol;
import it.eng.dataplane.api.model.DataFlow;
import it.eng.dataplane.api.model.DataFlowResult;
import it.eng.dataplane.api.model.DataFlowState;
import it.eng.dataplane.core.client.ControlPlaneClient;
import it.eng.dataplane.core.model.DataFlowEntity;
import it.eng.dataplane.core.registry.DataTransferProtocolRegistry;
import it.eng.dataplane.core.repository.DataFlowRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DataFlowService {

    private final DataFlowRepository repository;
    private final DataTransferProtocolRegistry registry;
    private final ControlPlaneClient controlPlaneClient;

    /**
     * Starts a data flow for the given DataFlow request.
     * Persists the entity, delegates to the matching protocol, and sends callback on completion.
     *
     * @param dataFlow the data flow to start
     * @throws IllegalArgumentException if no protocol supports the transfer type
     * @throws IllegalStateException if a flow with the same processId already exists
     */
    public void start(DataFlow dataFlow) {
        repository.findByProcessId(dataFlow.getProcessId()).ifPresent(existing -> {
            throw new IllegalStateException("DataFlow with processId " + dataFlow.getProcessId() + " already exists");
        });

        DataTransferProtocol protocol = registry.getProtocol(dataFlow.getTransferType());
        if (protocol == null) {
            throw new IllegalArgumentException("No protocol registered for transferType: " + dataFlow.getTransferType());
        }

        DataFlowEntity entity = toEntity(dataFlow, DataFlowState.STARTING);
        repository.save(entity);

        protocol.execute(dataFlow)
            .thenAccept(result -> handleCompletion(entity, result))
            .exceptionally(ex -> { handleError(entity, ex); return null; });
    }

    /**
     * Prepares a data flow (HTTP-PUSH: consumer creates temp credentials).
     *
     * @param dataFlow the data flow to prepare
     */
    public void prepare(DataFlow dataFlow) {
        repository.findByProcessId(dataFlow.getProcessId()).ifPresent(existing -> {
            throw new IllegalStateException("DataFlow with processId " + dataFlow.getProcessId() + " already exists");
        });

        DataTransferProtocol protocol = registry.getProtocol(dataFlow.getTransferType());
        if (protocol == null) {
            throw new IllegalArgumentException("No protocol registered for transferType: " + dataFlow.getTransferType());
        }

        DataFlowEntity entity = toEntity(dataFlow, DataFlowState.PREPARING);
        repository.save(entity);

        protocol.execute(dataFlow)
            .thenAccept(result -> handlePrepared(entity, result))
            .exceptionally(ex -> { handleError(entity, ex); return null; });
    }

    private void handleCompletion(DataFlowEntity entity, DataFlowResult result) {
        if (result.isSuccess()) {
            updateState(entity, DataFlowState.COMPLETED);
            controlPlaneClient.sendStatus(entity.getCallbackAddress(), entity.getProcessId(),
                DataFlowState.COMPLETED, result.getDataAddress(), null);
        } else {
            handleError(entity, new RuntimeException(result.getErrorMessage()));
        }
    }

    private void handlePrepared(DataFlowEntity entity, DataFlowResult result) {
        if (result.isSuccess()) {
            updateState(entity, DataFlowState.PREPARED);
            controlPlaneClient.sendStatus(entity.getCallbackAddress(), entity.getProcessId(),
                DataFlowState.PREPARED, result.getDataAddress(), null);
        } else {
            handleError(entity, new RuntimeException(result.getErrorMessage()));
        }
    }

    private void handleError(DataFlowEntity entity, Throwable ex) {
        log.error("DataFlow {} failed: {}", entity.getId(), ex.getMessage(), ex);
        updateState(entity, DataFlowState.TERMINATED);
        controlPlaneClient.sendStatus(entity.getCallbackAddress(), entity.getProcessId(),
            DataFlowState.TERMINATED, null, ex.getMessage());
    }

    private void updateState(DataFlowEntity entity, DataFlowState state) {
        entity.setState(state);
        entity.setUpdatedAt(Instant.now());
        repository.save(entity);
    }

    private DataFlowEntity toEntity(DataFlow dataFlow, DataFlowState state) {
        DataFlowEntity entity = new DataFlowEntity();
        entity.setId(dataFlow.getDataFlowId() != null ? dataFlow.getDataFlowId() : UUID.randomUUID().toString());
        entity.setProcessId(dataFlow.getProcessId());
        entity.setAgreementId(dataFlow.getAgreementId());
        entity.setDatasetId(dataFlow.getDatasetId());
        entity.setTransferType(dataFlow.getTransferType());
        entity.setCallbackAddress(dataFlow.getCallbackAddress());
        entity.setState(state);
        entity.setDataAddress(dataFlow.getDataAddress());
        entity.setTenantId(dataFlow.getTenantId());
        entity.setParticipantId(dataFlow.getParticipantId());
        entity.setCounterPartyId(dataFlow.getCounterPartyId());
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());
        return entity;
    }
}
```

- [ ] **Step 7: Create `ControlPlaneClient` stub** (full implementation in Task 12)

```java
// data-plane-core/src/main/java/it/eng/dataplane/core/client/ControlPlaneClient.java
package it.eng.dataplane.core.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.eng.dataplane.api.message.DataFlowStatusMessage;
import it.eng.dataplane.api.model.DataFlowState;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;

/**
 * HTTP client that sends DPS status callbacks from Data Plane to Control Plane.
 * Uses the shared {@link OkHttpClient} bean configured by {@code OkHttpClientConfiguration}
 * in {@code tools}, which supports TLS with a custom truststore ({@code server.ssl.enabled=true})
 * or an insecure noop client ({@code server.ssl.enabled=false}) for development.
 */
@Slf4j
@Component
public class ControlPlaneClient {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient okHttpClient;
    private final ObjectMapper objectMapper;

    /**
     * @param okHttpClient TLS-aware HTTP client from {@code OkHttpClientConfiguration}
     * @param objectMapper shared Jackson mapper
     */
    public ControlPlaneClient(OkHttpClient okHttpClient, ObjectMapper objectMapper) {
        this.okHttpClient = okHttpClient;
        this.objectMapper = objectMapper;
    }

    /**
     * Sends a DataFlowStatusMessage to the Control Plane callback endpoint.
     * Endpoint pattern: {callbackAddress}/{processId}/dataflow/{state-lowercase}
     *
     * @param callbackAddress base callback URL from Control Plane
     * @param processId the TransferProcess ID on the CP
     * @param state the new DataFlow state
     * @param dataAddress optional data address map (e.g. presigned URL for PULL)
     * @param error optional error message for TERMINATED state
     */
    public void sendStatus(String callbackAddress, String processId,
                           DataFlowState state, Map<String, String> dataAddress, String error) {
        String url = callbackAddress + "/" + processId + "/dataflow/" + state.name().toLowerCase();
        DataFlowStatusMessage message = DataFlowStatusMessage.Builder.newInstance()
            .dataFlowId(UUID.randomUUID().toString())
            .processId(processId)
            .state(state)
            .dataAddress(dataAddress)
            .error(error)
            .build();
        try {
            String json = objectMapper.writeValueAsString(message);
            Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(json, JSON))
                .addHeader("Content-Type", "application/json")
                .build();
            try (Response response = okHttpClient.newCall(request).execute()) {
                log.info("Sent {} status for processId={}, HTTP {}", state, processId, response.code());
                if (!response.isSuccessful()) {
                    log.error("CP callback rejected with HTTP {} at {}", response.code(), url);
                }
            }
        } catch (IOException e) {
            log.error("Failed to send {} callback to {}: {}", state, url, e.getMessage());
        }
    }
}
```

- [ ] **Step 8: Create `DataFlowController`**

```java
// data-plane-core/src/main/java/it/eng/dataplane/core/controller/DataFlowController.java
package it.eng.dataplane.core.controller;

import it.eng.dataplane.api.message.DataFlowPrepareMessage;
import it.eng.dataplane.api.message.DataFlowStartMessage;
import it.eng.dataplane.api.model.DataFlow;
import it.eng.dataplane.api.model.DataFlowState;
import it.eng.dataplane.core.service.DataFlowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

/**
 * Receives DPS messages from the Control Plane.
 * All endpoints require authentication (configured in DataPlaneSecurityConfig).
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/dataflows")
public class DataFlowController {

    private final DataFlowService dataFlowService;

    /**
     * Starts a data transfer. Called by CP for HTTP-PULL.
     * Returns 400 if processId already exists.
     *
     * @param message DPS start message
     * @return 201 Created on success
     */
    @PostMapping("/start")
    public ResponseEntity<Void> start(@RequestBody DataFlowStartMessage message) {
        log.info("Received start for processId={}, transferType={}", message.getProcessId(), message.getTransferType());
        try {
            DataFlow dataFlow = toDataFlow(message);
            dataFlowService.start(dataFlow);
            return ResponseEntity.status(HttpStatus.CREATED).build();
        } catch (IllegalStateException e) {
            log.warn("Duplicate processId {}: {}", message.getProcessId(), e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Prepares a data transfer. Called by CP for HTTP-PUSH (consumer side).
     *
     * @param message DPS prepare message
     * @return 201 Created on success
     */
    @PostMapping("/prepare")
    public ResponseEntity<Void> prepare(@RequestBody DataFlowPrepareMessage message) {
        log.info("Received prepare for processId={}, transferType={}", message.getProcessId(), message.getTransferType());
        try {
            DataFlow dataFlow = toDataFlowFromPrepare(message);
            dataFlowService.prepare(dataFlow);
            return ResponseEntity.status(HttpStatus.CREATED).build();
        } catch (IllegalStateException e) {
            log.warn("Duplicate processId {}: {}", message.getProcessId(), e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    private DataFlow toDataFlow(DataFlowStartMessage msg) {
        return DataFlow.Builder.newInstance()
            .dataFlowId(UUID.randomUUID().toString())
            .processId(msg.getProcessId())
            .agreementId(msg.getAgreementId())
            .datasetId(msg.getDatasetId())
            .transferType(msg.getTransferType())
            .callbackAddress(msg.getCallbackAddress())
            .dataAddress(msg.getDataAddress())
            .participantId(msg.getParticipantId())
            .counterPartyId(msg.getCounterPartyId())
            .state(DataFlowState.INITIALIZED)
            .build();
    }

    private DataFlow toDataFlowFromPrepare(DataFlowPrepareMessage msg) {
        return DataFlow.Builder.newInstance()
            .dataFlowId(UUID.randomUUID().toString())
            .processId(msg.getProcessId())
            .agreementId(msg.getAgreementId())
            .datasetId(msg.getDatasetId())
            .transferType(msg.getTransferType())
            .callbackAddress(msg.getCallbackAddress())
            .dataAddress(msg.getDataAddress())
            .participantId(msg.getParticipantId())
            .counterPartyId(msg.getCounterPartyId())
            .state(DataFlowState.INITIALIZED)
            .build();
    }
}
```

- [ ] **Step 9: Create `ControlPlaneRegistrationController`**

```java
// data-plane-core/src/main/java/it/eng/dataplane/core/controller/ControlPlaneRegistrationController.java
package it.eng.dataplane.core.controller;

import it.eng.dataplane.core.config.DataPlaneProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

/**
 * Receives the Control Plane's acknowledgement of this Data Plane's registration.
 * The CP calls PUT /controlplanes after accepting a DP registration.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/controlplanes")
public class ControlPlaneRegistrationController {

    private final DataPlaneProperties properties;

    /**
     * Accepts Control Plane registration confirmation.
     *
     * @param payload registration payload from CP
     * @return 200 OK
     */
    @PutMapping
    public ResponseEntity<Void> registerControlPlane(@RequestBody Map<String, String> payload) {
        log.info("Control Plane registered: endpoint={}", payload.get("endpoint"));
        properties.setControlPlaneEndpoint(payload.get("endpoint"));
        return ResponseEntity.ok().build();
    }
}
```

- [ ] **Step 10: Create `DataPlaneProperties`**

```java
// data-plane-core/src/main/java/it/eng/dataplane/core/config/DataPlaneProperties.java
package it.eng.dataplane.core.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.ArrayList;

/**
 * Configuration properties for a Data Plane instance.
 * Prefix: dataplane
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "dataplane")
public class DataPlaneProperties {

    /** This DP's public endpoint (used for self-registration with CP). */
    private String endpoint;

    /** Control Plane endpoint URL (populated at startup or by PUT /controlplanes). */
    private String controlPlaneEndpoint;

    /** Admin API endpoint on the Control Plane (for registration calls). */
    private String controlPlaneAdminEndpoint;

    /** Auth type for CP↔DP calls: API_KEY or OAUTH2. */
    private String authType = "API_KEY";

    /** API key value (when authType=API_KEY). */
    private String apiKey;
}
```

- [ ] **Step 11: Create `DataPlaneSecurityConfig`**

```java
// data-plane-core/src/main/java/it/eng/dataplane/core/security/DataPlaneSecurityConfig.java
package it.eng.dataplane.core.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Secures Data Plane endpoints.
 * /dataflows/** and /controlplanes require authentication.
 * /actuator/health is public.
 */
@Configuration
@EnableWebSecurity
public class DataPlaneSecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health").permitAll()
                .anyRequest().authenticated()
            )
            .httpBasic(basic -> {});
        return http.build();
    }
}
```

> **Note:** Auth will be extended in Phase 6 (OAuth2 + API Key filter). This stub keeps it simple for now using HTTP Basic.

- [ ] **Step 12: Create `ControlPlaneRegistrationBean`** (startup registration — full retry logic in Task 10)

```java
// data-plane-core/src/main/java/it/eng/dataplane/core/startup/ControlPlaneRegistrationBean.java
package it.eng.dataplane.core.startup;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.eng.dataplane.core.config.DataPlaneProperties;
import it.eng.dataplane.core.registry.DataTransferProtocolRegistry;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.util.Map;

/**
 * Registers this Data Plane with the Control Plane at startup via the DP registration API.
 * Uses the shared {@link OkHttpClient} bean (TLS-aware, configured by {@code OkHttpClientConfiguration}).
 * Retries with exponential backoff are added in Task 10; this stub logs on failure without retry.
 */
@Slf4j
@Component
public class ControlPlaneRegistrationBean implements ApplicationListener<ApplicationReadyEvent> {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final DataPlaneProperties properties;
    private final DataTransferProtocolRegistry registry;
    private final OkHttpClient okHttpClient;
    private final ObjectMapper objectMapper;

    /**
     * @param properties DP runtime configuration
     * @param registry registered transfer protocol implementations
     * @param okHttpClient TLS-aware HTTP client from {@code OkHttpClientConfiguration}
     * @param objectMapper shared Jackson mapper
     */
    public ControlPlaneRegistrationBean(DataPlaneProperties properties,
                                        DataTransferProtocolRegistry registry,
                                        OkHttpClient okHttpClient,
                                        ObjectMapper objectMapper) {
        this.properties = properties;
        this.registry = registry;
        this.okHttpClient = okHttpClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        if (properties.getControlPlaneAdminEndpoint() == null) {
            log.warn("dataplane.control-plane-admin-endpoint not set, skipping CP registration");
            return;
        }
        String url = properties.getControlPlaneAdminEndpoint() + "/api/v1/dataplanes";
        try {
            Map<String, Object> payload = Map.of(
                "endpoint", properties.getEndpoint(),
                "supportedTransferTypes", registry.getSupportedTransferTypes()
            );
            String json = objectMapper.writeValueAsString(payload);
            Request request = new Request.Builder()
                .url(url)
                .put(RequestBody.create(json, JSON))
                .addHeader("Content-Type", "application/json")
                .build();
            try (Response response = okHttpClient.newCall(request).execute()) {
                if (response.isSuccessful()) {
                    log.info("Registered with Control Plane at {}", properties.getControlPlaneAdminEndpoint());
                } else {
                    log.error("CP registration rejected with HTTP {} — will not retry (retry added in Task 10)", response.code());
                }
            }
        } catch (IOException e) {
            log.error("Failed to register with Control Plane at {}: {}", url, e.getMessage());
        }
    }
}
```

- [ ] **Step 13: Run tests to verify they pass**

```bash
mvn -pl data-plane-core test -Dtest="DataFlowServiceTest,DataTransferProtocolRegistryTest"
```

Expected: `BUILD SUCCESS`, all tests pass.

- [ ] **Step 14: Commit**

```bash
git add data-plane-core/
git commit -m "feat(data-plane-core): add DataFlowService, DataFlowController, registry, security, startup bean"
```

---
---

## Phase 2 — Protocol Apps (parallel after Task 3)

> Tasks 4 and 5 can be executed in parallel by two agents.

### Task 4: `data-plane-http-pull` Spring Boot app

**Files:**
- Create: `data-plane-http-pull/src/main/java/it/eng/dataplane/httppull/DataPlaneHttpPullApplication.java`
- Create: `data-plane-http-pull/src/main/java/it/eng/dataplane/httppull/HttpPullTransferProtocol.java`
- Create: `data-plane-http-pull/src/main/java/it/eng/dataplane/httppull/config/HttpPullConfiguration.java`
- Create: `data-plane-http-pull/src/main/resources/application.properties`
- Test: `data-plane-http-pull/src/test/java/it/eng/dataplane/httppull/HttpPullTransferProtocolTest.java`

The logic to migrate here lives in:
`data-transfer/src/main/java/it/eng/datatransfer/service/api/strategy/HttpPullTransferStrategy.java` (lines 82-162 — the `downloadAndUploadToS3()` method with `AtomicReference` connection management).

- [ ] **Step 1: Write failing test**

```java
// data-plane-http-pull/src/test/java/it/eng/dataplane/httppull/HttpPullTransferProtocolTest.java
package it.eng.dataplane.httppull;

import it.eng.dataplane.api.model.DataFlow;
import it.eng.dataplane.api.model.DataFlowResult;
import it.eng.tools.s3.service.S3ClientService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class HttpPullTransferProtocolTest {

    @Mock S3ClientService s3ClientService;
    @InjectMocks HttpPullTransferProtocol protocol;

    @Test
    void transferTypeIsHttpDataPull() {
        assertThat(protocol.transferType()).isEqualTo("HttpData-PULL");
    }

    @Test
    void executeReturnsFailureWhenPresignedUrlMissing() throws Exception {
        DataFlow dataFlow = DataFlow.Builder.newInstance()
            .dataFlowId("df-1")
            .processId("tp-1")
            .transferType("HttpData-PULL")
            .dataAddress(Map.of()) // no endpoint key
            .build();

        DataFlowResult result = protocol.execute(dataFlow).get();

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("endpoint");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
mvn -pl data-plane-http-pull test -Dtest=HttpPullTransferProtocolTest
```

Expected: FAIL — `HttpPullTransferProtocol` not found.

- [ ] **Step 3: Create `DataPlaneHttpPullApplication`**

```java
// data-plane-http-pull/src/main/java/it/eng/dataplane/httppull/DataPlaneHttpPullApplication.java
package it.eng.dataplane.httppull;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"it.eng.dataplane", "it.eng.tools"})
public class DataPlaneHttpPullApplication {

    public static void main(String[] args) {
        SpringApplication.run(DataPlaneHttpPullApplication.class, args);
    }
}
```

- [ ] **Step 4: Create `HttpPullTransferProtocol`**

This is the migration of `HttpPullTransferStrategy.downloadAndUploadToS3()`. Read that file carefully before implementing — the `AtomicReference<HttpURLConnection>` pattern and dynamic timeout calculation must be preserved.

```java
// data-plane-http-pull/src/main/java/it/eng/dataplane/httppull/HttpPullTransferProtocol.java
package it.eng.dataplane.httppull;

import it.eng.dataplane.api.DataTransferProtocol;
import it.eng.dataplane.api.model.DataFlow;
import it.eng.dataplane.api.model.DataFlowResult;
import it.eng.tools.s3.service.S3ClientService;
import it.eng.tools.s3.utils.S3Utils;
import it.eng.tools.service.TenantBucketResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

/**
 * HTTP-PULL transfer protocol implementation.
 * Downloads the artifact from the presigned URL in dataAddress.endpoint
 * and uploads it to the consumer's S3 bucket.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HttpPullTransferProtocol implements DataTransferProtocol {

    private static final String TRANSFER_TYPE = "HttpData-PULL";
    private static final int CHUNK_SIZE = 1024 * 1024; // 1 MB

    private final S3ClientService s3ClientService;
    private final TenantBucketResolver tenantBucketResolver;
    private final Executor transferExecutor;

    @Override
    public String transferType() {
        return TRANSFER_TYPE;
    }

    @Override
    public CompletableFuture<DataFlowResult> execute(DataFlow dataFlow) {
        return CompletableFuture.supplyAsync(() -> {
            String presignedUrl = dataFlow.getDataAddress() != null
                ? dataFlow.getDataAddress().get("endpoint")
                : null;
            if (presignedUrl == null || presignedUrl.isBlank()) {
                return DataFlowResult.failure(dataFlow.getDataFlowId(),
                    "dataAddress.endpoint (presigned URL) is required for HttpData-PULL");
            }
            return downloadAndUploadToS3(dataFlow, presignedUrl);
        }, transferExecutor);
    }

    private DataFlowResult downloadAndUploadToS3(DataFlow dataFlow, String presignedUrl) {
        AtomicReference<HttpURLConnection> connRef = new AtomicReference<>();
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(presignedUrl).openConnection();
            connRef.set(conn);
            conn.setRequestMethod("GET");
            conn.connect();

            long contentLength = conn.getContentLengthLong();
            int readTimeoutMs = contentLength > 0
                ? (int) (Math.ceil(contentLength * 1.1 / (1024 * 1024)) * 1000)
                : 60_000;
            conn.setReadTimeout(readTimeoutMs);

            String bucketName = tenantBucketResolver.resolveBucketName(dataFlow.getTenantId());
            String objectKey = dataFlow.getProcessId();

            Map<String, String> s3Props = buildS3Props(bucketName, objectKey);

            try (InputStream in = conn.getInputStream()) {
                s3ClientService.uploadFile(in, s3Props, "application/octet-stream", null).get();
            }

            log.info("HTTP-PULL transfer complete: processId={}, bucket={}, key={}",
                dataFlow.getProcessId(), bucketName, objectKey);
            return DataFlowResult.success(dataFlow.getDataFlowId());
        } catch (Exception e) {
            log.error("HTTP-PULL transfer failed: processId={}: {}", dataFlow.getProcessId(), e.getMessage(), e);
            return DataFlowResult.failure(dataFlow.getDataFlowId(), e.getMessage());
        } finally {
            HttpURLConnection conn = connRef.get();
            if (conn != null) conn.disconnect();
        }
    }

    private Map<String, String> buildS3Props(String bucketName, String objectKey) {
        // S3 credentials for consumer are read from application.properties / S3Properties bean
        // This is injected via S3ClientService using the consumer's own admin credentials
        Map<String, String> props = new HashMap<>();
        props.put(S3Utils.BUCKET_NAME, bucketName);
        props.put(S3Utils.OBJECT_KEY, objectKey);
        return props;
    }

    @Override
    public void suspend(DataFlow dataFlow) {
        log.warn("Suspend not supported for HttpData-PULL: processId={}", dataFlow.getProcessId());
    }

    @Override
    public void resume(DataFlow dataFlow) {
        log.warn("Resume not supported for HttpData-PULL: processId={}", dataFlow.getProcessId());
    }

    @Override
    public void terminate(DataFlow dataFlow) {
        log.info("Terminate requested for HttpData-PULL: processId={}", dataFlow.getProcessId());
    }
}
```

- [ ] **Step 5: Create `HttpPullConfiguration`**

```java
// data-plane-http-pull/src/main/java/it/eng/dataplane/httppull/config/HttpPullConfiguration.java
package it.eng.dataplane.httppull.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * HTTP-PULL Data Plane configuration.
 * The {@link okhttp3.OkHttpClient} bean used by {@code ControlPlaneClient} and
 * {@code ControlPlaneRegistrationBean} is provided automatically by
 * {@code it.eng.tools.configuration.OkHttpClientConfiguration}, which is component-scanned
 * via {@code @ComponentScan(basePackages = {"it.eng.dataplane", "it.eng.tools"})} in the
 * application class. It reads {@code server.ssl.enabled}: when {@code true} it creates a
 * TLS client with the custom truststore bundle; when {@code false} it creates an insecure
 * client (noop hostname verifier, trust-all) suitable for development.
 */
@Configuration
public class HttpPullConfiguration {

    /**
     * Virtual-thread executor for async HTTP-PULL transfers (Java 21).
     * Each transfer task runs on its own virtual thread — no fixed pool ceiling,
     * blocked I/O does not park OS threads, so thousands of concurrent transfers
     * are practical without tuning thread pool sizes.
     *
     * @return virtual-thread-per-task executor
     */
    @Bean("transferExecutor")
    public Executor transferExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
```

- [ ] **Step 6: Create `application.properties`**

```properties
# data-plane-http-pull/src/main/resources/application.properties
server.port=9090
spring.application.name=data-plane-http-pull

# MongoDB
spring.data.mongodb.uri=mongodb://localhost:27017/data-plane-pull

# Data Plane self-registration
dataplane.endpoint=http://localhost:9090
dataplane.control-plane-admin-endpoint=http://localhost:8080
dataplane.auth-type=API_KEY
dataplane.api-key=changeme

# S3 (consumer's S3 for storing downloaded files)
s3.endpoint=http://minio:9000
s3.accessKey=minioadmin
s3.secretKey=minioadmin
s3.region=us-east-1
s3.bucketName=consumer-bucket

# TLS configuration
# Controls OkHttpClientConfiguration (in tools): false = insecure noop client (dev only)
# true = TLS client with OCSP-validated custom truststore
server.ssl.enabled=false

# When server.ssl.enabled=true, configure the truststore SSL bundle (required by OcspTrustManagerFactory):
# spring.ssl.bundle.jks.connector.truststore.location=classpath:truststore.p12
# spring.ssl.bundle.jks.connector.truststore.password=changeit
# spring.ssl.bundle.jks.connector.truststore.type=PKCS12
# spring.ssl.key-store=classpath:keystore.p12
# spring.ssl.key-store-password=changeit
# spring.ssl.key-store-type=PKCS12
```

- [ ] **Step 7: Run tests to verify they pass**

```bash
mvn -pl data-plane-http-pull test -Dtest=HttpPullTransferProtocolTest
```

Expected: `BUILD SUCCESS`, 2 tests pass.

- [ ] **Step 8: Commit**

```bash
git add data-plane-http-pull/
git commit -m "feat(data-plane-http-pull): add HttpPullTransferProtocol and Spring Boot app"
```

---

### Task 5: `data-plane-http-push` Spring Boot app

**Files:**
- Create: `data-plane-http-push/src/main/java/it/eng/dataplane/httppush/DataPlaneHttpPushApplication.java`
- Create: `data-plane-http-push/src/main/java/it/eng/dataplane/httppush/HttpPushTransferProtocol.java`
- Create: `data-plane-http-push/src/main/java/it/eng/dataplane/httppush/config/HttpPushConfiguration.java`
- Create: `data-plane-http-push/src/main/resources/application.properties`
- Test: `data-plane-http-push/src/test/java/it/eng/dataplane/httppush/HttpPushTransferProtocolTest.java`

The logic to migrate here lives in:
`data-transfer/src/main/java/it/eng/datatransfer/service/api/strategy/HttpPushTransferStrategy.java`.
Also read: `data-transfer/src/main/java/it/eng/datatransfer/service/api/DataTransferAPIService.java` lines 137-183 for the temporary credential creation logic that moves here.

- [ ] **Step 1: Write failing test**

```java
// data-plane-http-push/src/test/java/it/eng/dataplane/httppush/HttpPushTransferProtocolTest.java
package it.eng.dataplane.httppush;

import it.eng.dataplane.api.model.DataFlow;
import it.eng.dataplane.api.model.DataFlowResult;
import it.eng.tools.s3.service.S3ClientService;
import it.eng.tools.s3.service.S3BucketProvisionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class HttpPushTransferProtocolTest {

    @Mock S3ClientService s3ClientService;
    @InjectMocks HttpPushTransferProtocol protocol;

    @Test
    void transferTypeIsHttpDataPush() {
        assertThat(protocol.transferType()).isEqualTo("HttpData-PUSH");
    }

    @Test
    void executeReturnsFailureWhenDataAddressEmpty() throws Exception {
        DataFlow dataFlow = DataFlow.Builder.newInstance()
            .dataFlowId("df-2")
            .processId("tp-2")
            .transferType("HttpData-PUSH")
            .dataAddress(Map.of())
            .build();

        DataFlowResult result = protocol.execute(dataFlow).get();

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("bucketName");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
mvn -pl data-plane-http-push test -Dtest=HttpPushTransferProtocolTest
```

Expected: FAIL — `HttpPushTransferProtocol` not found.

- [ ] **Step 3: Create `DataPlaneHttpPushApplication`** — same pattern as Task 4 Step 3, package `it.eng.dataplane.httppush`.

- [ ] **Step 4: Create `HttpPushTransferProtocol`**

This migrates the push logic. On the **consumer** side: creates temp S3 credentials and returns them as `dataAddress` in the `PREPARED` callback. On the **provider** side: receives consumer's S3 credentials, downloads artifact via presigned URL, uploads to consumer S3.

Read `HttpPushTransferStrategy.java` and `DataTransferAPIService.java` lines 137-183 before implementing.

```java
// data-plane-http-push/src/main/java/it/eng/dataplane/httppush/HttpPushTransferProtocol.java
package it.eng.dataplane.httppush;

import it.eng.dataplane.api.DataTransferProtocol;
import it.eng.dataplane.api.model.DataFlow;
import it.eng.dataplane.api.model.DataFlowResult;
import it.eng.tools.s3.service.S3ClientService;
import it.eng.tools.s3.service.TemporaryBucketUserService;
import it.eng.tools.s3.model.TemporaryBucketUser;
import it.eng.tools.s3.utils.S3Utils;
import it.eng.tools.service.TenantBucketResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

/**
 * HTTP-PUSH transfer protocol implementation.
 *
 * Role=CONSUMER (prepare): creates a temporary S3 user with PutObject permission
 * and returns credentials in the PREPARED callback dataAddress.
 *
 * Role=PROVIDER (start/execute): downloads artifact from provider S3 (presigned GET),
 * uploads to consumer S3 bucket using the temp credentials from dataAddress.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HttpPushTransferProtocol implements DataTransferProtocol {

    private static final String TRANSFER_TYPE = "HttpData-PUSH";

    private final S3ClientService s3ClientService;
    private final TemporaryBucketUserService temporaryBucketUserService;
    private final TenantBucketResolver tenantBucketResolver;
    private final Executor transferExecutor;

    @Override
    public String transferType() {
        return TRANSFER_TYPE;
    }

    /**
     * PROVIDER role: pushes artifact to consumer S3 using credentials from dataAddress.
     * dataAddress must contain: bucketName, objectKey, accessKey, secretKey, endpointOverride, region.
     */
    @Override
    public CompletableFuture<DataFlowResult> execute(DataFlow dataFlow) {
        return CompletableFuture.supplyAsync(() -> {
            Map<String, String> addr = dataFlow.getDataAddress();
            if (addr == null || !addr.containsKey(S3Utils.BUCKET_NAME)) {
                return DataFlowResult.failure(dataFlow.getDataFlowId(),
                    "dataAddress.bucketName is required for HttpData-PUSH provider role");
            }
            return pushToConsumerS3(dataFlow);
        }, transferExecutor);
    }

    private DataFlowResult pushToConsumerS3(DataFlow dataFlow) {
        AtomicReference<HttpURLConnection> connRef = new AtomicReference<>();
        String processId = dataFlow.getProcessId();
        try {
            // Generate presigned GET for the provider's own artifact
            String providerBucket = tenantBucketResolver.resolveBucketName(dataFlow.getTenantId());
            String providerObjectKey = dataFlow.getDatasetId();
            String presignedUrl = s3ClientService.generateGetPresignedUrl(
                providerBucket, providerObjectKey, java.time.Duration.ofDays(1L));

            HttpURLConnection conn = (HttpURLConnection) new URL(presignedUrl).openConnection();
            connRef.set(conn);
            conn.setRequestMethod("GET");
            conn.connect();

            Map<String, String> consumerProps = new HashMap<>(dataFlow.getDataAddress());
            consumerProps.put(S3Utils.OBJECT_KEY, processId);

            try (InputStream in = conn.getInputStream()) {
                s3ClientService.uploadFile(in, consumerProps, "application/octet-stream", null).get();
            }

            // Clean up temp user after successful push
            temporaryBucketUserService.deleteTemporaryUser(processId);

            log.info("HTTP-PUSH transfer complete: processId={}", processId);
            return DataFlowResult.success(dataFlow.getDataFlowId());
        } catch (Exception e) {
            log.error("HTTP-PUSH transfer failed: processId={}: {}", processId, e.getMessage(), e);
            return DataFlowResult.failure(dataFlow.getDataFlowId(), e.getMessage());
        } finally {
            HttpURLConnection conn = connRef.get();
            if (conn != null) conn.disconnect();
        }
    }

    @Override
    public void suspend(DataFlow dataFlow) {
        log.warn("Suspend not supported for HttpData-PUSH: processId={}", dataFlow.getProcessId());
    }

    @Override
    public void resume(DataFlow dataFlow) {
        log.warn("Resume not supported for HttpData-PUSH: processId={}", dataFlow.getProcessId());
    }

    @Override
    public void terminate(DataFlow dataFlow) {
        log.info("Terminate: cleaning up temp user for processId={}", dataFlow.getProcessId());
        try {
            temporaryBucketUserService.deleteTemporaryUser(dataFlow.getProcessId());
        } catch (Exception e) {
            log.warn("Failed to clean up temp user for processId={}: {}", dataFlow.getProcessId(), e.getMessage());
        }
    }
}
```

- [ ] **Step 5: Create `HttpPushConfiguration`** — same as Task 4 Step 5, thread prefix `http-push-`, bean name `transferExecutor`.

- [ ] **Step 6: Create `application.properties`**

```properties
# data-plane-http-push/src/main/resources/application.properties
server.port=9091
spring.application.name=data-plane-http-push
spring.data.mongodb.uri=mongodb://localhost:27017/data-plane-push
dataplane.endpoint=http://localhost:9091
dataplane.control-plane-admin-endpoint=http://localhost:8080
dataplane.auth-type=API_KEY
dataplane.api-key=changeme
s3.endpoint=http://minio:9000
s3.accessKey=minioadmin
s3.secretKey=minioadmin
s3.region=us-east-1
s3.bucketName=provider-bucket

# TLS configuration — same semantics as data-plane-http-pull (see Task 4 Step 6)
server.ssl.enabled=false
# spring.ssl.bundle.jks.connector.truststore.location=classpath:truststore.p12
# spring.ssl.bundle.jks.connector.truststore.password=changeit
# spring.ssl.bundle.jks.connector.truststore.type=PKCS12
```

- [ ] **Step 7: Run tests**

```bash
mvn -pl data-plane-http-push test -Dtest=HttpPushTransferProtocolTest
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 8: Commit**

```bash
git add data-plane-http-push/
git commit -m "feat(data-plane-http-push): add HttpPushTransferProtocol and Spring Boot app"
```

---
## Phase 3 — Control Plane Side (parallel trio after Task 3)

> Tasks 6, 7, and 8 can be executed in parallel by three agents.

### Task 6: `DataPlaneRegistration` — CP registration model, repo, service, controller

**Files:**
- Create: `data-transfer/src/main/java/it/eng/datatransfer/model/DataPlaneRegistration.java`
- Create: `data-transfer/src/main/java/it/eng/datatransfer/repository/DataPlaneRegistrationRepository.java`
- Create: `data-transfer/src/main/java/it/eng/datatransfer/service/DataPlaneRegistrationService.java`
- Create: `data-transfer/src/main/java/it/eng/datatransfer/rest/api/DataPlaneRegistrationController.java`
- Test: `data-transfer/src/test/java/it/eng/datatransfer/service/DataPlaneRegistrationServiceTest.java`
- Test: `data-transfer/src/test/java/it/eng/datatransfer/model/DataPlaneRegistrationTest.java`

- [ ] **Step 1: Write failing test**

```java
// data-transfer/src/test/java/it/eng/datatransfer/service/DataPlaneRegistrationServiceTest.java
package it.eng.datatransfer.service;

import it.eng.datatransfer.model.DataPlaneRegistration;
import it.eng.datatransfer.repository.DataPlaneRegistrationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DataPlaneRegistrationServiceTest {

    @Mock DataPlaneRegistrationRepository repository;
    @InjectMocks DataPlaneRegistrationService service;

    @Test
    void registerSavesNewDataPlane() {
        DataPlaneRegistration reg = DataPlaneRegistration.Builder.newInstance()
            .endpoint("http://dp:9090")
            .supportedTransferTypes(Set.of("HttpData-PULL"))
            .build();
        when(repository.save(any())).thenReturn(reg);

        DataPlaneRegistration saved = service.register(reg);

        verify(repository).save(reg);
        assertThat(saved.getEndpoint()).isEqualTo("http://dp:9090");
    }

    @Test
    void findByTransferTypeReturnsMatchingRegistrations() {
        DataPlaneRegistration reg = DataPlaneRegistration.Builder.newInstance()
            .endpoint("http://dp:9090")
            .supportedTransferTypes(Set.of("HttpData-PULL"))
            .build();
        when(repository.findBySupportedTransferTypesContaining("HttpData-PULL"))
            .thenReturn(List.of(reg));

        List<DataPlaneRegistration> result = service.findByTransferType("HttpData-PULL");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getEndpoint()).isEqualTo("http://dp:9090");
    }

    @Test
    void deregisterDeletesById() {
        service.deregister("dp-1");
        verify(repository).deleteById("dp-1");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
mvn -pl data-transfer test -Dtest=DataPlaneRegistrationServiceTest
```

Expected: FAIL.

- [ ] **Step 3: Create `DataPlaneRegistration`**

```java
// data-transfer/src/main/java/it/eng/datatransfer/model/DataPlaneRegistration.java
package it.eng.datatransfer.model;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.ValidationException;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AccessLevel;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Document("data_plane_registrations")
public class DataPlaneRegistration {

    @Id
    private String id;
    @NotBlank(message = "must not be blank")
    private String endpoint;
    @NotEmpty(message = "must not be empty")
    private Set<String> supportedTransferTypes;
    private String authType;
    private String apiKey;
    private Instant lastHeartbeat;
    private Instant registeredAt;

    public static class Builder {
        private final DataPlaneRegistration instance = new DataPlaneRegistration();

        private Builder() {}

        public static Builder newInstance() { return new Builder(); }

        public Builder id(String id) { instance.id = id; return this; }
        public Builder endpoint(String endpoint) { instance.endpoint = endpoint; return this; }
        public Builder supportedTransferTypes(Set<String> types) { instance.supportedTransferTypes = types; return this; }
        public Builder authType(String authType) { instance.authType = authType; return this; }
        public Builder apiKey(String apiKey) { instance.apiKey = apiKey; return this; }

        public DataPlaneRegistration build() {
            if (instance.registeredAt == null) instance.registeredAt = Instant.now();
            if (instance.id == null) instance.id = java.util.UUID.randomUUID().toString();
            Set<ConstraintViolation<DataPlaneRegistration>> violations =
                Validation.buildDefaultValidatorFactory().getValidator().validate(instance);
            if (violations.isEmpty()) {
                return instance;
            }
            throw new ValidationException("DataPlaneRegistration - " +
                violations.stream()
                    .map(v -> v.getPropertyPath() + " " + v.getMessage())
                    .collect(Collectors.joining(",")));
        }
    }
}
```

- [ ] **Step 3a: Write and run `DataPlaneRegistrationTest`**

```java
// data-transfer/src/test/java/it/eng/datatransfer/model/DataPlaneRegistrationTest.java
package it.eng.datatransfer.model;

import jakarta.validation.ValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class DataPlaneRegistrationTest {

    @Test
    @DisplayName("Build with required fields succeeds and auto-generates id and registeredAt")
    void buildWithRequiredFieldsSucceeds() {
        DataPlaneRegistration reg = DataPlaneRegistration.Builder.newInstance()
            .endpoint("http://dp:9090")
            .supportedTransferTypes(Set.of("HttpData-PULL"))
            .build();
        assertThat(reg.getEndpoint()).isEqualTo("http://dp:9090");
        assertThat(reg.getSupportedTransferTypes()).containsExactly("HttpData-PULL");
        assertThat(reg.getId()).isNotBlank();
        assertThat(reg.getRegisteredAt()).isNotNull();
    }

    @Test
    @DisplayName("Missing endpoint throws ValidationException")
    void missingEndpointThrowsValidation() {
        assertThrows(ValidationException.class, () ->
            DataPlaneRegistration.Builder.newInstance()
                .supportedTransferTypes(Set.of("HttpData-PULL"))
                .build());
    }

    @Test
    @DisplayName("Missing supportedTransferTypes throws ValidationException")
    void missingSupportedTransferTypesThrowsValidation() {
        assertThrows(ValidationException.class, () ->
            DataPlaneRegistration.Builder.newInstance()
                .endpoint("http://dp:9090")
                .build());
    }

    @Test
    @DisplayName("Empty supportedTransferTypes throws ValidationException")
    void emptySupportedTransferTypesThrowsValidation() {
        assertThrows(ValidationException.class, () ->
            DataPlaneRegistration.Builder.newInstance()
                .endpoint("http://dp:9090")
                .supportedTransferTypes(Set.of())
                .build());
    }

    @Test
    @DisplayName("Empty builder throws ValidationException")
    void emptyBuilderThrowsValidation() {
        assertThrows(ValidationException.class, () ->
            DataPlaneRegistration.Builder.newInstance().build());
    }
}
```

Run:
```bash
mvn -pl data-transfer -am test -Dtest=DataPlaneRegistrationTest
```

Expected: `BUILD SUCCESS`, 5 tests pass.

- [ ] **Step 4: Create `DataPlaneRegistrationRepository`**

```java
// data-transfer/src/main/java/it/eng/datatransfer/repository/DataPlaneRegistrationRepository.java
package it.eng.datatransfer.repository;

import it.eng.datatransfer.model.DataPlaneRegistration;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;

public interface DataPlaneRegistrationRepository extends MongoRepository<DataPlaneRegistration, String> {

    /**
     * Finds all registered Data Planes that support the given transfer type.
     *
     * @param transferType e.g. "HttpData-PULL"
     * @return list of matching registrations
     */
    List<DataPlaneRegistration> findBySupportedTransferTypesContaining(String transferType);
}
```

- [ ] **Step 5: Create `DataPlaneRegistrationService`**

```java
// data-transfer/src/main/java/it/eng/datatransfer/service/DataPlaneRegistrationService.java
package it.eng.datatransfer.service;

import it.eng.datatransfer.model.DataPlaneRegistration;
import it.eng.datatransfer.repository.DataPlaneRegistrationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.List;

/**
 * Manages Data Plane registrations on the Control Plane side.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataPlaneRegistrationService {

    private final DataPlaneRegistrationRepository repository;

    /**
     * Registers or updates a Data Plane.
     *
     * @param registration the registration to save
     * @return the saved registration
     */
    public DataPlaneRegistration register(DataPlaneRegistration registration) {
        log.info("Registering Data Plane: endpoint={}, types={}",
            registration.getEndpoint(), registration.getSupportedTransferTypes());
        return repository.save(registration);
    }

    /**
     * Finds all Data Planes supporting the given transfer type.
     *
     * @param transferType e.g. "HttpData-PULL"
     * @return list of matching registrations
     */
    public List<DataPlaneRegistration> findByTransferType(String transferType) {
        return repository.findBySupportedTransferTypesContaining(transferType);
    }

    /**
     * Removes a Data Plane registration by ID.
     *
     * @param id the registration ID
     */
    public void deregister(String id) {
        log.info("Deregistering Data Plane: id={}", id);
        repository.deleteById(id);
    }

    /**
     * Returns all registered Data Planes.
     *
     * @return all registrations
     */
    public List<DataPlaneRegistration> findAll() {
        return repository.findAll();
    }
}
```

- [ ] **Step 6: Create `DataPlaneRegistrationController`**

```java
// data-transfer/src/main/java/it/eng/datatransfer/rest/api/DataPlaneRegistrationController.java
package it.eng.datatransfer.rest.api;

import it.eng.datatransfer.model.DataPlaneRegistration;
import it.eng.datatransfer.service.DataPlaneRegistrationService;
import it.eng.tools.controller.ApiEndpoints;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Admin API for managing Data Plane registrations.
 * Requires ROLE_ADMIN.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping(ApiEndpoints.DATA_PLANES)
public class DataPlaneRegistrationController {

    private final DataPlaneRegistrationService registrationService;

    /**
     * Registers or updates a Data Plane. Called by the DP on startup.
     *
     * @param payload registration payload with endpoint and supportedTransferTypes
     * @return 200 OK
     */
    @PutMapping
    public ResponseEntity<DataPlaneRegistration> register(@RequestBody Map<String, Object> payload) {
        @SuppressWarnings("unchecked")
        Set<String> types = Set.copyOf((List<String>) payload.get("supportedTransferTypes"));
        DataPlaneRegistration reg = DataPlaneRegistration.Builder.newInstance()
            .endpoint((String) payload.get("endpoint"))
            .supportedTransferTypes(types)
            .build();
        return ResponseEntity.ok(registrationService.register(reg));
    }

    /**
     * Deregisters a Data Plane by ID.
     *
     * @param id the registration ID
     * @return 204 No Content
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deregister(@PathVariable String id) {
        registrationService.deregister(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Lists all registered Data Planes.
     *
     * @return list of registrations
     */
    @GetMapping
    public ResponseEntity<List<DataPlaneRegistration>> list() {
        return ResponseEntity.ok(registrationService.findAll());
    }
}
```

> **Note:** `ApiEndpoints.DATA_PLANES` = `"/api/v1/dataplanes"` — add this constant to `it.eng.tools.controller.ApiEndpoints`.

- [ ] **Step 7: Add `DATA_PLANES` constant to `ApiEndpoints`**

In `tools/src/main/java/it/eng/tools/controller/ApiEndpoints.java`, add:

```java
public static final String DATA_PLANES = "/api/v1/dataplanes";
```

- [ ] **Step 8: Run tests**

```bash
mvn -pl data-transfer test -Dtest=DataPlaneRegistrationServiceTest
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 9: Commit**

```bash
git add data-transfer/src/main/java/it/eng/datatransfer/model/DataPlaneRegistration.java \
        data-transfer/src/main/java/it/eng/datatransfer/repository/DataPlaneRegistrationRepository.java \
        data-transfer/src/main/java/it/eng/datatransfer/service/DataPlaneRegistrationService.java \
        data-transfer/src/main/java/it/eng/datatransfer/rest/api/DataPlaneRegistrationController.java \
        tools/src/main/java/it/eng/tools/controller/ApiEndpoints.java
git commit -m "feat(data-transfer): add DataPlaneRegistration model, repo, service, admin controller"
```

---

### Task 7: `DataPlaneClient` and `DataPlaneRouter`

**Files:**
- Create: `data-transfer/src/main/java/it/eng/datatransfer/client/DataPlaneClient.java`
- Create: `data-transfer/src/main/java/it/eng/datatransfer/router/DataPlaneRouter.java`
- Test: `data-transfer/src/test/java/it/eng/datatransfer/router/DataPlaneRouterTest.java`
- Test: `data-transfer/src/test/java/it/eng/datatransfer/client/DataPlaneClientTest.java`

- [ ] **Step 1: Write failing tests**

```java
// data-transfer/src/test/java/it/eng/datatransfer/router/DataPlaneRouterTest.java
package it.eng.datatransfer.router;

import it.eng.datatransfer.model.DataPlaneRegistration;
import it.eng.datatransfer.service.DataPlaneRegistrationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataPlaneRouterTest {

    @Mock DataPlaneRegistrationService registrationService;
    @InjectMocks DataPlaneRouter router;

    @Test
    void selectsEndpointForKnownTransferType() {
        DataPlaneRegistration reg = DataPlaneRegistration.Builder.newInstance()
            .endpoint("http://dp-pull:9090")
            .supportedTransferTypes(Set.of("HttpData-PULL"))
            .build();
        when(registrationService.findByTransferType("HttpData-PULL")).thenReturn(List.of(reg));

        Optional<DataPlaneRegistration> result = router.selectDataPlane("HttpData-PULL");

        assertThat(result).isPresent();
        assertThat(result.get().getEndpoint()).isEqualTo("http://dp-pull:9090");
    }

    @Test
    void returnsEmptyForUnknownTransferType() {
        when(registrationService.findByTransferType("Unknown")).thenReturn(List.of());

        Optional<DataPlaneRegistration> result = router.selectDataPlane("Unknown");

        assertThat(result).isEmpty();
    }

    @Test
    void roundRobinsAcrossMultipleInstances() {
        DataPlaneRegistration reg1 = DataPlaneRegistration.Builder.newInstance()
            .endpoint("http://dp-pull-1:9090").supportedTransferTypes(Set.of("HttpData-PULL")).build();
        DataPlaneRegistration reg2 = DataPlaneRegistration.Builder.newInstance()
            .endpoint("http://dp-pull-2:9090").supportedTransferTypes(Set.of("HttpData-PULL")).build();
        when(registrationService.findByTransferType("HttpData-PULL")).thenReturn(List.of(reg1, reg2));

        String ep1 = router.selectDataPlane("HttpData-PULL").get().getEndpoint();
        String ep2 = router.selectDataPlane("HttpData-PULL").get().getEndpoint();

        assertThat(ep1).isNotEqualTo(ep2);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
mvn -pl data-transfer test -Dtest=DataPlaneRouterTest
```

- [ ] **Step 3: Create `DataPlaneRouter`**

```java
// data-transfer/src/main/java/it/eng/datatransfer/router/DataPlaneRouter.java
package it.eng.datatransfer.router;

import it.eng.datatransfer.model.DataPlaneRegistration;
import it.eng.datatransfer.service.DataPlaneRegistrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Routes transfer requests to a registered Data Plane instance.
 * Uses round-robin selection across multiple instances of the same transfer type.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataPlaneRouter {

    private final DataPlaneRegistrationService registrationService;
    private final ConcurrentHashMap<String, AtomicInteger> counters = new ConcurrentHashMap<>();

    /**
     * Selects a Data Plane for the given transfer type using round-robin.
     *
     * @param transferType e.g. "HttpData-PULL"
     * @return the selected registration, or empty if none registered
     */
    public Optional<DataPlaneRegistration> selectDataPlane(String transferType) {
        List<DataPlaneRegistration> candidates = registrationService.findByTransferType(transferType);
        if (candidates.isEmpty()) {
            log.warn("No Data Plane registered for transferType={}", transferType);
            return Optional.empty();
        }
        AtomicInteger counter = counters.computeIfAbsent(transferType, k -> new AtomicInteger(0));
        int index = Math.abs(counter.getAndIncrement() % candidates.size());
        DataPlaneRegistration selected = candidates.get(index);
        log.debug("Selected Data Plane endpoint={} for transferType={}", selected.getEndpoint(), transferType);
        return Optional.of(selected);
    }
}
```

- [ ] **Step 4: Write `DataPlaneClientTest` then create `DataPlaneClient`**

```java
// data-transfer/src/test/java/it/eng/datatransfer/client/DataPlaneClientTest.java
package it.eng.datatransfer.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.eng.dataplane.api.message.DataFlowStartMessage;
import it.eng.dataplane.api.message.DataFlowPrepareMessage;
import it.eng.datatransfer.model.DataPlaneRegistration;
import it.eng.datatransfer.router.DataPlaneRouter;
import okhttp3.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.io.IOException;
import java.util.Optional;
import java.util.Set;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DataPlaneClientTest {

    @Mock OkHttpClient okHttpClient;
    @Mock Call call;
    @Mock DataPlaneRouter router;

    DataPlaneClient client;
    ObjectMapper objectMapper = new ObjectMapper();

    DataPlaneRegistration dp = DataPlaneRegistration.Builder.newInstance()
        .endpoint("http://dp:9090")
        .supportedTransferTypes(Set.of("HttpData-PULL"))
        .apiKey("secret-key")
        .build();

    @BeforeEach
    void setUp() throws IOException {
        client = new DataPlaneClient(okHttpClient, objectMapper, router);
        Response fakeResponse = new Response.Builder()
            .request(new Request.Builder().url("http://dp:9090/dataflows/start").build())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(ResponseBody.create("", MediaType.get("application/json")))
            .build();
        when(okHttpClient.newCall(any())).thenReturn(call);
        when(call.execute()).thenReturn(fakeResponse);
    }

    @Test
    void startSendsPostToDataPlaneWithApiKey() throws IOException {
        DataFlowStartMessage msg = DataFlowStartMessage.Builder.newInstance()
            .processId("proc-1")
            .transferType("HttpData-PULL")
            .build();
        when(router.selectDataPlane("HttpData-PULL")).thenReturn(Optional.of(dp));

        client.start(msg);

        verify(okHttpClient).newCall(argThat(req ->
            req.url().toString().contains("/dataflows/start") &&
            "secret-key".equals(req.header("X-Api-Key"))));
    }

    @Test
    void prepareSendsPostToDataPlane() throws IOException {
        DataFlowPrepareMessage msg = DataFlowPrepareMessage.Builder.newInstance()
            .processId("proc-1")
            .transferType("HttpData-PUSH")
            .build();
        DataPlaneRegistration pushDp = DataPlaneRegistration.Builder.newInstance()
            .endpoint("http://dp:9091")
            .supportedTransferTypes(Set.of("HttpData-PUSH"))
            .build();
        when(router.selectDataPlane("HttpData-PUSH")).thenReturn(Optional.of(pushDp));

        client.prepare(msg);

        verify(okHttpClient).newCall(argThat(req ->
            req.url().toString().contains("/dataflows/prepare")));
    }

    @Test
    void startThrowsWhenNoDataPlaneRegistered() {
        DataFlowStartMessage msg = DataFlowStartMessage.Builder.newInstance()
            .processId("proc-1")
            .transferType("Unknown")
            .build();
        when(router.selectDataPlane("Unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> client.start(msg))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Unknown");
    }
}
```

Run to verify it fails:
```bash
mvn -pl data-transfer test -Dtest=DataPlaneClientTest
```

Expected: FAIL — `DataPlaneClient` not found.

Now create the implementation:

```java
// data-transfer/src/main/java/it/eng/datatransfer/client/DataPlaneClient.java
package it.eng.datatransfer.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.eng.dataplane.api.message.DataFlowPrepareMessage;
import it.eng.dataplane.api.message.DataFlowStartMessage;
import it.eng.datatransfer.model.DataPlaneRegistration;
import it.eng.datatransfer.router.DataPlaneRouter;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.stereotype.Component;
import java.io.IOException;

/**
 * Sends DPS messages from Control Plane to Data Plane over HTTP.
 * Uses the shared {@link OkHttpClient} bean from {@code OkHttpClientConfiguration} (in
 * {@code tools}), which is TLS-aware: it reads {@code server.ssl.enabled} and creates
 * either a TLS client with the custom truststore bundle or a noop-insecure client for
 * development. No {@code RestTemplate} is used so that the same TLS policy applies to
 * all outbound HTTP in the connector.
 */
@Slf4j
@Component
public class DataPlaneClient {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient okHttpClient;
    private final ObjectMapper objectMapper;
    private final DataPlaneRouter router;

    /**
     * @param okHttpClient TLS-aware HTTP client from {@code OkHttpClientConfiguration}
     * @param objectMapper shared Jackson mapper
     * @param router selects the correct Data Plane registration for a transfer type
     */
    public DataPlaneClient(OkHttpClient okHttpClient, ObjectMapper objectMapper, DataPlaneRouter router) {
        this.okHttpClient = okHttpClient;
        this.objectMapper = objectMapper;
        this.router = router;
    }

    /**
     * Sends a DataFlowStartMessage to the appropriate Data Plane (HTTP-PULL provider side).
     *
     * @param startMessage the DPS start message
     * @throws IllegalStateException if no Data Plane is registered for the transfer type
     */
    public void start(DataFlowStartMessage startMessage) {
        DataPlaneRegistration dp = router.selectDataPlane(startMessage.getTransferType())
            .orElseThrow(() -> new IllegalStateException(
                "No Data Plane registered for transferType: " + startMessage.getTransferType()));
        post(dp, "/dataflows/start", startMessage);
    }

    /**
     * Sends a DataFlowPrepareMessage to the appropriate Data Plane (HTTP-PUSH consumer side).
     *
     * @param prepareMessage the DPS prepare message
     * @throws IllegalStateException if no Data Plane is registered
     */
    public void prepare(DataFlowPrepareMessage prepareMessage) {
        DataPlaneRegistration dp = router.selectDataPlane(prepareMessage.getTransferType())
            .orElseThrow(() -> new IllegalStateException(
                "No Data Plane registered for transferType: " + prepareMessage.getTransferType()));
        post(dp, "/dataflows/prepare", prepareMessage);
    }

    private void post(DataPlaneRegistration dp, String path, Object body) {
        String url = dp.getEndpoint() + path;
        try {
            String json = objectMapper.writeValueAsString(body);
            Request.Builder requestBuilder = new Request.Builder()
                .url(url)
                .post(RequestBody.create(json, JSON))
                .addHeader("Content-Type", "application/json");
            if (dp.getApiKey() != null) {
                requestBuilder.addHeader("X-Api-Key", dp.getApiKey());
            }
            try (Response response = okHttpClient.newCall(requestBuilder.build()).execute()) {
                log.info("DP {} at {} returned HTTP {}", path, url, response.code());
                if (!response.isSuccessful()) {
                    log.error("Data Plane rejected {} with HTTP {}", path, response.code());
                }
            }
        } catch (IOException e) {
            log.error("Failed to call Data Plane at {}: {}", url, e.getMessage());
        }
    }
}
```

- [ ] **Step 5: Run tests**

```bash
mvn -pl data-transfer test -Dtest="DataPlaneRouterTest,DataPlaneClientTest"
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 6: Commit**

```bash
git add data-transfer/src/main/java/it/eng/datatransfer/client/ \
        data-transfer/src/main/java/it/eng/datatransfer/router/
git commit -m "feat(data-transfer): add DataPlaneRouter and DataPlaneClient"
```

---

### Task 8: `DataFlowCallbackController` — receive DP callbacks on CP

**Files:**
- Create: `data-transfer/src/main/java/it/eng/datatransfer/rest/protocol/DataFlowCallbackController.java`
- Test: `data-transfer/src/test/java/it/eng/datatransfer/rest/protocol/DataFlowCallbackControllerTest.java`

This controller receives state callbacks from the Data Plane at:
`POST /{tenantId}/transfers/{transferProcessId}/dataflow/{state}`

It updates the `TransferProcess` state in MongoDB and notifies the counterparty via the existing protocol message senders.

- [ ] **Step 1: Write failing test**

```java
// data-transfer/src/test/java/it/eng/datatransfer/rest/protocol/DataFlowCallbackControllerTest.java
package it.eng.datatransfer.rest.protocol;

import it.eng.dataplane.api.message.DataFlowStatusMessage;
import it.eng.dataplane.api.model.DataFlowState;
import it.eng.datatransfer.service.DataFlowCallbackService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DataFlowCallbackControllerTest {

    @Mock DataFlowCallbackService callbackService;
    @InjectMocks DataFlowCallbackController controller;

    @Test
    void completedCallbackReturns200() {
        DataFlowStatusMessage msg = DataFlowStatusMessage.Builder.newInstance()
            .dataFlowId("df-1").processId("tp-1").state(DataFlowState.COMPLETED).build();

        ResponseEntity<Void> response = controller.handleCallback("tenant1", "tp-1", "completed", msg);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(callbackService).handleCallback("tenant1", "tp-1", DataFlowState.COMPLETED, msg);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
mvn -pl data-transfer test -Dtest=DataFlowCallbackControllerTest
```

- [ ] **Step 3: Create `DataFlowCallbackService`**

```java
// data-transfer/src/main/java/it/eng/datatransfer/service/DataFlowCallbackService.java
package it.eng.datatransfer.service;

import it.eng.dataplane.api.message.DataFlowStatusMessage;
import it.eng.dataplane.api.model.DataFlowState;
import it.eng.datatransfer.model.TransferProcess;
import it.eng.datatransfer.model.TransferProcessStates;
import it.eng.datatransfer.repository.TransferProcessRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Handles DataFlow state callbacks from the Data Plane.
 * Translates DataFlowState to TransferProcess state transitions.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataFlowCallbackService {

    private final TransferProcessRepository transferProcessRepository;

    /**
     * Processes a callback from the Data Plane.
     * COMPLETED → TransferProcess transitions to COMPLETED.
     * TERMINATED → logs the error (does not change TransferProcess state).
     * PREPARED → TransferProcess stores dataAddress for provider to use.
     *
     * @param tenantId the tenant context
     * @param processId the TransferProcess ID
     * @param state the new DataFlow state
     * @param message the full status message
     */
    public void handleCallback(String tenantId, String processId, DataFlowState state, DataFlowStatusMessage message) {
        log.info("DataFlow callback: tenantId={}, processId={}, state={}", tenantId, processId, state);
        switch (state) {
            case COMPLETED -> handleCompleted(tenantId, processId);
            case TERMINATED -> log.error("DataFlow terminated for processId={}: {}", processId, message.getError());
            case PREPARED -> handlePrepared(tenantId, processId, message);
            default -> log.debug("Ignoring intermediate state {} for processId={}", state, processId);
        }
    }

    private void handleCompleted(String tenantId, String processId) {
        transferProcessRepository.findByIdAndTenantId(processId, tenantId).ifPresent(tp -> {
            tp.setState(TransferProcessStates.COMPLETED);
            transferProcessRepository.save(tp);
            log.info("TransferProcess {} moved to COMPLETED", processId);
        });
    }

    private void handlePrepared(String tenantId, String processId, DataFlowStatusMessage message) {
        transferProcessRepository.findByIdAndTenantId(processId, tenantId).ifPresent(tp -> {
            // Store consumer dataAddress so provider can push to it
            if (message.getDataAddress() != null) {
                tp.setDataAddress(message.getDataAddress());
                transferProcessRepository.save(tp);
            }
            log.info("TransferProcess {} prepared with dataAddress", processId);
        });
    }
}
```

- [ ] **Step 4: Create `DataFlowCallbackController`**

```java
// data-transfer/src/main/java/it/eng/datatransfer/rest/protocol/DataFlowCallbackController.java
package it.eng.datatransfer.rest.protocol;

import it.eng.dataplane.api.message.DataFlowStatusMessage;
import it.eng.dataplane.api.model.DataFlowState;
import it.eng.datatransfer.service.DataFlowCallbackService;
import it.eng.tools.rest.api.TenantAwareProtocolController;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Receives DPS state callbacks from Data Plane instances.
 * URL: POST /{tenantId}/transfers/{transferProcessId}/dataflow/{state}
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class DataFlowCallbackController extends TenantAwareProtocolController {

    private final DataFlowCallbackService callbackService;

    /**
     * Handles a DataFlow state callback from a Data Plane.
     *
     * @param tenantId the tenant context from path
     * @param transferProcessId the TransferProcess ID
     * @param state lowercase state name (e.g. "completed", "terminated")
     * @param message the status payload from DP
     * @return 200 OK
     */
    @PostMapping("/{tenantId}/transfers/{transferProcessId}/dataflow/{state}")
    public ResponseEntity<Void> handleCallback(
            @PathVariable String tenantId,
            @PathVariable String transferProcessId,
            @PathVariable String state,
            @RequestBody DataFlowStatusMessage message) {
        DataFlowState dataFlowState = DataFlowState.valueOf(state.toUpperCase());
        callbackService.handleCallback(tenantId, transferProcessId, dataFlowState, message);
        return ResponseEntity.ok().build();
    }
}
```

- [ ] **Step 5: Run tests**

```bash
mvn -pl data-transfer test -Dtest=DataFlowCallbackControllerTest
```

- [ ] **Step 6: Commit**

```bash
git add data-transfer/src/main/java/it/eng/datatransfer/rest/protocol/DataFlowCallbackController.java \
        data-transfer/src/main/java/it/eng/datatransfer/service/DataFlowCallbackService.java
git commit -m "feat(data-transfer): add DataFlowCallbackController and DataFlowCallbackService"
```

---
## Phase 4 — Wire CP to DP

### Task 9: Modify `DataTransferAPIService` to use `DataPlaneClient`

**Files:**
- Modify: `data-transfer/src/main/java/it/eng/datatransfer/service/api/DataTransferAPIService.java`
- Test: `data-transfer/src/test/java/it/eng/datatransfer/service/api/DataTransferAPIServiceTest.java` (existing — update)

The goal: replace all calls to `DataTransferStrategyFactory` / `DataTransferStrategy.transfer()` with calls to `DataPlaneClient.start()` or `DataPlaneClient.prepare()`.

Read the current file before making changes. Key areas:
- Constructor (lines ~61-91): remove `DataTransferStrategyFactory` injection, add `DataPlaneClient`
- `startTransfer()` (lines ~100-136): remove strategy call, add `DataPlaneClient.start()`
- `requestTransfer()` (lines ~137-183): remove S3 temp-user creation (moves to data-plane-http-push), add `DataPlaneClient.prepare()`

- [ ] **Step 1: Add `data-plane-api` dependency to `data-transfer/pom.xml`**

```xml
<dependency>
    <groupId>it.eng</groupId>
    <artifactId>data-plane-api</artifactId>
    <version>${revision}</version>
</dependency>
```

- [ ] **Step 2: Update `DataTransferAPIService` constructor — remove `DataTransferStrategyFactory`, add `DataPlaneClient`**

In the constructor and field list, replace:

```java
// REMOVE these imports and fields:
private final DataTransferStrategyFactory strategyFactory;
// ADD:
private final DataPlaneClient dataPlaneClient;
```

- [ ] **Step 3: Update `startTransfer()` — replace strategy invocation with `DataPlaneClient.start()`**

Replace the strategy dispatch block with:

```java
DataFlowStartMessage startMessage = DataFlowStartMessage.Builder.newInstance()
    .messageId(UUID.randomUUID().toString())
    .processId(transferProcess.getId())
    .agreementId(transferProcess.getAgreementId())
    .datasetId(transferProcess.getDatasetId())
    .transferType(transferProcess.getFormat())  // "HttpData-PULL" or "HttpData-PUSH"
    .callbackAddress(buildCallbackAddress(transferProcess))
    .participantId(transferProcess.getParticipantId())
    .counterPartyId(transferProcess.getCounterPartyId())
    .build();
dataPlaneClient.start(startMessage);
```

Where `buildCallbackAddress(tp)` constructs the CP callback URL:
```java
private String buildCallbackAddress(TransferProcess tp) {
    return applicationProperties.getConnectorBaseUrl()
        + "/" + tp.getTenantId()
        + "/transfers";
}
```

- [ ] **Step 4: Update `requestTransfer()` — remove S3 temp-user block, add `DataPlaneClient.prepare()`**

The temp S3 user creation (approximately lines 137-183) is now handled by `data-plane-http-push`. Replace with:

```java
DataFlowPrepareMessage prepareMessage = DataFlowPrepareMessage.Builder.newInstance()
    .messageId(UUID.randomUUID().toString())
    .processId(transferProcess.getId())
    .agreementId(transferProcess.getAgreementId())
    .datasetId(transferProcess.getDatasetId())
    .transferType(transferProcess.getFormat())
    .callbackAddress(buildCallbackAddress(transferProcess))
    .participantId(transferProcess.getParticipantId())
    .counterPartyId(transferProcess.getCounterPartyId())
    .build();
dataPlaneClient.prepare(prepareMessage);
```

- [ ] **Step 5: Run modified service tests**

```bash
mvn -pl data-transfer test -Dtest=DataTransferAPIServiceTest
```

Expected: tests pass with updated mock setup for `DataPlaneClient`.

- [ ] **Step 6: Commit**

```bash
git add data-transfer/src/main/java/it/eng/datatransfer/service/api/DataTransferAPIService.java \
        data-transfer/pom.xml
git commit -m "feat(data-transfer): wire DataTransferAPIService to DataPlaneClient"
```

---

## Phase 5 — Startup Registration

### Task 10: DP startup self-registration with retry

**Files:**
- Modify: `data-plane-core/src/main/java/it/eng/dataplane/core/startup/ControlPlaneRegistrationBean.java`
- Test: `data-plane-core/src/test/java/it/eng/dataplane/core/startup/ControlPlaneRegistrationBeanTest.java`

Replace the single-attempt registration with exponential backoff retry (max 5 attempts, 2s/4s/8s/16s delays).

- [ ] **Step 1: Write failing test**

```java
// data-plane-core/src/test/java/it/eng/dataplane/core/startup/ControlPlaneRegistrationBeanTest.java
package it.eng.dataplane.core.startup;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.eng.dataplane.core.config.DataPlaneProperties;
import it.eng.dataplane.core.registry.DataTransferProtocolRegistry;
import okhttp3.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.io.IOException;
import java.util.Set;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ControlPlaneRegistrationBeanTest {

    @Mock DataPlaneProperties properties;
    @Mock DataTransferProtocolRegistry registry;
    @Mock OkHttpClient okHttpClient;
    @Mock Call call;

    ControlPlaneRegistrationBean bean;
    ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        bean = new ControlPlaneRegistrationBean(properties, registry, okHttpClient, objectMapper);
    }

    private Response okResponse() {
        return new Response.Builder()
            .request(new Request.Builder().url("http://cp:8080/api/v1/dataplanes").build())
            .protocol(Protocol.HTTP_1_1).code(200).message("OK")
            .body(ResponseBody.create("", MediaType.get("application/json")))
            .build();
    }

    private Response errorResponse(int code) {
        return new Response.Builder()
            .request(new Request.Builder().url("http://cp:8080/api/v1/dataplanes").build())
            .protocol(Protocol.HTTP_1_1).code(code).message("Error")
            .body(ResponseBody.create("", MediaType.get("application/json")))
            .build();
    }

    @Test
    void registersSuccessfullyOnFirstAttempt() throws IOException {
        when(properties.getControlPlaneAdminEndpoint()).thenReturn("http://cp:8080");
        when(properties.getEndpoint()).thenReturn("http://dp:9090");
        when(registry.getSupportedTransferTypes()).thenReturn(Set.of("HttpData-PULL"));
        when(okHttpClient.newCall(any())).thenReturn(call);
        when(call.execute()).thenReturn(okResponse());

        bean.onApplicationEvent(null);

        verify(okHttpClient, times(1)).newCall(argThat(req ->
            req.url().toString().contains("/api/v1/dataplanes")));
    }

    @Test
    void retriesOnIoExceptionThenSucceeds() throws IOException {
        when(properties.getControlPlaneAdminEndpoint()).thenReturn("http://cp:8080");
        when(properties.getEndpoint()).thenReturn("http://dp:9090");
        when(registry.getSupportedTransferTypes()).thenReturn(Set.of("HttpData-PULL"));
        when(okHttpClient.newCall(any())).thenReturn(call);
        when(call.execute())
            .thenThrow(new IOException("timeout"))
            .thenReturn(okResponse());

        bean.onApplicationEvent(null);

        verify(okHttpClient, times(2)).newCall(any());
    }

    @Test
    void skipsRegistrationWhenEndpointNotConfigured() throws IOException {
        when(properties.getControlPlaneAdminEndpoint()).thenReturn(null);

        bean.onApplicationEvent(null);

        verifyNoInteractions(okHttpClient);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
mvn -pl data-plane-core test -Dtest=ControlPlaneRegistrationBeanTest
```

- [ ] **Step 3: Update `ControlPlaneRegistrationBean` with retry logic**

```java
// data-plane-core/src/main/java/it/eng/dataplane/core/startup/ControlPlaneRegistrationBean.java
package it.eng.dataplane.core.startup;

import it.eng.dataplane.core.config.DataPlaneProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.eng.dataplane.core.config.DataPlaneProperties;
import it.eng.dataplane.core.registry.DataTransferProtocolRegistry;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.util.Map;

/**
 * Registers this Data Plane with the Control Plane at startup.
 * Retries up to 5 times with exponential backoff (2s base delay).
 * Uses the shared {@link OkHttpClient} bean from {@code OkHttpClientConfiguration} for
 * TLS-aware outbound HTTP.
 */
@Slf4j
@Component
public class ControlPlaneRegistrationBean implements ApplicationListener<ApplicationReadyEvent> {

    private static final int MAX_ATTEMPTS = 5;
    private static final long BASE_DELAY_MS = 2_000L;
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final DataPlaneProperties properties;
    private final DataTransferProtocolRegistry registry;
    private final OkHttpClient okHttpClient;
    private final ObjectMapper objectMapper;

    /**
     * @param properties DP runtime configuration
     * @param registry registered transfer protocol implementations
     * @param okHttpClient TLS-aware HTTP client from {@code OkHttpClientConfiguration}
     * @param objectMapper shared Jackson mapper
     */
    public ControlPlaneRegistrationBean(DataPlaneProperties properties,
                                        DataTransferProtocolRegistry registry,
                                        OkHttpClient okHttpClient,
                                        ObjectMapper objectMapper) {
        this.properties = properties;
        this.registry = registry;
        this.okHttpClient = okHttpClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        if (properties.getControlPlaneAdminEndpoint() == null) {
            log.warn("dataplane.control-plane-admin-endpoint not set, skipping CP registration");
            return;
        }
        registerWithRetry();
    }

    private void registerWithRetry() {
        String url = properties.getControlPlaneAdminEndpoint() + "/api/v1/dataplanes";
        Map<String, Object> payload = Map.of(
            "endpoint", properties.getEndpoint(),
            "supportedTransferTypes", registry.getSupportedTransferTypes()
        );
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                String json = objectMapper.writeValueAsString(payload);
                Request request = new Request.Builder()
                    .url(url)
                    .put(RequestBody.create(json, JSON))
                    .addHeader("Content-Type", "application/json")
                    .build();
                try (Response response = okHttpClient.newCall(request).execute()) {
                    if (response.isSuccessful()) {
                        log.info("Successfully registered with Control Plane at {} (attempt {})", url, attempt);
                        return;
                    }
                    log.warn("Registration attempt {}/{} rejected with HTTP {}", attempt, MAX_ATTEMPTS, response.code());
                }
            } catch (IOException e) {
                log.warn("Registration attempt {}/{} failed: {}", attempt, MAX_ATTEMPTS, e.getMessage());
            }
            if (attempt < MAX_ATTEMPTS) {
                sleep(BASE_DELAY_MS * (long) Math.pow(2, attempt - 1));
            }
        }
        log.error("Failed to register with Control Plane after {} attempts", MAX_ATTEMPTS);
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
```

- [ ] **Step 4: Run tests**

```bash
mvn -pl data-plane-core test -Dtest=ControlPlaneRegistrationBeanTest
```

- [ ] **Step 5: Commit**

```bash
git add data-plane-core/src/main/java/it/eng/dataplane/core/startup/ControlPlaneRegistrationBean.java
git commit -m "feat(data-plane-core): add exponential backoff retry for CP registration"
```

---

## Phase 6 — Auth (parallel pair)

> Tasks 11 and 12 can be executed in parallel.

### Task 11: API Key auth in `DataPlaneClient` (CP → DP calls)

**Files:**
- Modify: `data-transfer/src/main/java/it/eng/datatransfer/client/DataPlaneClient.java`

The stub in Task 7 already includes `X-Api-Key` header injection from `DataPlaneRegistration.apiKey`. This task adds an `ApiKeyFilter` on the DP side to validate incoming requests from CP.

- [ ] **Step 1: Add `ApiKeyAuthFilter` to `data-plane-core`**

```java
// data-plane-core/src/main/java/it/eng/dataplane/core/security/ApiKeyAuthFilter.java
package it.eng.dataplane.core.security;

import it.eng.dataplane.core.config.DataPlaneProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private static final String API_KEY_HEADER = "X-Api-Key";
    private final DataPlaneProperties properties;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String apiKey = request.getHeader(API_KEY_HEADER);
        if (apiKey != null && apiKey.equals(properties.getApiKey())) {
            UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken("control-plane", null, List.of());
            SecurityContextHolder.getContext().setAuthentication(auth);
        }
        filterChain.doFilter(request, response);
    }
}
```

- [ ] **Step 2: Update `DataPlaneSecurityConfig` to register the filter**

Replace the stub `filterChain` bean:

```java
@Bean
public SecurityFilterChain filterChain(HttpSecurity http, DataPlaneProperties properties) throws Exception {
    ApiKeyAuthFilter apiKeyFilter = new ApiKeyAuthFilter(properties);
    http
        .csrf(csrf -> csrf.disable())
        .addFilterBefore(apiKeyFilter, org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class)
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/actuator/health").permitAll()
            .anyRequest().authenticated()
        );
    return http.build();
}
```

- [ ] **Step 3: Write test for ApiKeyAuthFilter**

```java
// data-plane-core/src/test/java/it/eng/dataplane/core/security/ApiKeyAuthFilterTest.java
package it.eng.dataplane.core.security;

import it.eng.dataplane.core.config.DataPlaneProperties;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiKeyAuthFilterTest {

    @Mock DataPlaneProperties properties;
    @Mock FilterChain filterChain;

    @Test
    void authenticatesWithValidApiKey() throws Exception {
        when(properties.getApiKey()).thenReturn("secret");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Api-Key", "secret");

        new ApiKeyAuthFilter(properties).doFilterInternal(
            request, new MockHttpServletResponse(), filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getName())
            .isEqualTo("control-plane");
    }

    @Test
    void doesNotAuthenticateWithInvalidApiKey() throws Exception {
        when(properties.getApiKey()).thenReturn("secret");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Api-Key", "wrong");

        SecurityContextHolder.clearContext();
        new ApiKeyAuthFilter(properties).doFilterInternal(
            request, new MockHttpServletResponse(), filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
```

- [ ] **Step 4: Run tests**

```bash
mvn -pl data-plane-core test -Dtest=ApiKeyAuthFilterTest
```

- [ ] **Step 5: Commit**

```bash
git add data-plane-core/src/main/java/it/eng/dataplane/core/security/
git commit -m "feat(data-plane-core): add API key authentication filter for CP→DP calls"
```

---

### Task 12: API Key auth in `ControlPlaneClient` (DP → CP callbacks)

**Files:**
- Modify: `data-plane-core/src/main/java/it/eng/dataplane/core/client/ControlPlaneClient.java`
- Test: `data-plane-core/src/test/java/it/eng/dataplane/core/client/ControlPlaneClientTest.java`

Add `X-Api-Key` header (from `DataPlaneProperties.apiKey`) to outbound callback requests.
The `ControlPlaneClient` stub from Task 3 already uses `OkHttpClient`. This task adds `DataPlaneProperties` injection and the API key header to `sendStatus()`.

- [ ] **Step 1: Update `ControlPlaneClient` constructor to inject `DataPlaneProperties`**

Add `DataPlaneProperties` as a constructor parameter and pass the `apiKey` in the request:

```java
// Updated constructor — add DataPlaneProperties
public ControlPlaneClient(OkHttpClient okHttpClient, ObjectMapper objectMapper,
                          DataPlaneProperties properties) {
    this.okHttpClient = okHttpClient;
    this.objectMapper = objectMapper;
    this.properties = properties;
}
```

Update `sendStatus()` to add the API key header:

```java
Request request = new Request.Builder()
    .url(url)
    .post(RequestBody.create(json, JSON))
    .addHeader("Content-Type", "application/json")
    .addHeader("X-Api-Key", properties.getApiKey() != null ? properties.getApiKey() : "")
    .build();
```

- [ ] **Step 2: Write test**

```java
// data-plane-core/src/test/java/it/eng/dataplane/core/client/ControlPlaneClientTest.java
package it.eng.dataplane.core.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.eng.dataplane.api.model.DataFlowState;
import it.eng.dataplane.core.config.DataPlaneProperties;
import okhttp3.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.io.IOException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ControlPlaneClientTest {

    @Mock OkHttpClient okHttpClient;
    @Mock Call call;
    @Mock DataPlaneProperties properties;

    ControlPlaneClient client;
    ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() throws IOException {
        client = new ControlPlaneClient(okHttpClient, objectMapper, properties);
        Response fakeResponse = new Response.Builder()
            .request(new Request.Builder().url("http://cp:8080/callback").build())
            .protocol(Protocol.HTTP_1_1).code(200).message("OK")
            .body(ResponseBody.create("", MediaType.get("application/json")))
            .build();
        when(okHttpClient.newCall(any())).thenReturn(call);
        when(call.execute()).thenReturn(fakeResponse);
    }

    @Test
    void sendsApiKeyHeaderInCallback() throws IOException {
        when(properties.getApiKey()).thenReturn("dp-secret");
        ArgumentCaptor<Request> reqCaptor = ArgumentCaptor.forClass(Request.class);

        client.sendStatus("http://cp:8080", "tp-1", DataFlowState.COMPLETED, null, null);

        verify(okHttpClient).newCall(reqCaptor.capture());
        assertThat(reqCaptor.getValue().header("X-Api-Key")).isEqualTo("dp-secret");
        assertThat(reqCaptor.getValue().url().toString()).contains("tp-1/dataflow/completed");
    }

    @Test
    void sendsCorrectUrlForStartedState() throws IOException {
        when(properties.getApiKey()).thenReturn(null);
        ArgumentCaptor<Request> reqCaptor = ArgumentCaptor.forClass(Request.class);

        client.sendStatus("http://cp:8080", "tp-2", DataFlowState.STARTED, null, null);

        verify(okHttpClient).newCall(reqCaptor.capture());
        assertThat(reqCaptor.getValue().url().toString()).contains("tp-2/dataflow/started");
    }
}
```

- [ ] **Step 3: Run tests**

```bash
mvn -pl data-plane-core test -Dtest=ControlPlaneClientTest
```

- [ ] **Step 4: Commit**

```bash
git add data-plane-core/src/main/java/it/eng/dataplane/core/client/ControlPlaneClient.java
git commit -m "feat(data-plane-core): add API key header to CP callback requests"
```

---

## Phase 7 — Docker Compose

### Task 13: Update Docker Compose for CP + DP-PULL + DP-PUSH

**Files:**
- Modify: `ci/docker/docker-compose.yml`
- Modify: `ci/docker/.env`
- Create: `ci/docker/connector_a_resources/application-data-plane-pull.properties`
- Create: `ci/docker/connector_a_resources/application-data-plane-push.properties`

Read the current `ci/docker/docker-compose.yml` before editing to understand existing service names and network setup.

- [ ] **Step 1: Add Data Plane services to `docker-compose.yml`**

Add after the existing `connector-a` service definition:

```yaml
  data-plane-http-pull-a:
    image: data-plane-http-pull:${VERSION:-latest}
    container_name: data-plane-http-pull-a
    ports:
      - "9090:9090"
    environment:
      - SPRING_DATA_MONGODB_URI=mongodb://mongo:27017/data-plane-pull-a
      - DATAPLANE_ENDPOINT=http://data-plane-http-pull-a:9090
      - DATAPLANE_CONTROL_PLANE_ADMIN_ENDPOINT=http://connector-a:8090
      - DATAPLANE_AUTH_TYPE=API_KEY
      - DATAPLANE_API_KEY=${DP_PULL_API_KEY:-changeme}
      - S3_ENDPOINT=http://minio:9000
      - S3_ACCESSKEY=minioadmin
      - S3_SECRETKEY=minioadmin
      - S3_REGION=us-east-1
      - S3_BUCKETNAME=connector-a-bucket
    depends_on:
      - mongo
      - minio
      - connector-a
    networks:
      - connector-network

  data-plane-http-push-a:
    image: data-plane-http-push:${VERSION:-latest}
    container_name: data-plane-http-push-a
    ports:
      - "9091:9091"
    environment:
      - SPRING_DATA_MONGODB_URI=mongodb://mongo:27017/data-plane-push-a
      - DATAPLANE_ENDPOINT=http://data-plane-http-push-a:9091
      - DATAPLANE_CONTROL_PLANE_ADMIN_ENDPOINT=http://connector-a:8090
      - DATAPLANE_AUTH_TYPE=API_KEY
      - DATAPLANE_API_KEY=${DP_PUSH_API_KEY:-changeme}
      - S3_ENDPOINT=http://minio:9000
      - S3_ACCESSKEY=minioadmin
      - S3_SECRETKEY=minioadmin
      - S3_REGION=us-east-1
      - S3_BUCKETNAME=connector-a-bucket
    depends_on:
      - mongo
      - minio
      - connector-a
    networks:
      - connector-network
```

- [ ] **Step 2: Add env vars to `ci/docker/.env`**

```bash
DP_PULL_API_KEY=changeme-pull
DP_PUSH_API_KEY=changeme-push
```

- [ ] **Step 3: Add Dockerfiles for DP apps**

`data-plane-http-pull/Dockerfile`:
```dockerfile
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY target/data-plane-http-pull-*.jar app.jar
COPY target/dependency-jars/ dependency-jars/
ENTRYPOINT ["java", "-jar", "app.jar"]
```

`data-plane-http-push/Dockerfile` — same structure, copy `data-plane-http-push-*.jar`.

- [ ] **Step 4: Commit**

```bash
git add ci/docker/docker-compose.yml ci/docker/.env \
        data-plane-http-pull/Dockerfile data-plane-http-push/Dockerfile
git commit -m "chore(docker): add data-plane-http-pull and data-plane-http-push services to Docker Compose"
```

---

## Phase 8 — Cleanup (parallel pair)

> Tasks 14 and 15 can be executed in parallel.

### Task 14: Remove `DataTransferStrategyFactory` and `DataTransferFormat`

**Files:**
- Delete: `data-transfer/src/main/java/it/eng/datatransfer/service/api/DataTransferStrategyFactory.java`
- Delete: `data-transfer/src/main/java/it/eng/datatransfer/model/DataTransferFormat.java`
- Delete: `data-transfer/src/main/java/it/eng/datatransfer/service/api/DataTransferStrategy.java`
- Modify: `data-transfer/src/main/java/it/eng/datatransfer/configuration/DataTransferConfiguration.java`

- [ ] **Step 1: Remove strategy-related beans from `DataTransferConfiguration`**

In `DataTransferConfiguration.java`, remove:
- The `ThreadPoolTaskExecutor` named `dataTransferTaskExecutor` (moves to each DP app, replaced by virtual-thread executor in Java 21)
- Any `@Bean` method returning `DataTransferStrategy` or `DataTransferStrategyFactory`
- Keep only the `transferTaskScheduler` bean and unrelated beans

- [ ] **Step 2: Delete files**

```bash
rm data-transfer/src/main/java/it/eng/datatransfer/service/api/DataTransferStrategyFactory.java
rm data-transfer/src/main/java/it/eng/datatransfer/model/DataTransferFormat.java
rm data-transfer/src/main/java/it/eng/datatransfer/service/api/DataTransferStrategy.java
```

- [ ] **Step 3: Fix any remaining compilation errors**

```bash
mvn -pl data-transfer compile
```

Fix any `cannot find symbol` errors by removing stale imports or usages.

- [ ] **Step 4: Run tests**

```bash
mvn -pl data-transfer test
```

Expected: all tests pass.

- [ ] **Step 5: Commit**

```bash
git add -A data-transfer/
git commit -m "chore(data-transfer): remove DataTransferStrategyFactory, DataTransferFormat, DataTransferStrategy"
```

---

### Task 15: Remove `HttpPullTransferStrategy` and `HttpPushTransferStrategy`

**Files:**
- Delete: `data-transfer/src/main/java/it/eng/datatransfer/service/api/strategy/HttpPullTransferStrategy.java`
- Delete: `data-transfer/src/main/java/it/eng/datatransfer/service/api/strategy/HttpPushTransferStrategy.java`
- Delete corresponding test files if present

- [ ] **Step 1: Delete files**

```bash
rm data-transfer/src/main/java/it/eng/datatransfer/service/api/strategy/HttpPullTransferStrategy.java
rm data-transfer/src/main/java/it/eng/datatransfer/service/api/strategy/HttpPushTransferStrategy.java
rm -f data-transfer/src/test/java/it/eng/datatransfer/service/api/strategy/HttpPullTransferStrategyTest.java
rm -f data-transfer/src/test/java/it/eng/datatransfer/service/api/strategy/HttpPushTransferStrategyTest.java
```

- [ ] **Step 2: Verify build**

```bash
mvn -pl data-transfer compile
```

- [ ] **Step 3: Run all data-transfer tests**

```bash
mvn -pl data-transfer test
```

- [ ] **Step 4: Full build verification**

```bash
mvn clean verify -pl data-plane-api,data-plane-core,data-plane-http-pull,data-plane-http-push,data-transfer -am -DskipITs
```

Expected: `BUILD SUCCESS` for all modules.

- [ ] **Step 5: Commit**

```bash
git add -A data-transfer/
git commit -m "chore(data-transfer): remove HttpPullTransferStrategy and HttpPushTransferStrategy"
```

---

## Phase 9 — Java 21 Upgrade

### Task 16: Upgrade project to Java 21

**Files:**
- Modify: `pom.xml` (root)

The root `pom.xml` currently sets `<java.version>17</java.version>`. All new Data Plane modules and
Dockerfiles already use Java 21. This task brings the rest of the project in line.

- [ ] **Step 1: Update `<java.version>` in root `pom.xml`**

```xml
<!-- root pom.xml — change line ~14 -->
<java.version>21</java.version>
```

All downstream properties (`maven.compiler.release`, compiler plugin `<release>`) inherit from
`${java.version}`, so this single change is sufficient.

- [ ] **Step 2: Verify full build compiles on Java 21**

```bash
java -version   # confirm JDK 21 is active
mvn clean compile -DskipTests
```

Expected: `BUILD SUCCESS` for all modules with no compiler warnings about source/target.

- [ ] **Step 3: Run unit tests**

```bash
mvn test -DskipITs
```

Expected: all tests pass. Fix any test that relied on Java 17-only behaviour (rare).

- [ ] **Step 4: Run integration tests**

```bash
mvn verify
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 5: Commit**

```bash
git add pom.xml
git commit -m "chore: upgrade Java source/target from 17 to 21"
```

---

## Phase 10 — Documentation

### Task 17: Write technical and user-facing documentation

**Files:**
- Create: `doc/data-plane-signaling-technical.md`
- Create: `doc/data-plane-signaling-user-guide.md`

These two docs follow the same style as `doc/multi-tenant-technical.md` and `doc/multi-tenant-user-guide.md`.

---

#### Step 1: Write `doc/data-plane-signaling-technical.md`

```markdown
# Data Plane Signaling — Technical Reference

## Overview

TRUE Connector implements the
[Eclipse Dataplane Signaling Protocol (DPS)](https://github.com/eclipse-dataplane-signaling/dataplane-signaling).
The Control Plane (CP) and each Data Plane (DP) communicate over REST, with the CP
acting as the orchestrator and each DP as an independent service.

---

## Architecture

```
Consumer CP              Provider CP              Provider DP (HTTP-PULL or HTTP-PUSH)
    |                        |                              |
    |--- TransferRequest ---->|                              |
    |                        |--- POST /dataflows/start --->|
    |                        |                              |--- executes transfer
    |                        |<-- POST /{cpCallback}/status |
    |<-- TransferStartMsg ----|                              |
```

### Modules

| Module | Role | Artifact |
|---|---|---|
| `data-plane-api` | SPI interfaces + DSP message models | Library JAR |
| `data-plane-core` | Shared runtime: registration, routing, client | Library JAR |
| `data-plane-http-pull` | HTTP-PULL standalone service (port 9090) | Spring Boot fat JAR |
| `data-plane-http-push` | HTTP-PUSH standalone service (port 9091) | Spring Boot fat JAR |

### Control Plane additions

| Component | Package | Purpose |
|---|---|---|
| `DataPlaneRegistration` | `it.eng.datatransfer.model` | Persisted DP registration record |
| `DataPlaneRegistrationService` | `it.eng.datatransfer.service` | CRUD + routing logic |
| `DataPlaneRouter` | `it.eng.datatransfer.service` | Selects DP by transfer type |
| `DataPlaneClient` | `it.eng.datatransfer.rest.client` | CP → DP HTTP calls |
| `DataFlowCallbackController` | `it.eng.datatransfer.rest.protocol` | Receives DP status callbacks |
| `DataPlaneRegistrationController` | `it.eng.datatransfer.rest.api` | Admin CRUD for DP registrations |

---

## Data Plane Registration

A DP registers itself with the CP at startup by calling:

```
PUT /api/v1/dataplanes
{
  "endpoint":              "http://dp-http-pull:9090",
  "supportedTransferTypes": ["HttpData-PULL"]
}
```

`ControlPlaneRegistrationBean` (in `data-plane-core`) performs this call with exponential-backoff
retry (5 attempts, base delay 2 s). If `dataplane.control-plane-admin-endpoint` is not set,
registration is skipped (useful for local development without a CP).

The CP stores the registration in the `data_plane_registrations` MongoDB collection and uses it to
route `DataFlowStartMessage` requests.

---

## Transfer Flow

### HTTP-PULL

1. CP receives `TransferRequestMessage` from consumer with `transferType = HttpData-PULL`.
2. CP calls `POST /dataflows/start` on the registered HTTP-PULL DP.
3. DP generates a presigned S3 GET URL and sends `TransferStartMessage` back to the consumer.
4. DP posts `STARTED` status to `POST /{cpCallbackAddress}/{processId}/dataflow/started`.
5. Consumer downloads the artifact directly from the presigned URL.
6. DP posts `COMPLETED` status to CP.

### HTTP-PUSH

1. CP routes `TransferRequestMessage` (type `HttpData-PUSH`) to the HTTP-PUSH DP.
2. DP downloads the artifact from the provider's S3 bucket (presigned GET URL).
3. DP uploads the artifact directly to the consumer's S3 bucket using temporary credentials.
4. DP posts `COMPLETED` to CP.

---

## API Key Authentication

All CP → DP calls carry an `X-Api-Key` header (`DataPlaneRegistration.apiKey`).
All DP → CP callbacks carry an `X-Api-Key` header (`DataPlaneProperties.apiKey`).

On each side, `ApiKeyFilter` validates the header against the stored value. Requests with a missing
or mismatched key receive HTTP 403.

Set API keys in properties:
- CP: stored in `DataPlaneRegistration.apiKey` (written at registration time)
- DP: `dataplane.api-key=<secret>` in `application.properties`

---

## Concurrency Model

Each DP app uses `Executors.newVirtualThreadPerTaskExecutor()` (Java 21 virtual threads).
Each transfer runs on its own virtual thread. There is no fixed pool ceiling — thousands of
concurrent transfers are practical.

---

## OkHttpClient / TLS

Both DP apps component-scan `it.eng.tools`, which auto-configures `OkHttpClient` via
`OkHttpClientConfiguration`:
- `server.ssl.enabled=true` → TLS client with custom truststore (OCSP-validated)
- `server.ssl.enabled=false` → insecure noop client (development only)

See `doc/security.md` for truststore configuration details.

---

## MongoDB Collections

| Collection | Model | Owner |
|---|---|---|
| `data_plane_registrations` | `DataPlaneRegistration` | CP (`data-transfer` module) |

---

## Key Configuration Properties

### Control Plane (`application.properties`)
No new required properties. DP registration is driven by the admin API.

### Data Plane (`application.properties` in each DP app)

| Property | Description | Example |
|---|---|---|
| `dataplane.endpoint` | Public URL of this DP | `http://dp-http-pull:9090` |
| `dataplane.control-plane-admin-endpoint` | CP admin base URL | `http://connector:8080` |
| `dataplane.api-key` | Shared secret for DP↔CP auth | `dp-secret-key` |
| `server.port` | Listening port | `9090` (pull) / `9091` (push) |
| `server.ssl.enabled` | Enable TLS | `true` / `false` |

---

## Extending with a New Data Plane Type

1. Add a new `transferType` constant to `DataTransferProtocol` (in `data-plane-api`).
2. Create a new Spring Boot module (e.g., `data-plane-mqtt`) that depends on `data-plane-core`.
3. Implement `DataTransferProtocol` and annotate with `@Component`.
4. Register the implementation in a `DataTransferProtocolRegistry` `@Bean`.
5. Start the app; it self-registers with the CP on startup.
6. No CP code changes needed — `DataPlaneRouter` selects the correct DP by transfer type.
```

- [ ] **Step 2: Write `doc/data-plane-signaling-user-guide.md`**

```markdown
# Data Plane Signaling — User Guide

## Overview

TRUE Connector uses the **Dataplane Signaling Protocol** to separate orchestration logic
(Control Plane) from actual data movement (Data Plane). You can deploy one or more Data Plane
services independently and scale them as needed.

---

## Concepts

| Term | Description |
|---|---|
| **Control Plane (CP)** | The main connector application that manages negotiations and transfer lifecycle |
| **Data Plane (DP)** | A lightweight service responsible for the actual data transfer |
| **Transfer type** | Protocol used for data movement — `HttpData-PULL` or `HttpData-PUSH` |
| **DP Registration** | A CP record describing where a DP lives and what it supports |

---

## Deployment

The connector ships two ready-made Data Plane images:

| Image | Transfer type | Default port |
|---|---|---|
| `data-plane-http-pull` | `HttpData-PULL` | 9090 |
| `data-plane-http-push` | `HttpData-PUSH` | 9091 |

Both are included in the default Docker Compose at `ci/docker/docker-compose.yml`.

### Minimum required configuration (each DP)

```properties
# Which CP to register with
dataplane.control-plane-admin-endpoint=http://connector:8080

# Public URL of this DP (reachable from CP)
dataplane.endpoint=http://dp-http-pull:9090

# Shared secret — must match what the CP stores for this DP
dataplane.api-key=change-me-in-production
```

---

## Registering a Data Plane Manually

If automatic startup registration is disabled, register a DP via the CP admin API:

```bash
curl -X PUT http://localhost:8080/api/v1/dataplanes   -H "Content-Type: application/json"   -H "Authorization: Basic ..."   -d '{
    "endpoint": "http://my-dp:9090",
    "supportedTransferTypes": ["HttpData-PULL"],
    "apiKey": "my-secret"
  }'
```

### View registered Data Planes

```bash
curl http://localhost:8080/api/v1/dataplanes   -H "Authorization: Basic ..."
```

### Remove a Data Plane

```bash
curl -X DELETE http://localhost:8080/api/v1/dataplanes/{id}   -H "Authorization: Basic ..."
```

---

## Running a Transfer

Transfers work the same as before — initiate via the standard DSP Transfer Request API.
The connector automatically routes the request to the appropriate Data Plane based on the
requested transfer type.

**Consumer side** — request a transfer with `dspace:transferType`:
```json
{
  "@context": "https://w3id.org/dspace/2025/1/context.json",
  "@type": "dspace:TransferRequestMessage",
  "dspace:agreementId": "...",
  "dcat:format": "HttpData-PULL",
  "dspace:dataAddress": {}
}
```

---

## Scaling

Each Data Plane is a stateless Spring Boot application. You can run multiple instances of the
same DP type behind a load balancer. Register each instance separately with the CP:

```bash
# Register replica 1
curl -X PUT http://localhost:8080/api/v1/dataplanes   -d '{"endpoint":"http://dp-pull-1:9090","supportedTransferTypes":["HttpData-PULL"],"apiKey":"..."}'

# Register replica 2  
curl -X PUT http://localhost:8080/api/v1/dataplanes   -d '{"endpoint":"http://dp-pull-2:9090","supportedTransferTypes":["HttpData-PULL"],"apiKey":"..."}'
```

`DataPlaneRouter` selects a DP in round-robin order among registered DPs for the requested transfer type.

---

## Adding a Custom Data Plane

If you have a custom DP implementation compliant with the Dataplane Signaling API spec,
register it the same way as the built-in DPs using `PUT /api/v1/dataplanes`.
Your DP must implement:

- `POST /dataflows/start` — start a transfer
- `POST /dataflows/{id}/stop` — stop a transfer
- `POST /{cpCallback}/{processId}/dataflow/{state}` — send status callbacks to CP

See `doc/data-plane-signaling-technical.md` for the full API contract.

---

## Troubleshooting

| Symptom | Likely cause | Fix |
|---|---|---|
| Transfer stays in `REQUESTED` state | No DP registered for that transfer type | Register a DP via admin API |
| DP logs "CP registration failed" on startup | CP not reachable or wrong endpoint configured | Check `dataplane.control-plane-admin-endpoint` |
| CP rejects DP callbacks with HTTP 403 | API key mismatch | Verify `dataplane.api-key` matches `DataPlaneRegistration.apiKey` on CP |
| DP logs "CP registration rejected with HTTP 401" | CP API requires auth but no credentials configured | Add `dataplane.control-plane-api-user` / `dataplane.control-plane-api-password` (if supported) |
```

- [ ] **Step 3: Commit documentation**

```bash
git add doc/data-plane-signaling-technical.md doc/data-plane-signaling-user-guide.md
git commit -m "docs: add data-plane signaling technical reference and user guide"
```

---

## Phase 11 — Changelog

### Task 18: Update `CHANGELOG.md`

**Files:**
- Modify: `CHANGELOG.md`

Add a new `[Unreleased]` entry (or extend the existing one if it is still open) with all notable
changes introduced by the Data Plane Signaling implementation.

- [ ] **Step 1: Add the DPS section to `CHANGELOG.md`**

Insert the following block at the top of `CHANGELOG.md`, directly after the `# Changelog` header
(or merge into the existing `[Unreleased]` block if one exists):

```markdown
## [Unreleased] — Dataplane Signaling Protocol

### Added
- **Dataplane Signaling Protocol (DPS)** — TRUE Connector now implements the
  [Eclipse Dataplane Signaling Protocol](https://github.com/eclipse-dataplane-signaling/dataplane-signaling),
  decoupling data-movement logic from the Control Plane orchestration.
- `data-plane-api` module — SPI interfaces (`DataTransferProtocol`) and DSP message models
  (`DataFlowStartMessage`, `DataFlowPrepareMessage`, `DataFlowStatusMessage`, `DataFlow`).
- `data-plane-core` module — shared runtime library: `DataTransferProtocolRegistry`,
  `ControlPlaneClient`, `ControlPlaneRegistrationBean` (startup self-registration with retry),
  `DataPlaneProperties`.
- `data-plane-http-pull` module — standalone HTTP-PULL Data Plane Spring Boot application
  (default port 9090). Generates presigned S3 GET URLs and streams artifacts to consumers.
  Uses Java 21 virtual threads for concurrent transfers.
- `data-plane-http-push` module — standalone HTTP-PUSH Data Plane Spring Boot application
  (default port 9091). Pushes provider artifacts directly to consumer S3 buckets using
  temporary IAM credentials. Uses Java 21 virtual threads for concurrent transfers.
- `DataPlaneRegistration` model, repository, service, and admin controller (`/api/v1/dataplanes`)
  for managing registered Data Plane services on the Control Plane side.
- `DataPlaneClient` + `DataPlaneRouter` — CP-side components for routing and dispatching
  `DataFlowStartMessage` to the correct Data Plane.
- `DataFlowCallbackController` — CP-side endpoint receiving status callbacks
  (`STARTED`, `COMPLETED`, `FAILED`, `TERMINATED`) from Data Planes.
- API key authentication for all CP ↔ DP traffic (`X-Api-Key` header + `ApiKeyFilter`).
- Docker Compose services for `data-plane-http-pull` and `data-plane-http-push` in
  `ci/docker/docker-compose.yml`.
- `doc/data-plane-signaling-technical.md` — developer and architecture reference.
- `doc/data-plane-signaling-user-guide.md` — operator manual covering deployment, registration,
  scaling, and troubleshooting.

### Changed
- `DataTransferAPIService` — transfer dispatch now routes through `DataPlaneClient` instead of
  calling `HttpPullTransferStrategy` / `HttpPushTransferStrategy` directly.
- Java source/target level upgraded from **17 to 21**. All modules now compile with
  `--release 21`. Docker base images updated to `eclipse-temurin:21-jre-jammy`.

### Removed
- `DataTransferStrategyFactory` and `DataTransferFormat` (replaced by `DataPlaneRouter` +
  `DataPlaneRegistration`).
- `HttpPullTransferStrategy` and `HttpPushTransferStrategy` from `data-transfer` module
  (logic moved into `data-plane-http-pull` and `data-plane-http-push` respectively).
```

- [ ] **Step 2: Commit**

```bash
git add CHANGELOG.md
git commit -m "docs(changelog): add Dataplane Signaling Protocol release notes"
```


---

## Final Verification

- [ ] **Run full build**

```bash
mvn clean verify -DskipITs
```

Expected: `BUILD SUCCESS` for all modules including `connector`.

- [ ] **Start environment and run API tests**

```bash
docker compose -f ci/docker/docker-compose.yml --env-file ci/docker/.env up -d
# Wait for all services to be healthy
sleep 30
newman run ci/docker/test-cases/api-tests/api-endpoints-tests.json
newman run ci/docker/test-cases/transfer-tests/transfer-process-tests.json
docker compose -f ci/docker/docker-compose.yml --env-file ci/docker/.env down -v
```

- [ ] **Create integration test for end-to-end DPS flow** (optional but recommended)

Add `DataPlaneSignalingIT.java` in `connector/src/test/java/it/eng/connector/integration/` that:
1. Registers a mock DP via `PUT /api/v1/dataplanes`
2. Initiates a transfer
3. Verifies CP calls `POST /dataflows/start` on the mock DP (via WireMock)
4. Sends `COMPLETED` callback to CP
5. Verifies TransferProcess is `COMPLETED`

---

## Parallelization Summary

| Phase | Tasks | Can run in parallel |
|---|---|---|
| Phase 1 | Task 1 → 2 → 3 | Sequential |
| Phase 2 | Task 4 + Task 5 | ✅ Parallel after Task 3 |
| Phase 3 | Task 6 + Task 7 + Task 8 | ✅ Parallel after Task 3 |
| Phase 4 | Task 9 | After Tasks 4–8 |
| Phase 5 | Task 10 | After Task 3 |
| Phase 6 | Task 11 + Task 12 | ✅ Parallel after Task 9 |
| Phase 7 | Task 13 | After Task 9 |
| Phase 8 | Task 14 + Task 15 | ✅ Parallel after Task 9 |
| Phase 9 | Task 16 (Java 21 upgrade) | After Phase 1 complete |
| Phase 10 | Task 17 (documentation) | ✅ Parallel with Phase 9 |
| Phase 11 | Task 18 (CHANGELOG) | After all other phases complete |
