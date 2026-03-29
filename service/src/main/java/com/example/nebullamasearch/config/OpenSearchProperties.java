package com.example.nebullamasearch.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "opensearch")
public record OpenSearchProperties(
    @DefaultValue("localhost") String host,
    @DefaultValue("9200") int port,
    @DefaultValue("http") String scheme) {}
