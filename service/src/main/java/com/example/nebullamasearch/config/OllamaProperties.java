package com.example.nebullamasearch.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "ollama")
public record OllamaProperties(
        @DefaultValue("http://localhost:11434") String baseUrl,
        @DefaultValue("nomic-embed-text") String embeddingModel,
        @DefaultValue("mistral") String intentModel,
        @DefaultValue("5000") int connectTimeoutMs,
        @DefaultValue("10000") int readTimeoutMs
) {}
