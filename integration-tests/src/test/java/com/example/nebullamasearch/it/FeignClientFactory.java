package com.example.nebullamasearch.it;

import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Feign;
import feign.Request;
import feign.jackson.JacksonDecoder;
import feign.jackson.JacksonEncoder;
import java.time.Duration;

final class FeignClientFactory {

  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
  private static final Duration READ_TIMEOUT = Duration.ofSeconds(30);

  private FeignClientFactory() {}

  static <T> T create(Class<T> apiType, String baseUrl, ObjectMapper objectMapper) {
    return Feign.builder()
        .encoder(new JacksonEncoder(objectMapper))
        .decoder(new JacksonDecoder(objectMapper))
        .options(new Request.Options(CONNECT_TIMEOUT, READ_TIMEOUT, true))
        .target(apiType, baseUrl);
  }
}
