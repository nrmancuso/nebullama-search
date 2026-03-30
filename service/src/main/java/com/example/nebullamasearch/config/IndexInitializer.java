package com.example.nebullamasearch.config;

import com.example.nebullamasearch.domain.ResourceType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.json.stream.JsonParser;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.opensearch.client.json.JsonpMapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.mapping.TypeMapping;
import org.opensearch.client.opensearch.generic.Body;
import org.opensearch.client.opensearch.generic.Requests;
import org.opensearch.client.opensearch.indices.CreateIndexRequest;
import org.opensearch.client.opensearch.indices.IndexSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class IndexInitializer implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(IndexInitializer.class);

  private final OpenSearchClient client;
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Value("${search.hybrid-weight.bm25:0.4}")
  private float bm25Weight = 0.4f;

  @Value("${search.hybrid-weight.knn:0.6}")
  private float knnWeight = 0.6f;

  public IndexInitializer(OpenSearchClient client) {
    this.client = client;
  }

  @Override
  public void run(ApplicationArguments args) throws Exception {
    for (final ResourceType type : ResourceType.values()) {
      createIndexIfAbsent(type.indexName());
    }
    createHybridPipeline();
  }

  private void createHybridPipeline() throws IOException {
    final String pipelineJson =
        String.format(
            Locale.ROOT,
            """
            {
              "phase_results_processors": [{
                "normalization-processor": {
                  "normalization": { "technique": "min_max" },
                  "combination": {
                    "technique": "arithmetic_mean",
                    "parameters": { "weights": [%s, %s] }
                  }
                }
              }]
            }
            """,
            Float.toString(bm25Weight),
            Float.toString(knnWeight));

    final byte[] bytes = pipelineJson.getBytes(StandardCharsets.UTF_8);
    try (org.opensearch.client.opensearch.generic.Response response =
        client
            .generic()
            .execute(
                Requests.builder()
                    .method("PUT")
                    .endpoint("/_search/pipeline/hybrid-pipeline")
                    .body(Body.from(new ByteArrayInputStream(bytes), "application/json"))
                    .build())) {
      log.info("Hybrid search pipeline created (status: {})", response.getStatus());
      if (response.getStatus() != 200) {
        throw new IOException("Failed to create hybrid pipeline, status: " + response.getStatus());
      }
    }
  }

  private void createIndexIfAbsent(String indexName) throws IOException {
    boolean exists = client.indices().exists(r -> r.index(indexName)).value();

    if (exists) {
      log.info("Index '{}' already exists — skipping creation", indexName);
      return;
    }

    InputStream mappingJson = getClass().getResourceAsStream("/opensearch/" + indexName + ".json");

    if (mappingJson == null) {
      throw new IllegalStateException(
          "No mapping file found on classpath: /opensearch/" + indexName + ".json");
    }

    JsonpMapper mapper = client._transport().jsonpMapper();

    JsonNode body;
    try (mappingJson) {
      body = objectMapper.readTree(mappingJson);
    }

    TypeMapping mappings = null;
    if (body.has("mappings")) {
      byte[] mappingsBytes = objectMapper.writeValueAsBytes(body.get("mappings"));
      try (JsonParser mappingsParser =
          mapper.jsonProvider().createParser(new ByteArrayInputStream(mappingsBytes))) {
        mappings = TypeMapping._DESERIALIZER.deserialize(mappingsParser, mapper);
      }
    }

    IndexSettings settings = null;
    if (body.has("settings")) {
      byte[] settingsBytes = objectMapper.writeValueAsBytes(body.get("settings"));
      try (JsonParser settingsParser =
          mapper.jsonProvider().createParser(new ByteArrayInputStream(settingsBytes))) {
        settings = IndexSettings._DESERIALIZER.deserialize(settingsParser, mapper);
      }
    }

    final CreateIndexRequest.Builder builder = new CreateIndexRequest.Builder().index(indexName);
    if (mappings != null) builder.mappings(mappings);
    if (settings != null) builder.settings(settings);
    client.indices().create(builder.build());
    log.info("Created index '{}'", indexName);
  }
}
