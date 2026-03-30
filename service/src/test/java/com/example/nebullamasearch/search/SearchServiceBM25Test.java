package com.example.nebullamasearch.search;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
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
import java.time.Duration;
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
class SearchServiceBM25Test {

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
  private static final ObjectMapper objectMapper = new ObjectMapper();

  @BeforeAll
  static void startWireMock() {
    wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
    wireMock.start();
  }

  @AfterAll
  static void stopWireMock() {
    wireMock.stop();
  }

  private OpenSearchClient openSearchClient;
  private OllamaEmbeddingService embeddingService;
  private SearchService searchService;

  @BeforeEach
  void setUp() throws Exception {
    final HttpHost host =
        new HttpHost("http", openSearch.getHost(), openSearch.getMappedPort(9200));
    final ApacheHttpClient5Transport transport =
        ApacheHttpClient5TransportBuilder.builder(host).build();
    openSearchClient = new OpenSearchClient(transport);

    final OllamaProperties props =
        new OllamaProperties(
            "http://localhost:" + wireMock.port(), "nomic-embed-text", "mistral", 5000, 10000);
    embeddingService = new OllamaEmbeddingService(props, new ObjectMapper());

    searchService = new SearchService(openSearchClient, embeddingService);

    wireMock.resetAll();
    wireMock.stubFor(
        post(urlEqualTo("/api/embeddings"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(embeddingResponseBody())));

    createIndexes();
  }

  private void createIndexes() throws Exception {
    final JsonpMapper jsonpMapper = openSearchClient._transport().jsonpMapper();
    for (final ResourceType type : ResourceType.values()) {
      final String indexName = type.indexName();
      if (openSearchClient.indices().exists(req -> req.index(indexName)).value()) {
        openSearchClient.indices().delete(req -> req.index(indexName));
      }
      try (InputStream is =
          SearchServiceBM25Test.class.getResourceAsStream("/opensearch/" + indexName + ".json")) {
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

  private void indexDocument(String indexName, String docId, Map<String, Object> doc)
      throws IOException {
    openSearchClient.index(req -> req.index(indexName).id(docId).document(doc));
    openSearchClient.indices().refresh(req -> req.index(indexName));
  }

  private static String embeddingResponseBody() {
    final StringBuilder sb = new StringBuilder("{\"embedding\":[");
    for (int i = 0; i < 768; i++) {
      sb.append("0.1");
      if (i < 767) {
        sb.append(",");
      }
    }
    sb.append("]}");
    return sb.toString();
  }

  @Test
  void testKeywordSearchMultiIndex() throws IOException {
    indexDocument(
        "celestial_objects",
        "crab-nebula-1",
        Map.of(
            "name", "Crab Nebula",
            "description", "A supernova remnant in Taurus"));

    indexDocument(
        "missions",
        "mission-1",
        Map.of(
            "name", "Crab Nebula Survey",
            "target_name", "Crab Nebula",
            "agency", "NASA"));

    final SearchRequest request =
        new SearchRequest("Crab Nebula", null, null, Pagination.defaultPagination());

    final SearchResponse response = searchService.searchBM25(request);

    assertThat(response.hits()).isNotEmpty();
    assertThat(response.hits())
        .anyMatch(
            h ->
                h.id().equals("crab-nebula-1")
                    && h.resourceType() == ResourceType.CELESTIAL_OBJECTS);
    assertThat(response.hits())
        .anyMatch(h -> h.id().equals("mission-1") && h.resourceType() == ResourceType.MISSIONS);
  }

  @Test
  void testResourceTypeFilter() throws IOException {
    indexDocument(
        "missions",
        "mission-1",
        Map.of(
            "name", "Chandra X-ray Telescope Observatory",
            "agency", "NASA"));

    indexDocument(
        "missions",
        "mission-2",
        Map.of(
            "name", "XMM-Newton X-ray telescope",
            "agency", "ESA"));

    indexDocument(
        "celestial_objects",
        "obj-1",
        Map.of(
            "name", "catalog",
            "description", "A celestial catalog"));

    final SearchRequest request =
        new SearchRequest(
            "telescope", List.of(ResourceType.MISSIONS), null, Pagination.defaultPagination());

    final SearchResponse response = searchService.searchBM25(request);

    assertThat(response.hits()).isNotEmpty();
    assertThat(response.hits()).allMatch(h -> h.resourceType() == ResourceType.MISSIONS);
  }

  @Test
  void testAgencyFilter() throws IOException {
    indexDocument(
        "missions",
        "mission-nasa",
        Map.of(
            "name", "NASA space telescope mission",
            "agency", "NASA"));

    indexDocument(
        "missions",
        "mission-esa",
        Map.of(
            "name", "ESA space telescope project",
            "agency", "ESA"));

    final SearchFilters filters =
        new SearchFilters(null, "NASA", null, null, null, null, null, null);

    final SearchRequest request =
        new SearchRequest(
            "telescope", List.of(ResourceType.MISSIONS), filters, Pagination.defaultPagination());

    final SearchResponse response = searchService.searchBM25(request);

    assertThat(response.hits()).isNotEmpty();
    assertThat(response.hits()).allMatch(h -> "NASA".equals(h.source().get("agency")));
  }

  @Test
  void testYearRangeFilter() throws IOException {
    indexDocument(
        "publications", "year-test-1980", Map.of("title", "Early Pulsar Timing", "year", 1980));

    indexDocument(
        "publications",
        "year-test-2010",
        Map.of("title", "Modern Pulsar Timing Array", "year", 2010));

    indexDocument(
        "publications",
        "year-test-2020",
        Map.of("title", "Latest Pulsar Timing Results", "year", 2020));

    final SearchFilters filters = new SearchFilters(null, null, null, null, null, null, 2000, 2015);

    final SearchRequest request =
        new SearchRequest(
            "pulsar timing",
            List.of(ResourceType.PUBLICATIONS),
            filters,
            Pagination.defaultPagination());

    final SearchResponse response = searchService.searchBM25(request);

    assertThat(response.hits()).isNotEmpty();
    assertThat(response.hits()).anyMatch(h -> h.id().equals("year-test-2010"));
    assertThat(response.hits())
        .allMatch(
            h -> {
              final Object year = h.source().get("year");
              return year instanceof Integer && (Integer) year >= 2000 && (Integer) year <= 2015;
            });
  }
}
