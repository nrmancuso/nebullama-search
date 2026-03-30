package com.example.nebullamasearch.config;

import static org.junit.jupiter.api.Assertions.*;

import com.example.nebullamasearch.domain.ResourceType;
import java.io.IOException;
import java.time.Duration;
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

@SpringBootTest
@Testcontainers
class IndexInitializerTest {

  @Container
  static GenericContainer<?> opensearch =
      new GenericContainer<>(DockerImageName.parse("opensearchproject/opensearch:2.13.0"))
          .withEnv("discovery.type", "single-node")
          .withEnv("DISABLE_SECURITY_PLUGIN", "true")
          .withEnv("OPENSEARCH_JAVA_OPTS", "-Xms512m -Xmx512m")
          .withExposedPorts(9200)
          .waitingFor(
              Wait.forHttp("/_cluster/health")
                  .forStatusCode(200)
                  .withStartupTimeout(Duration.ofMinutes(3)));

  @DynamicPropertySource
  static void opensearchProperties(DynamicPropertyRegistry registry) {
    registry.add("opensearch.host", opensearch::getHost);
    registry.add("opensearch.port", () -> opensearch.getMappedPort(9200));
    registry.add("opensearch.scheme", () -> "http");
    registry.add("ollama.base-url", () -> "http://localhost:1");
  }

  @Autowired private OpenSearchClient client;

  @Autowired private IndexInitializer initializer;

  @Test
  void allFiveIndexesCreatedOnStartup() throws IOException {
    for (ResourceType type : ResourceType.values()) {
      boolean exists = client.indices().exists(r -> r.index(type.indexName())).value();
      assertTrue(exists, "Expected index '" + type.indexName() + "' to exist after startup");
    }
  }

  @Test
  void celestialObjectsIndexHasEmbeddingField() throws IOException {
    var response = client.indices().getMapping(r -> r.index("celestial_objects"));
    var properties = response.result().get("celestial_objects").mappings().properties();
    assertTrue(
        properties.containsKey("embedding"),
        "celestial_objects index should have an 'embedding' field");
  }

  @Test
  void startupIsIdempotent() {
    assertDoesNotThrow(
        () -> initializer.run(null), "Running IndexInitializer a second time should not throw");
  }

  @Test
  void hybridPipelineCreatedOnStartup() throws IOException {
    try (org.opensearch.client.opensearch.generic.Response response =
        client
            .generic()
            .execute(
                org.opensearch.client.opensearch.generic.Requests.builder()
                    .method("GET")
                    .endpoint("/_search/pipeline/hybrid-pipeline")
                    .build())) {
      assertEquals(200, response.getStatus(), "hybrid-pipeline should exist after startup");
    }
  }
}
