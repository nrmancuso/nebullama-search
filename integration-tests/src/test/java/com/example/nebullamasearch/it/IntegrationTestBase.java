package com.example.nebullamasearch.it;

import static org.assertj.core.api.Assertions.fail;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

abstract class IntegrationTestBase {

  protected static final String SERVICE_URL =
      System.getProperty("service.url", "http://localhost:8080");

  protected static final String OPENSEARCH_URL =
      System.getProperty("opensearch.url", "http://localhost:9200");

  protected static final ObjectMapper MAPPER = new ObjectMapper();

  protected static final WebClient SERVICE =
      WebClient.builder()
          .baseUrl(SERVICE_URL)
          .codecs(c -> c.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
          .build();

  protected static final WebClient OPENSEARCH =
      WebClient.builder()
          .baseUrl(OPENSEARCH_URL)
          .codecs(c -> c.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
          .build();

  private static final int MAX_RETRIES = 2;
  private static final Duration RETRY_DELAY = Duration.ofSeconds(5);

  @BeforeAll
  static void verifyStackIsReady() {
    try {
      String health =
          SERVICE
              .get()
              .uri("/actuator/health")
              .retrieve()
              .bodyToMono(String.class)
              .block(Duration.ofSeconds(5));
      if (health == null) {
        fail("Health endpoint returned null");
      }
    } catch (Exception e) {
      fail(
          "Service is not running at "
              + SERVICE_URL
              + ". Run: ./scripts/run-integration-tests.sh\n"
              + e.getMessage());
    }
  }

  protected static JsonNode graphql(String query) {
    String body;
    try {
      body = MAPPER.writeValueAsString(Map.of("query", query));
    } catch (Exception e) {
      fail("Failed to serialize GraphQL query: " + e.getMessage());
      return null;
    }

    for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
      try {
        String response =
            SERVICE
                .post()
                .uri("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .block(Duration.ofSeconds(30));
        return MAPPER.readTree(response);
      } catch (WebClientResponseException.ServiceUnavailable e) {
        if (attempt < MAX_RETRIES) {
          try {
            Thread.sleep(RETRY_DELAY.toMillis());
          } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            break;
          }
        } else {
          fail("GraphQL request failed after retries: " + e.getMessage());
        }
      } catch (Exception e) {
        fail("GraphQL request failed: " + e.getMessage());
        return null;
      }
    }
    return null;
  }

  protected static JsonNode assertNoErrors(JsonNode response) {
    if (response.has("errors")) {
      fail("GraphQL errors: " + response.path("errors"));
    }
    return response.path("data");
  }

  protected static JsonNode searchHits(JsonNode data, String queryName) {
    return data.path(queryName).path("hits");
  }
}
