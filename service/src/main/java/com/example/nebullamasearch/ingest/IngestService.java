package com.example.nebullamasearch.ingest;

import com.example.nebullamasearch.domain.ResourceType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch.core.IndexRequest;
import org.springframework.stereotype.Service;

@Service
public class IngestService {
  private static final String EMBEDDING_FIELD = "embedding";
  private static final int EMBEDDING_DIMENSIONS = 768;

  private static final Map<ResourceType, String> PRIMARY_TEXT_FIELD =
      Map.of(
          ResourceType.CELESTIAL_OBJECTS, "description",
          ResourceType.MISSIONS, "description",
          ResourceType.OBSERVATIONS, "notes",
          ResourceType.ASTRONOMERS, "biography",
          ResourceType.PUBLICATIONS, "abstract");

  private final OllamaEmbeddingService embeddingService;
  private final OpenSearchClient openSearchClient;
  private final ObjectMapper objectMapper;

  public IngestService(
      OllamaEmbeddingService embeddingService,
      OpenSearchClient openSearchClient,
      ObjectMapper objectMapper) {
    this.embeddingService = embeddingService;
    this.openSearchClient = openSearchClient;
    this.objectMapper = objectMapper;
  }

  public IngestResult ingestOne(ResourceType resourceType, Map<String, Object> doc) {
    String id = UUID.randomUUID().toString();
    try {
      Map<String, Object> enriched = prepareDocument(resourceType, doc, id);
      writeToOpenSearch(resourceType.indexName(), id, enriched);
      return IngestResult.ok(id);
    } catch (Exception ex) {
      return IngestResult.failed(id, ex.getMessage());
    }
  }

  public List<IngestResult> ingestBulk(ResourceType resourceType, List<Map<String, Object>> docs) {
    List<Future<IngestResult>> futures = new ArrayList<>(docs.size());

    try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
      for (Map<String, Object> doc : docs) {
        futures.add(executor.submit(() -> ingestOne(resourceType, doc)));
      }
    }

    List<IngestResult> results = new ArrayList<>(futures.size());
    for (Future<IngestResult> future : futures) {
      try {
        results.add(future.get());
      } catch (Exception ex) {
        results.add(IngestResult.failed(null, "Unexpected executor error: " + ex.getMessage()));
      }
    }
    return results;
  }

  private Map<String, Object> prepareDocument(
      ResourceType resourceType, Map<String, Object> doc, String id) {
    Map<String, Object> enriched = new HashMap<>(doc);
    enriched.put("id", id);
    enriched.put("resource_type", resourceType.indexName());
    enriched.put(EMBEDDING_FIELD, resolveEmbedding(resourceType, enriched));
    return enriched;
  }

  private List<Double> resolveEmbedding(ResourceType resourceType, Map<String, Object> enriched) {
    Object providedEmbedding = enriched.get(EMBEDDING_FIELD);
    if (providedEmbedding != null) {
      return normalizeEmbedding(providedEmbedding);
    }

    String primaryField = PRIMARY_TEXT_FIELD.get(resourceType);
    String textToEmbed =
        primaryField != null ? String.valueOf(enriched.getOrDefault(primaryField, "")) : "";

    float[] embedding = embeddingService.embed(textToEmbed);
    return toDoubleList(embedding);
  }

  private List<Double> normalizeEmbedding(Object rawEmbedding) {
    if (!(rawEmbedding instanceof List<?> rawList)) {
      throw new IllegalArgumentException("embedding must be a JSON array of numbers");
    }

    List<Double> embedding = new ArrayList<>(rawList.size());
    for (Object value : rawList) {
      if (!(value instanceof Number number)) {
        throw new IllegalArgumentException("embedding entries must be numeric");
      }
      embedding.add(number.doubleValue());
    }

    if (embedding.size() != EMBEDDING_DIMENSIONS) {
      throw new IllegalArgumentException(
          "embedding must contain exactly " + EMBEDDING_DIMENSIONS + " values");
    }
    return embedding;
  }

  private void writeToOpenSearch(String indexName, String id, Map<String, Object> doc) {
    try {
      IndexRequest<Map<String, Object>> request =
          IndexRequest.of(b -> b.index(indexName).id(id).document(doc));
      openSearchClient.index(request);
    } catch (Exception ex) {
      throw new RuntimeException(
          "OpenSearch write failed for id=" + id + ": " + ex.getMessage(), ex);
    }
  }

  private List<Double> toDoubleList(float[] floats) {
    List<Double> list = new ArrayList<>(floats.length);
    for (float f : floats) {
      list.add((double) f);
    }
    return list;
  }
}
