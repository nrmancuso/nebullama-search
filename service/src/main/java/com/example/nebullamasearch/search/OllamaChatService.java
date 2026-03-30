package com.example.nebullamasearch.search;

import com.example.nebullamasearch.config.OllamaProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Service
public class OllamaChatService {

  private static final Logger log = LoggerFactory.getLogger(OllamaChatService.class);

  private final String baseUrl;
  private final String intentModel;
  private final ObjectMapper objectMapper;

  public OllamaChatService(OllamaProperties props, ObjectMapper objectMapper) {
    this.baseUrl = props.baseUrl();
    this.intentModel = props.intentModel();
    this.objectMapper = objectMapper;
  }

  public String chat(String systemPrompt, String userMessage, int timeoutMs) {
    final ObjectNode body = objectMapper.createObjectNode();
    body.put("model", intentModel);
    body.put("stream", false);

    final ArrayNode messages = body.putArray("messages");
    messages.addObject().put("role", "system").put("content", systemPrompt);
    messages.addObject().put("role", "user").put("content", userMessage);

    final SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(Duration.ofMillis(timeoutMs));
    factory.setReadTimeout(Duration.ofMillis(timeoutMs));
    final RestClient client = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();

    try {
      final String responseBody =
          client
              .post()
              .uri("/api/chat")
              .contentType(MediaType.APPLICATION_JSON)
              .body(body.toString())
              .retrieve()
              .body(String.class);

      final JsonNode root = objectMapper.readTree(responseBody);
      return root.path("message").path("content").asText();

    } catch (ResourceAccessException e) {
      throw new OllamaChatTimeoutException("Ollama chat timed out after " + timeoutMs + "ms", e);
    } catch (RestClientResponseException e) {
      throw new OllamaChatException("Ollama chat returned HTTP " + e.getStatusCode().value(), e);
    } catch (Exception e) {
      throw new OllamaChatException("Unexpected error calling Ollama chat", e);
    }
  }
}
