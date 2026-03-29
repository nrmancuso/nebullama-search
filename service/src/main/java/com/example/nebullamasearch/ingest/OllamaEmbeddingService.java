package com.example.nebullamasearch.ingest;

import com.example.nebullamasearch.config.OllamaProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Service
public class OllamaEmbeddingService {

  private final RestClient restClient;
  private final String embeddingModel;
  private final ObjectMapper objectMapper;

  public OllamaEmbeddingService(OllamaProperties props, ObjectMapper objectMapper) {
    this.embeddingModel = props.embeddingModel();
    this.objectMapper = objectMapper;
    var factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(Duration.ofMillis(props.connectTimeoutMs()));
    factory.setReadTimeout(Duration.ofMillis(props.readTimeoutMs()));
    this.restClient = RestClient.builder().baseUrl(props.baseUrl()).requestFactory(factory).build();
  }

  public float[] embed(String text) {
    Map<String, String> requestBody = Map.of("model", embeddingModel, "prompt", text);

    try {
      String responseBody =
          restClient
              .post()
              .uri("/api/embeddings")
              .contentType(MediaType.APPLICATION_JSON)
              .body(requestBody)
              .retrieve()
              .body(String.class);

      JsonNode root = objectMapper.readTree(responseBody);
      JsonNode embeddingNode = root.get("embedding");
      if (embeddingNode == null || !embeddingNode.isArray()) {
        throw new EmbeddingException("Ollama response missing 'embedding' array");
      }

      float[] result = new float[embeddingNode.size()];
      for (int i = 0; i < embeddingNode.size(); i++) {
        result[i] = (float) embeddingNode.get(i).asDouble();
      }
      return result;

    } catch (RestClientResponseException ex) {
      throw new EmbeddingException(
          "Ollama embedding request failed with status "
              + ex.getStatusCode().value()
              + ": "
              + ex.getResponseBodyAsString(),
          ex);
    } catch (EmbeddingException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new EmbeddingException(
          "Failed to parse Ollama embedding response: " + ex.getMessage(), ex);
    }
  }
}
