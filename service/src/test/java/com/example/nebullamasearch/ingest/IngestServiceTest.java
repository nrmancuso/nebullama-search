package com.example.nebullamasearch.ingest;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.nebullamasearch.domain.ResourceType;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch.core.GetResponse;
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
@Execution(ExecutionMode.SAME_THREAD)
class IngestServiceTest {

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

  static WireMockServer wireMock;

  @BeforeAll
  static void startWireMock() {
    wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
    wireMock.start();
  }

  @AfterAll
  static void stopWireMock() {
    wireMock.stop();
  }

  @DynamicPropertySource
  static void overrideProperties(DynamicPropertyRegistry registry) {
    registry.add("opensearch.host", opensearch::getHost);
    registry.add("opensearch.port", () -> opensearch.getMappedPort(9200));
    registry.add("opensearch.scheme", () -> "http");
    registry.add("ollama.base-url", () -> "http://localhost:" + wireMock.port());
  }

  @Autowired IngestService ingestService;

  @Autowired OpenSearchClient openSearchClient;

  private static String embeddingResponseBody() {
    StringBuilder sb = new StringBuilder("{\"embedding\":[");
    for (int i = 0; i < 768; i++) {
      sb.append("0.1");
      if (i < 767) sb.append(",");
    }
    sb.append("]}");
    return sb.toString();
  }

  @BeforeEach
  void resetWireMock() {
    wireMock.resetAll();
    // Default stub - successful embedding
    wireMock.stubFor(
        post(urlEqualTo("/api/embeddings"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(embeddingResponseBody())));
  }

  @Test
  void singleIngest_writesDocumentWithEmbeddingToOpenSearch() throws Exception {
    /*
     * Request: curl -X POST http://localhost:8080/api/v1/ingest/CELESTIAL_OBJECTS \
     *   -H "Content-Type: application/json" \
     *   -d '{"name":"Crab Nebula","object_type":"nebula",...}'
     */
    Map<String, Object> doc =
        Map.of(
            "name", "Crab Nebula",
            "object_type", "nebula",
            "description", "Supernova remnant in Taurus",
            "constellation", "Taurus");

    IngestResult result = ingestService.ingestOne(ResourceType.CELESTIAL_OBJECTS, doc);

    assertThat(result.success()).isTrue();
    assertThat(result.id()).isNotBlank();

    GetResponse<Map> response =
        openSearchClient.get(g -> g.index("celestial_objects").id(result.id()), Map.class);
    assertThat(response.found()).isTrue();
    @SuppressWarnings("unchecked")
    Map<String, Object> source = (Map<String, Object>) response.source();
    assertThat(source).containsKey("embedding");
    assertThat(source.get("description")).isEqualTo("Supernova remnant in Taurus");
  }

  @Test
  void bulkIngest_writesAllDocuments() throws Exception {
    /*
     * Request: curl -X POST http://localhost:8080/api/v1/ingest/MISSIONS/bulk \
     *   -H "Content-Type: application/json" \
     *   -d '[{"name":"Hubble...","description":"..."},...}'
     */
    List<Map<String, Object>> docs =
        List.of(
            Map.of(
                "name", "Hubble Space Telescope",
                "description", "NASA observatory",
                "status", "active"),
            Map.of(
                "name", "James Webb Space Telescope",
                "description", "Next-gen infrared",
                "status", "active"),
            Map.of(
                "name", "Chandra X-ray Observatory",
                "description", "X-ray telescope",
                "status", "active"));

    List<IngestResult> results = ingestService.ingestBulk(ResourceType.MISSIONS, docs);

    assertThat(results).hasSize(3);
    assertThat(results).allMatch(IngestResult::success);
    assertThat(results).allMatch(r -> r.id() != null && !r.id().isBlank());
  }

  @Test
  void bulkIngest_withEmbeddingErrors_returnsFailedResults() {
    /*
     * Request: curl -X POST http://localhost:8080/api/v1/ingest/ASTRONOMERS/bulk \
     *   -H "Content-Type: application/json" \
     *   -d '[{"name":"Person1","biography":"..."},...}'
     * Tests partial failure scenario (some docs fail, others succeed)
     */
    // Reset and setup error responses only for this test
    wireMock.resetAll();
    wireMock.stubFor(
        post(urlEqualTo("/api/embeddings"))
            .willReturn(aResponse().withStatus(500).withBody("Embedding service error")));

    List<Map<String, Object>> docs =
        List.of(
            Map.of("name", "Jocelyn Bell Burnell", "biography", "Discovered pulsars"),
            Map.of("name", "Carl Sagan", "biography", "Cosmos series host"));

    List<IngestResult> results = ingestService.ingestBulk(ResourceType.ASTRONOMERS, docs);

    assertThat(results).hasSize(2);
    long failures = results.stream().filter(r -> !r.success()).count();
    assertThat(failures).isEqualTo(2);
    results.stream().filter(r -> !r.success()).forEach(r -> assertThat(r.error()).isNotBlank());
  }

  @Test
  void singleIngest_usesProvidedEmbeddingWithoutCallingOllama() throws Exception {
    List<Double> providedEmbedding = new ArrayList<>(768);
    for (int i = 0; i < 768; i++) {
      providedEmbedding.add(i / 1000.0);
    }

    Map<String, Object> doc =
        Map.of(
            "name", "Crab Nebula",
            "object_type", "nebula",
            "description", "Supernova remnant in Taurus",
            "constellation", "Taurus",
            "embedding", providedEmbedding);

    IngestResult result = ingestService.ingestOne(ResourceType.CELESTIAL_OBJECTS, doc);

    assertThat(result.success()).isTrue();

    GetResponse<Map> response =
        openSearchClient.get(g -> g.index("celestial_objects").id(result.id()), Map.class);
    assertThat(response.found()).isTrue();
    @SuppressWarnings("unchecked")
    Map<String, Object> source = (Map<String, Object>) response.source();
    assertThat(source.get("embedding")).isEqualTo(providedEmbedding);
    wireMock.verify(0, postRequestedFor(urlEqualTo("/api/embeddings")));
  }
}
