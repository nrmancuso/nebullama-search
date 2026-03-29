package com.example.nebullamasearch.config;

import com.example.nebullamasearch.domain.ResourceType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.json.stream.JsonParser;
import org.opensearch.client.json.JsonpMapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.mapping.TypeMapping;
import org.opensearch.client.opensearch.indices.CreateIndexRequest;
import org.opensearch.client.opensearch.indices.IndexSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

@Component
public class IndexInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(IndexInitializer.class);

    private final OpenSearchClient client;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public IndexInitializer(OpenSearchClient client) {
        this.client = client;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        for (ResourceType type : ResourceType.values()) {
            createIndexIfAbsent(type.indexName());
        }
    }

    private void createIndexIfAbsent(String indexName) throws IOException {
        boolean exists = client.indices()
                .exists(r -> r.index(indexName))
                .value();

        if (exists) {
            log.info("Index '{}' already exists — skipping creation", indexName);
            return;
        }

        InputStream mappingJson = getClass()
                .getResourceAsStream("/opensearch/" + indexName + ".json");

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
            try (JsonParser mappingsParser = mapper.jsonProvider()
                    .createParser(new ByteArrayInputStream(mappingsBytes))) {
                mappings = TypeMapping._DESERIALIZER.deserialize(mappingsParser, mapper);
            }
        }

        IndexSettings settings = null;
        if (body.has("settings")) {
            byte[] settingsBytes = objectMapper.writeValueAsBytes(body.get("settings"));
            try (JsonParser settingsParser = mapper.jsonProvider()
                    .createParser(new ByteArrayInputStream(settingsBytes))) {
                settings = IndexSettings._DESERIALIZER.deserialize(settingsParser, mapper);
            }
        }

        var builder = new CreateIndexRequest.Builder().index(indexName);
        if (mappings != null) builder.mappings(mappings);
        if (settings != null) builder.settings(settings);
        client.indices().create(builder.build());
        log.info("Created index '{}'", indexName);
    }
}
