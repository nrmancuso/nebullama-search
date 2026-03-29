# Issue #5: OpenSearch Index Configuration & Mappings Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Auto-create five astronomy OpenSearch indexes with k-NN vector mappings on Spring Boot startup.

**Architecture:** Mapping JSON files live on the classpath under `resources/opensearch/`. An `ApplicationRunner` bean iterates `ResourceType.values()`, checks existence, and creates any missing index by loading the corresponding JSON file via opensearch-java's `withJson(InputStream)`. All operations are idempotent — existing indexes are skipped.

**Tech Stack:** Java 21, Spring Boot 3.3.x, opensearch-java 2.10.3, Apache HttpClient 5, Testcontainers (GenericContainer with `opensearchproject/opensearch:2.13.0`), JUnit 5.

---

## File Map

| File | Action | Responsibility |
|------|--------|----------------|
| `service/src/main/resources/opensearch/celestial_objects.json` | Create | Index settings + field mappings for celestial objects |
| `service/src/main/resources/opensearch/missions.json` | Create | Index settings + field mappings for missions |
| `service/src/main/resources/opensearch/observations.json` | Create | Index settings + field mappings for observations |
| `service/src/main/resources/opensearch/astronomers.json` | Create | Index settings + field mappings for astronomers |
| `service/src/main/resources/opensearch/publications.json` | Create | Index settings + field mappings for publications |
| `service/src/main/java/com/example/nebullamasearch/domain/ResourceType.java` | Create | Enum mapping constants to index names |
| `service/src/main/java/com/example/nebullamasearch/domain/CelestialObject.java` | Create | POJO for celestial objects documents |
| `service/src/main/java/com/example/nebullamasearch/domain/Mission.java` | Create | POJO for mission documents |
| `service/src/main/java/com/example/nebullamasearch/domain/Observation.java` | Create | POJO for observation documents |
| `service/src/main/java/com/example/nebullamasearch/domain/Astronomer.java` | Create | POJO for astronomer documents |
| `service/src/main/java/com/example/nebullamasearch/domain/Publication.java` | Create | POJO for publication documents |
| `service/src/main/java/com/example/nebullamasearch/config/OpenSearchProperties.java` | Create | `@ConfigurationProperties` binding for opensearch config |
| `service/src/main/java/com/example/nebullamasearch/config/OpenSearchConfig.java` | Create | `@Bean OpenSearchClient` factory |
| `service/src/main/java/com/example/nebullamasearch/config/IndexInitializer.java` | Create | `ApplicationRunner` that creates missing indexes at startup |
| `service/src/main/java/com/example/nebullamasearch/NebullamaSearchApplication.java` | Modify | Add `@ConfigurationPropertiesScan` |
| `service/src/test/java/com/example/nebullamasearch/config/IndexInitializerTest.java` | Create | Testcontainers integration test |

---

### Task 1: OpenSearch Mapping JSON Files

**Files:**

- Create: `service/src/main/resources/opensearch/celestial_objects.json`
- Create: `service/src/main/resources/opensearch/missions.json`
- Create: `service/src/main/resources/opensearch/observations.json`
- Create: `service/src/main/resources/opensearch/astronomers.json`
- Create: `service/src/main/resources/opensearch/publications.json`

- [ ] **Step 1: Create celestial_objects.json**

Create `service/src/main/resources/opensearch/celestial_objects.json`:

```json
{
  "settings": {
    "index": {
      "knn": true,
      "number_of_shards": 1,
      "number_of_replicas": 0
    }
  },
  "mappings": {
    "properties": {
      "resource_type":    { "type": "keyword" },
      "name":             { "type": "text" },
      "designations":     { "type": "text" },
      "object_type":      { "type": "keyword" },
      "constellation":    { "type": "keyword" },
      "distance_ly":      { "type": "double" },
      "description":      { "type": "text" },
      "discovered_by":    { "type": "keyword" },
      "discovery_year":   { "type": "integer" },
      "embedding": {
        "type": "knn_vector",
        "dimension": 768,
        "method": {
          "name": "hnsw",
          "space_type": "cosinesimil",
          "engine": "lucene",
          "parameters": {
            "ef_construction": 128,
            "m": 16
          }
        }
      }
    }
  }
}
```

- [ ] **Step 2: Create missions.json**

Create `service/src/main/resources/opensearch/missions.json`:

```json
{
  "settings": {
    "index": {
      "knn": true,
      "number_of_shards": 1,
      "number_of_replicas": 0
    }
  },
  "mappings": {
    "properties": {
      "resource_type":  { "type": "keyword" },
      "name":           { "type": "text" },
      "agency":         { "type": "keyword" },
      "mission_type":   { "type": "keyword" },
      "launch_year":    { "type": "integer" },
      "status":         { "type": "keyword" },
      "targets":        { "type": "keyword" },
      "description":    { "type": "text" },
      "embedding": {
        "type": "knn_vector",
        "dimension": 768,
        "method": {
          "name": "hnsw",
          "space_type": "cosinesimil",
          "engine": "lucene",
          "parameters": {
            "ef_construction": 128,
            "m": 16
          }
        }
      }
    }
  }
}
```

- [ ] **Step 3: Create observations.json**

Create `service/src/main/resources/opensearch/observations.json`:

```json
{
  "settings": {
    "index": {
      "knn": true,
      "number_of_shards": 1,
      "number_of_replicas": 0
    }
  },
  "mappings": {
    "properties": {
      "resource_type":      { "type": "keyword" },
      "target_name":        { "type": "text" },
      "instrument":         { "type": "keyword" },
      "observatory":        { "type": "keyword" },
      "observation_date":   { "type": "date" },
      "wavelength_band":    { "type": "keyword" },
      "notes":              { "type": "text" },
      "embedding": {
        "type": "knn_vector",
        "dimension": 768,
        "method": {
          "name": "hnsw",
          "space_type": "cosinesimil",
          "engine": "lucene",
          "parameters": {
            "ef_construction": 128,
            "m": 16
          }
        }
      }
    }
  }
}
```

- [ ] **Step 4: Create astronomers.json**

Create `service/src/main/resources/opensearch/astronomers.json`:

```json
{
  "settings": {
    "index": {
      "knn": true,
      "number_of_shards": 1,
      "number_of_replicas": 0
    }
  },
  "mappings": {
    "properties": {
      "resource_type":        { "type": "keyword" },
      "name":                 { "type": "text" },
      "birth_year":           { "type": "integer" },
      "death_year":           { "type": "integer" },
      "nationality":          { "type": "keyword" },
      "known_for":            { "type": "text" },
      "associated_objects":   { "type": "keyword" },
      "associated_missions":  { "type": "keyword" },
      "biography":            { "type": "text" },
      "embedding": {
        "type": "knn_vector",
        "dimension": 768,
        "method": {
          "name": "hnsw",
          "space_type": "cosinesimil",
          "engine": "lucene",
          "parameters": {
            "ef_construction": 128,
            "m": 16
          }
        }
      }
    }
  }
}
```

- [ ] **Step 5: Create publications.json**

Create `service/src/main/resources/opensearch/publications.json`:

```json
{
  "settings": {
    "index": {
      "knn": true,
      "number_of_shards": 1,
      "number_of_replicas": 0
    }
  },
  "mappings": {
    "properties": {
      "resource_type": { "type": "keyword" },
      "title":         { "type": "text" },
      "authors":       { "type": "keyword" },
      "year":          { "type": "integer" },
      "journal":       { "type": "keyword" },
      "abstract":      { "type": "text" },
      "topics":        { "type": "keyword" },
      "doi":           { "type": "keyword" },
      "embedding": {
        "type": "knn_vector",
        "dimension": 768,
        "method": {
          "name": "hnsw",
          "space_type": "cosinesimil",
          "engine": "lucene",
          "parameters": {
            "ef_construction": 128,
            "m": 16
          }
        }
      }
    }
  }
}
```

- [ ] **Step 6: Commit**

```bash
git add service/src/main/resources/opensearch/
git commit -m "Issue #5: add OpenSearch index mapping files (768-dim k-NN)"
```

Expected: commit succeeds. Verify with `git log --oneline -1`.

---

### Task 2: ResourceType Enum & Domain POJOs

**Files:**

- Create: `service/src/main/java/com/example/nebullamasearch/domain/ResourceType.java`
- Create: `service/src/main/java/com/example/nebullamasearch/domain/CelestialObject.java`
- Create: `service/src/main/java/com/example/nebullamasearch/domain/Mission.java`
- Create: `service/src/main/java/com/example/nebullamasearch/domain/Observation.java`
- Create: `service/src/main/java/com/example/nebullamasearch/domain/Astronomer.java`
- Create: `service/src/main/java/com/example/nebullamasearch/domain/Publication.java`

- [ ] **Step 1: Create ResourceType.java**

Create `service/src/main/java/com/example/nebullamasearch/domain/ResourceType.java`:

```java
package com.example.nebullamasearch.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum ResourceType {

    CELESTIAL_OBJECTS("celestial_objects"),
    MISSIONS("missions"),
    OBSERVATIONS("observations"),
    ASTRONOMERS("astronomers"),
    PUBLICATIONS("publications");

    private final String indexName;

    ResourceType(String indexName) {
        this.indexName = indexName;
    }

    public String indexName() {
        return indexName;
    }

    @JsonValue
    public String toValue() {
        return name();
    }

    @JsonCreator
    public static ResourceType fromValue(String value) {
        for (ResourceType type : values()) {
            if (type.name().equalsIgnoreCase(value) || type.indexName.equalsIgnoreCase(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown ResourceType: " + value);
    }

    public static ResourceType fromIndexName(String indexName) {
        for (ResourceType type : values()) {
            if (type.indexName.equals(indexName)) {
                return type;
            }
        }
        throw new IllegalArgumentException("No ResourceType for index: " + indexName);
    }
}
```

- [ ] **Step 2: Create CelestialObject.java**

Create `service/src/main/java/com/example/nebullamasearch/domain/CelestialObject.java`:

```java
package com.example.nebullamasearch.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class CelestialObject {
    public String id;
    public String name;
    public List<String> designations;
    @JsonProperty("object_type")   public String objectType;
    public String constellation;
    @JsonProperty("distance_ly")   public Double distanceLy;
    public String description;
    @JsonProperty("discovered_by") public String discoveredBy;
    @JsonProperty("discovery_year") public Integer discoveryYear;
    @JsonProperty("resource_type") public String resourceType;
    public float[] embedding;
}
```

- [ ] **Step 3: Create Mission.java**

Create `service/src/main/java/com/example/nebullamasearch/domain/Mission.java`:

```java
package com.example.nebullamasearch.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class Mission {
    public String id;
    public String name;
    public String agency;
    @JsonProperty("mission_type")  public String missionType;
    @JsonProperty("launch_year")   public Integer launchYear;
    public String status;
    public List<String> targets;
    public String description;
    @JsonProperty("resource_type") public String resourceType;
    public float[] embedding;
}
```

- [ ] **Step 4: Create Observation.java**

Create `service/src/main/java/com/example/nebullamasearch/domain/Observation.java`:

```java
package com.example.nebullamasearch.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Observation {
    public String id;
    @JsonProperty("target_name")      public String targetName;
    public String instrument;
    public String observatory;
    @JsonProperty("observation_date") public String observationDate;
    @JsonProperty("wavelength_band")  public String wavelengthBand;
    public String notes;
    @JsonProperty("resource_type")    public String resourceType;
    public float[] embedding;
}
```

- [ ] **Step 5: Create Astronomer.java**

Create `service/src/main/java/com/example/nebullamasearch/domain/Astronomer.java`:

```java
package com.example.nebullamasearch.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class Astronomer {
    public String id;
    public String name;
    @JsonProperty("birth_year")           public Integer birthYear;
    @JsonProperty("death_year")           public Integer deathYear;
    public String nationality;
    @JsonProperty("known_for")            public String knownFor;
    @JsonProperty("associated_objects")   public List<String> associatedObjects;
    @JsonProperty("associated_missions")  public List<String> associatedMissions;
    public String biography;
    @JsonProperty("resource_type")        public String resourceType;
    public float[] embedding;
}
```

- [ ] **Step 6: Create Publication.java**

Create `service/src/main/java/com/example/nebullamasearch/domain/Publication.java`:

```java
package com.example.nebullamasearch.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class Publication {
    public String id;
    public String title;
    public List<String> authors;
    public Integer year;
    public String journal;
    @JsonProperty("abstract")      public String abstractText;
    public List<String> topics;
    public String doi;
    @JsonProperty("resource_type") public String resourceType;
    public float[] embedding;
}
```

- [ ] **Step 7: Verify compilation**

```bash
cd service && ./gradlew compileJava
```

Expected output ends with `BUILD SUCCESSFUL`.

- [ ] **Step 8: Commit**

```bash
git add service/src/main/java/com/example/nebullamasearch/domain/
git commit -m "Issue #5: add ResourceType enum and domain POJOs"
```

---

### Task 3: OpenSearch Client Configuration

**Files:**

- Create: `service/src/main/java/com/example/nebullamasearch/config/OpenSearchProperties.java`
- Create: `service/src/main/java/com/example/nebullamasearch/config/OpenSearchConfig.java`
- Modify: `service/src/main/java/com/example/nebullamasearch/NebullamaSearchApplication.java`

- [ ] **Step 1: Create OpenSearchProperties.java**

Create `service/src/main/java/com/example/nebullamasearch/config/OpenSearchProperties.java`:

```java
package com.example.nebullamasearch.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "opensearch")
public class OpenSearchProperties {
    private String host = "localhost";
    private int port = 9200;
    private String scheme = "http";

    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }
    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }
    public String getScheme() { return scheme; }
    public void setScheme(String scheme) { this.scheme = scheme; }
}
```

- [ ] **Step 2: Create OpenSearchConfig.java**

Create `service/src/main/java/com/example/nebullamasearch/config/OpenSearchConfig.java`:

```java
package com.example.nebullamasearch.config;

import org.apache.hc.core5.http.HttpHost;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5TransportBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenSearchConfig {

    @Bean
    public OpenSearchClient openSearchClient(OpenSearchProperties props) {
        HttpHost host = new HttpHost(props.getScheme(), props.getHost(), props.getPort());
        var transport = ApacheHttpClient5TransportBuilder
                .builder(host)
                .build();
        return new OpenSearchClient(transport);
    }
}
```

- [ ] **Step 3: Add @ConfigurationPropertiesScan to main application class**

Modify `service/src/main/java/com/example/nebullamasearch/NebullamaSearchApplication.java`:

```java
package com.example.nebullamasearch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class NebullamaSearchApplication {

    public static void main(String[] args) {
        SpringApplication.run(NebullamaSearchApplication.class, args);
    }
}
```

- [ ] **Step 4: Verify compilation**

```bash
cd service && ./gradlew compileJava
```

Expected output ends with `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add service/src/main/java/com/example/nebullamasearch/config/OpenSearchProperties.java \
        service/src/main/java/com/example/nebullamasearch/config/OpenSearchConfig.java \
        service/src/main/java/com/example/nebullamasearch/NebullamaSearchApplication.java
git commit -m "Issue #5: add OpenSearch client config bean and properties"
```

---

### Task 4: IndexInitializer — TDD

**Files:**

- Create: `service/src/test/java/com/example/nebullamasearch/config/IndexInitializerTest.java`
- Create: `service/src/main/java/com/example/nebullamasearch/config/IndexInitializer.java`

- [ ] **Step 1: Write the failing test**

Create `service/src/test/java/com/example/nebullamasearch/config/IndexInitializerTest.java`:

```java
package com.example.nebullamasearch.config;

import com.example.nebullamasearch.domain.ResourceType;
import org.junit.jupiter.api.Test;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers
class IndexInitializerTest {

    @Container
    static GenericContainer<?> opensearch = new GenericContainer<>(
            DockerImageName.parse("opensearchproject/opensearch:2.13.0"))
            .withEnv("discovery.type", "single-node")
            .withEnv("DISABLE_SECURITY_PLUGIN", "true")
            .withEnv("OPENSEARCH_JAVA_OPTS", "-Xms512m -Xmx512m")
            .withExposedPorts(9200)
            .waitingFor(Wait.forHttp("/_cluster/health")
                    .forStatusCode(200)
                    .withStartupTimeout(Duration.ofMinutes(3)));

    @DynamicPropertySource
    static void opensearchProperties(DynamicPropertyRegistry registry) {
        registry.add("opensearch.host", opensearch::getHost);
        registry.add("opensearch.port", () -> opensearch.getMappedPort(9200));
        registry.add("opensearch.scheme", () -> "http");
        registry.add("ollama.base-url", () -> "http://localhost:1");
    }

    @Autowired
    private OpenSearchClient client;

    @Autowired
    private IndexInitializer initializer;

    @Test
    void allFiveIndexesCreatedOnStartup() throws IOException {
        for (ResourceType type : ResourceType.values()) {
            boolean exists = client.indices()
                    .exists(r -> r.index(type.indexName()))
                    .value();
            assertTrue(exists, "Expected index '" + type.indexName() + "' to exist after startup");
        }
    }

    @Test
    void celestialObjectsIndexHasEmbeddingField() throws IOException {
        var response = client.indices().getMapping(r -> r.index("celestial_objects"));
        var properties = response.result().get("celestial_objects").mappings().properties();
        assertTrue(properties.containsKey("embedding"),
                "celestial_objects index should have an 'embedding' field");
    }

    @Test
    void startupIsIdempotent() {
        assertDoesNotThrow(() -> initializer.run(null),
                "Running IndexInitializer a second time should not throw");
    }
}
```

- [ ] **Step 2: Run test — confirm it fails**

```bash
cd service && ./gradlew test --tests "*.IndexInitializerTest"
```

Expected: FAIL — compilation error because `IndexInitializer` does not exist yet.

- [ ] **Step 3: Create IndexInitializer.java**

Create `service/src/main/java/com/example/nebullamasearch/config/IndexInitializer.java`:

```java
package com.example.nebullamasearch.config;

import com.example.nebullamasearch.domain.ResourceType;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;

@Component
public class IndexInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(IndexInitializer.class);

    private final OpenSearchClient client;

    public IndexInitializer(OpenSearchClient client) {
        this.client = client;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        for (ResourceType type : ResourceType.values()) {
            createIndexIfAbsent(type.indexName());
        }
    }

    private void createIndexIfAbsent(String indexName) throws IOException {
        boolean exists = client.indices()
                .exists(r -> r.index(indexName))
                .value();

        if (exists) {
            log.info("Index '{}' already exists — skipping creation", indexName);
            return;
        }

        InputStream mappingJson = getClass()
                .getResourceAsStream("/opensearch/" + indexName + ".json");

        if (mappingJson == null) {
            throw new IllegalStateException(
                    "No mapping file found on classpath: /opensearch/" + indexName + ".json");
        }

        client.indices().create(r -> r.index(indexName).withJson(mappingJson));
        log.info("Created index '{}'", indexName);
    }
}
```

- [ ] **Step 4: Run tests — confirm they pass**

```bash
cd service && ./gradlew test --tests "*.IndexInitializerTest"
```

Expected: all three tests PASS. Note: the first run downloads the OpenSearch Docker image (~1 GB) — subsequent runs use the cached image.

- [ ] **Step 5: Run the full test suite**

```bash
cd service && ./gradlew test
```

Expected: `BUILD SUCCESSFUL`, all tests pass including `NebullamaSearchApplicationTests`.

- [ ] **Step 6: Commit**

```bash
git add service/src/main/java/com/example/nebullamasearch/config/IndexInitializer.java \
        service/src/test/java/com/example/nebullamasearch/config/IndexInitializerTest.java
git commit -m "Issue #5: add IndexInitializer with Testcontainers integration test"
```
