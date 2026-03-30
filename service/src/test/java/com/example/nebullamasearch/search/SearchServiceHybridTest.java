package com.example.nebullamasearch.search;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.nebullamasearch.config.OllamaProperties;
import com.example.nebullamasearch.domain.ResourceType;
import com.example.nebullamasearch.ingest.OllamaEmbeddingService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import jakarta.json.stream.JsonParser;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.hc.core5.http.HttpHost;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.opensearch.client.json.JsonpMapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.mapping.TypeMapping;
import org.opensearch.client.opensearch.generic.Body;
import org.opensearch.client.opensearch.generic.Requests;
import org.opensearch.client.opensearch.indices.CreateIndexRequest;
import org.opensearch.client.opensearch.indices.IndexSettings;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5Transport;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5TransportBuilder;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@Execution(ExecutionMode.SAME_THREAD)
class SearchServiceHybridTest {

  @Container
  static GenericContainer<?> openSearch =
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
  static OpenSearchClient openSearchClient;
  private static final ObjectMapper objectMapper = new ObjectMapper();

  private OllamaEmbeddingService embeddingService;
  private SearchService searchService;

  @BeforeAll
  static void startInfrastructure() throws Exception {
    wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
    wireMock.start();

    final HttpHost host =
        new HttpHost("http", openSearch.getHost(), openSearch.getMappedPort(9200));
    final ApacheHttpClient5Transport transport =
        ApacheHttpClient5TransportBuilder.builder(host).build();
    openSearchClient = new OpenSearchClient(transport);

    createAllIndexes();
    createHybridPipeline();
    indexAllDocuments();
  }

  @AfterAll
  static void stopInfrastructure() {
    wireMock.stop();
  }

  @BeforeEach
  void setUp() {
    wireMock.resetAll();
    final OllamaProperties props =
        new OllamaProperties(
            "http://localhost:" + wireMock.port(), "nomic-embed-text", "mistral", 5000, 10000);
    embeddingService = new OllamaEmbeddingService(props, new ObjectMapper());
    searchService = new SearchService(openSearchClient, embeddingService);
  }

  // -------------------------------------------------------------------------
  // Tests
  // -------------------------------------------------------------------------

  @Test
  void hybridSearchReturnsResults() throws IOException {
    stubQueryVector("exploding star remnants", TestVectors.QUERY_EXPLODING_STAR_REMNANTS);

    final SearchRequest request =
        new SearchRequest("exploding star remnants", null, null, Pagination.defaultPagination());
    final SearchResponse response = searchService.searchHybrid(request);

    assertThat(response.hits()).isNotEmpty();
    assertThat(response.hits()).extracting(SearchHit::id).contains("crab-nebula", "cassiopeia-a");
  }

  @Test
  void hybridSearchRespectsResourceTypeFilter() throws IOException {
    stubQueryVector("exploding star remnants", TestVectors.QUERY_EXPLODING_STAR_REMNANTS);

    final SearchRequest request =
        new SearchRequest(
            "exploding star remnants",
            List.of(ResourceType.MISSIONS),
            null,
            Pagination.defaultPagination());
    final SearchResponse response = searchService.searchHybrid(request);

    assertThat(response.hits()).isNotEmpty();
    assertThat(response.hits()).extracting(SearchHit::id).contains("chandra");
    assertThat(response.hits()).allMatch(h -> h.resourceType() == ResourceType.MISSIONS);
    assertThat(response.hits())
        .extracting(SearchHit::id)
        .doesNotContain("crab-nebula", "cassiopeia-a", "orion-nebula");
  }

  // -------------------------------------------------------------------------
  // Helpers
  // -------------------------------------------------------------------------

  private static void createAllIndexes() throws Exception {
    final JsonpMapper jsonpMapper = openSearchClient._transport().jsonpMapper();
    for (final ResourceType type : ResourceType.values()) {
      final String indexName = type.indexName();
      if (openSearchClient.indices().exists(req -> req.index(indexName)).value()) {
        openSearchClient.indices().delete(req -> req.index(indexName));
      }
      try (InputStream is =
          SearchServiceHybridTest.class.getResourceAsStream("/opensearch/" + indexName + ".json")) {
        final JsonNode body = objectMapper.readTree(is);
        TypeMapping mappings = null;
        if (body.has("mappings")) {
          final byte[] bytes = objectMapper.writeValueAsBytes(body.get("mappings"));
          try (JsonParser p =
              jsonpMapper.jsonProvider().createParser(new ByteArrayInputStream(bytes))) {
            mappings = TypeMapping._DESERIALIZER.deserialize(p, jsonpMapper);
          }
        }
        IndexSettings settings = null;
        if (body.has("settings")) {
          final byte[] bytes = objectMapper.writeValueAsBytes(body.get("settings"));
          try (JsonParser p =
              jsonpMapper.jsonProvider().createParser(new ByteArrayInputStream(bytes))) {
            settings = IndexSettings._DESERIALIZER.deserialize(p, jsonpMapper);
          }
        }
        final CreateIndexRequest.Builder builder =
            new CreateIndexRequest.Builder().index(indexName);
        if (mappings != null) builder.mappings(mappings);
        if (settings != null) builder.settings(settings);
        openSearchClient.indices().create(builder.build());
      }
    }
  }

  private static void createHybridPipeline() throws IOException {
    final String pipelineJson =
        """
        {
          "phase_results_processors": [{
            "normalization-processor": {
              "normalization": { "technique": "min_max" },
              "combination": {
                "technique": "arithmetic_mean",
                "parameters": { "weights": [0.4, 0.6] }
              }
            }
          }]
        }
        """;
    final byte[] bytes = pipelineJson.getBytes(StandardCharsets.UTF_8);
    try (org.opensearch.client.opensearch.generic.Response response =
        openSearchClient
            .generic()
            .execute(
                Requests.builder()
                    .method("PUT")
                    .endpoint("/_search/pipeline/hybrid-pipeline")
                    .body(Body.from(new ByteArrayInputStream(bytes), "application/json"))
                    .build())) {
      if (response.getStatus() != 200) {
        throw new IOException("Failed to create hybrid pipeline, status: " + response.getStatus());
      }
    }
  }

  private static void indexAllDocuments() throws IOException {
    indexDocument(
        "celestial_objects",
        "crab-nebula",
        Map.of(
            "name",
            "Crab Nebula",
            "description",
            "A supernova remnant in Taurus",
            "embedding",
            toDoubleList(TestVectors.CRAB_NEBULA_DESCRIPTION)));
    indexDocument(
        "celestial_objects",
        "cassiopeia-a",
        Map.of(
            "name",
            "Cassiopeia A",
            "description",
            "Young supernova remnant in Cassiopeia",
            "embedding",
            toDoubleList(TestVectors.CASSIOPEIA_A_DESCRIPTION)));
    indexDocument(
        "celestial_objects",
        "orion-nebula",
        Map.of(
            "name",
            "Orion Nebula",
            "description",
            "A stellar nursery in Orion",
            "embedding",
            toDoubleList(TestVectors.ORION_NEBULA_DESCRIPTION)));
    indexDocument(
        "missions",
        "chandra",
        Map.of(
            "name",
            "Chandra X-ray Observatory",
            "description",
            "NASA X-ray telescope",
            "embedding",
            toDoubleList(TestVectors.CHANDRA_MISSION_DESCRIPTION)));
  }

  private static void indexDocument(String indexName, String docId, Map<String, Object> doc)
      throws IOException {
    openSearchClient.index(req -> req.index(indexName).id(docId).document(doc));
    openSearchClient.indices().refresh(req -> req.index(indexName));
  }

  private void stubQueryVector(String prompt, float[] vector) {
    wireMock.stubFor(
        post(urlEqualTo("/api/embeddings"))
            .withRequestBody(containing(prompt))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(embeddingResponseJson(vector))));
  }

  private static String embeddingResponseJson(float[] vector) {
    final StringBuilder sb = new StringBuilder("{\"embedding\":[");
    for (int i = 0; i < vector.length; i++) {
      sb.append(vector[i]);
      if (i < vector.length - 1) {
        sb.append(",");
      }
    }
    sb.append("]}");
    return sb.toString();
  }

  private static List<Double> toDoubleList(float[] floats) {
    final List<Double> list = new ArrayList<>(floats.length);
    for (final float f : floats) {
      list.add((double) f);
    }
    return list;
  }
}
