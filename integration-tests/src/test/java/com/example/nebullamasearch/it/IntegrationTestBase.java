package com.example.nebullamasearch.it;

import static org.assertj.core.api.Assertions.fail;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import java.time.Duration;
import org.junit.jupiter.api.BeforeAll;

abstract class IntegrationTestBase {

  protected static final String SERVICE_URL =
      System.getProperty("service.url", "http://localhost:8080");

  protected static final String OPENSEARCH_URL =
      System.getProperty("opensearch.url", "http://localhost:9200");

  protected static final ObjectMapper MAPPER = new ObjectMapper();

  protected static final NebullamaTestClient CLIENT =
      new NebullamaTestClient(SERVICE_URL, OPENSEARCH_URL, MAPPER);

  private static final int MAX_RETRIES = 2;
  private static final Duration RETRY_DELAY = Duration.ofSeconds(5);

  @BeforeAll
  static void verifyStackIsReady() {
    try {
      JsonNode health = CLIENT.health();
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
    for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
      try {
        return CLIENT.graphql(query);
      } catch (FeignException.ServiceUnavailable e) {
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
