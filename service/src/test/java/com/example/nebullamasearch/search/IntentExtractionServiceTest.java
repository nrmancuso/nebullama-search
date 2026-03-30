package com.example.nebullamasearch.search;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.nebullamasearch.config.OllamaProperties;
import com.example.nebullamasearch.domain.ResourceType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

@Execution(ExecutionMode.SAME_THREAD)
class IntentExtractionServiceTest {

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

  @BeforeEach
  void resetStubs() {
    wireMock.resetAll();
  }

  private IntentExtractionService createService(boolean enabled, int timeoutMs) {
    final OllamaProperties props =
        new OllamaProperties(
            "http://localhost:" + wireMock.port(), "nomic-embed-text", "mistral", 5000, 10000);
    final OllamaChatService chatService = new OllamaChatService(props, objectMapper);
    return new IntentExtractionService(chatService, objectMapper, enabled, timeoutMs);
  }

  @Test
  void extractParsesValidJsonResponse() {
    final String content =
        "{\\\"cleanedQuery\\\":\\\"Jupiter missions\\\","
            + "\\\"resourceTypeHints\\\":[\\\"missions\\\"],"
            + "\\\"filters\\\":{\\\"agency\\\":\\\"NASA\\\",\\\"yearFrom\\\":2000},"
            + "\\\"searchMode\\\":\\\"hybrid\\\"}";

    wireMock.stubFor(
        post(urlEqualTo("/api/chat"))
            .willReturn(
                okJson(
                    "{\"model\":\"mistral\","
                        + "\"message\":{\"role\":\"assistant\",\"content\":\""
                        + content
                        + "\"},"
                        + "\"done\":true}")));

    final IntentExtractionService service = createService(true, 3000);
    final QueryInterpretation result = service.extract("Jupiter missions NASA");

    assertThat(result.rewrittenQuery()).isEqualTo("Jupiter missions");
    assertThat(result.extractedFilters()).containsEntry("agency", "NASA");
    assertThat(result.extractedFilters()).containsEntry("yearFrom", 2000);
    assertThat(result.searchMode()).isEqualTo(SearchMode.HYBRID);

    @SuppressWarnings("unchecked")
    final List<ResourceType> hints =
        (List<ResourceType>) result.extractedFilters().get("resourceTypeHints");
    assertThat(hints).containsExactly(ResourceType.MISSIONS);
  }

  @Test
  void extractFallsBackOnTimeout() {
    wireMock.stubFor(
        post(urlEqualTo("/api/chat"))
            .willReturn(
                aResponse()
                    .withFixedDelay(5000)
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"message\":{\"content\":\"{}\"}}")));

    final IntentExtractionService service = createService(true, 500);
    final QueryInterpretation result = service.extract("pulsars");

    assertThat(result.rewrittenQuery()).isEqualTo("pulsars");
    assertThat(result.extractedFilters()).isEmpty();
    assertThat(result.searchMode()).isEqualTo(SearchMode.HYBRID);
  }

  @Test
  void extractFallsBackOnMalformedJson() {
    wireMock.stubFor(
        post(urlEqualTo("/api/chat"))
            .willReturn(okJson("{\"message\":{\"content\":\"this is not json\"}}")));

    final IntentExtractionService service = createService(true, 3000);
    final QueryInterpretation result = service.extract("neutron stars");

    assertThat(result.rewrittenQuery()).isEqualTo("neutron stars");
    assertThat(result.extractedFilters()).isEmpty();
    assertThat(result.searchMode()).isEqualTo(SearchMode.HYBRID);
  }

  @Test
  void extractFallsBackWhenDisabled() {
    final IntentExtractionService service = createService(false, 3000);
    final QueryInterpretation result = service.extract("Crab Nebula");

    assertThat(result.rewrittenQuery()).isEqualTo("Crab Nebula");
    assertThat(result.searchMode()).isEqualTo(SearchMode.HYBRID);

    wireMock.verify(0, postRequestedFor(urlEqualTo("/api/chat")));
  }

  @Test
  void systemPromptRequiresJsonOnlyOutput() {
    wireMock.stubFor(
        post(urlEqualTo("/api/chat"))
            .willReturn(
                okJson(
                    "{\"message\":{\"content\":\""
                        + "{\\\"cleanedQuery\\\":\\\"Andromeda\\\","
                        + "\\\"resourceTypeHints\\\":[],\\\"filters\\\":{},"
                        + "\\\"searchMode\\\":\\\"hybrid\\\"}\"}}")));

    final IntentExtractionService service = createService(true, 3000);
    service.extract("Andromeda galaxy");

    wireMock.verify(
        postRequestedFor(urlEqualTo("/api/chat"))
            .withRequestBody(containing("ONLY a valid JSON object")));
  }

  @Test
  void extractFallsBackOnHttpError() {
    wireMock.stubFor(
        post(urlEqualTo("/api/chat"))
            .willReturn(aResponse().withStatus(500).withBody("Internal Server Error")));

    final IntentExtractionService service = createService(true, 3000);
    final QueryInterpretation result = service.extract("black holes");

    assertThat(result.rewrittenQuery()).isEqualTo("black holes");
    assertThat(result.extractedFilters()).isEmpty();
    assertThat(result.searchMode()).isEqualTo(SearchMode.HYBRID);
  }
}
