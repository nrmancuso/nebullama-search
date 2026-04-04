package com.example.nebullamasearch.ingest;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Execution(ExecutionMode.SAME_THREAD)
class IngestControllerTest {

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

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

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
  void stubOllama() {
    wireMock.resetAll();
    wireMock.stubFor(
        WireMock.post(urlEqualTo("/api/embeddings"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(embeddingResponseBody())));
  }

  @Test
  void singleIngest_returns201WithId() throws Exception {
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

    mockMvc
        .perform(
            post("/api/v1/ingest/CELESTIAL_OBJECTS")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(doc)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").exists())
        .andExpect(jsonPath("$.id").isNotEmpty());
  }

  @Test
  void bulkIngest_returns207WithResults() throws Exception {
    /*
     * Request: curl -X POST http://localhost:8080/api/v1/ingest/MISSIONS/bulk \
     *   -H "Content-Type: application/json" \
     *   -d '[{"name":"Hubble","description":"NASA observatory"}, ...]'
     */
    Map<String, Object> doc1 = Map.of("name", "Hubble", "description", "NASA observatory");
    Map<String, Object> doc2 = Map.of("name", "JWST", "description", "Infrared telescope");

    mockMvc
        .perform(
            post("/api/v1/ingest/MISSIONS/bulk")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(List.of(doc1, doc2))))
        .andExpect(status().isMultiStatus())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$[0].id").exists())
        .andExpect(jsonPath("$[0].success").value(true))
        .andExpect(jsonPath("$[1].id").exists())
        .andExpect(jsonPath("$[1].success").value(true));
  }

  @Test
  void invalidResourceType_returns400() throws Exception {
    /*
     * Request: curl -X POST http://localhost:8080/api/v1/ingest/INVALID_TYPE \
     *   -H "Content-Type: application/json" \
     *   -d '{"name":"Test"}'
     * Expected: 400 Bad Request
     */
    Map<String, Object> doc = Map.of("name", "Test");

    mockMvc
        .perform(
            post("/api/v1/ingest/INVALID_TYPE")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(doc)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void singleIngest_acceptsProvidedEmbeddingWithoutCallingOllama() throws Exception {
    List<Double> embedding = new ArrayList<>(768);
    for (int i = 0; i < 768; i++) {
      embedding.add(0.25d);
    }

    Map<String, Object> doc =
        Map.of(
            "name", "Crab Nebula",
            "object_type", "nebula",
            "description", "Supernova remnant in Taurus",
            "constellation", "Taurus",
            "embedding", embedding);

    mockMvc
        .perform(
            post("/api/v1/ingest/CELESTIAL_OBJECTS")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(doc)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").exists())
        .andExpect(jsonPath("$.success").value(true));

    wireMock.verify(0, WireMock.postRequestedFor(urlEqualTo("/api/embeddings")));
  }
}
