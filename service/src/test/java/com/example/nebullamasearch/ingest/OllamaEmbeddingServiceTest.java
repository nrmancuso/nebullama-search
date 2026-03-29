package com.example.nebullamasearch.ingest;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.example.nebullamasearch.config.OllamaProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

@Execution(ExecutionMode.SAME_THREAD)
class OllamaEmbeddingServiceTest {

  static WireMockServer wireMock;
  OllamaEmbeddingService service;

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
  void setUp() {
    wireMock.resetAll();
    var props =
        new OllamaProperties(
            "http://localhost:" + wireMock.port(), "nomic-embed-text", "mistral", 5000, 10000);
    service = new OllamaEmbeddingService(props, new ObjectMapper());
  }

  private static String embeddingResponseBody() {
    var sb = new StringBuilder("{\"embedding\":[");
    for (int i = 0; i < 768; i++) {
      sb.append("0.1");
      if (i < 767) sb.append(",");
    }
    sb.append("]}");
    return sb.toString();
  }

  @Test
  void embed_sendsCorrectRequestBody() {
    wireMock.stubFor(
        post(urlEqualTo("/api/embeddings"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(embeddingResponseBody())));

    service.embed("Crab Nebula pulsar");

    wireMock.verify(
        postRequestedFor(urlEqualTo("/api/embeddings"))
            .withRequestBody(matchingJsonPath("$.model", equalTo("nomic-embed-text")))
            .withRequestBody(matchingJsonPath("$.prompt", equalTo("Crab Nebula pulsar"))));
  }

  @Test
  void embed_returns768DimFloatArray() {
    wireMock.stubFor(
        post(urlEqualTo("/api/embeddings"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(embeddingResponseBody())));

    float[] result = service.embed("Andromeda Galaxy");

    assertThat(result).hasSize(768);
    assertThat(result[0]).isCloseTo(0.1f, within(1e-5f));
    assertThat(result[767]).isCloseTo(0.1f, within(1e-5f));
  }

  @Test
  void embed_throwsEmbeddingExceptionOn500() {
    wireMock.stubFor(
        post(urlEqualTo("/api/embeddings"))
            .willReturn(aResponse().withStatus(500).withBody("Internal Server Error")));

    assertThatThrownBy(() -> service.embed("some text"))
        .isInstanceOf(EmbeddingException.class)
        .hasMessageContaining("500");
  }
}
