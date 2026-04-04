package com.example.nebullamasearch.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
    classes = ContainerRuntimePropertiesTest.TestPropertiesConfiguration.class,
    properties = {
      "OPENSEARCH_HOST=opensearch",
      "OPENSEARCH_PORT=9201",
      "OLLAMA_BASE_URL=http://ollama:11434"
    })
class ContainerRuntimePropertiesTest {

  @Autowired private OpenSearchProperties openSearchProperties;

  @Autowired private OllamaProperties ollamaProperties;

  @Test
  void resolvesContainerHostOverridesFromEnvironmentStyleProperties() {
    assertThat(openSearchProperties.host()).isEqualTo("opensearch");
    assertThat(openSearchProperties.port()).isEqualTo(9201);
    assertThat(ollamaProperties.baseUrl()).isEqualTo("http://ollama:11434");
  }

  @EnableConfigurationProperties({OpenSearchProperties.class, OllamaProperties.class})
  static class TestPropertiesConfiguration {}
}
