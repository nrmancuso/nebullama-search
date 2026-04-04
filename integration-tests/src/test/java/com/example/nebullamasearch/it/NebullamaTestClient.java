package com.example.nebullamasearch.it;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Headers;
import feign.Param;
import feign.RequestLine;
import feign.Response;
import java.util.Map;

final class NebullamaTestClient {

  private final ServiceApi serviceApi;
  private final OpenSearchApi openSearchApi;

  NebullamaTestClient(String serviceUrl, String opensearchUrl, ObjectMapper objectMapper) {
    this.serviceApi = FeignClientFactory.create(ServiceApi.class, serviceUrl, objectMapper);
    this.openSearchApi =
        FeignClientFactory.create(OpenSearchApi.class, opensearchUrl, objectMapper);
  }

  JsonNode health() {
    return serviceApi.health();
  }

  JsonNode graphql(String query) {
    return serviceApi.graphql(new GraphqlRequest(query));
  }

  Response ingestSingle(String resourceType, Map<String, Object> doc) {
    return serviceApi.ingestSingle(resourceType, doc);
  }

  JsonNode countIndex(String index) {
    return openSearchApi.count(index);
  }

  JsonNode searchIndex(String index, JsonNode query) {
    return openSearchApi.search(index, query);
  }

  private interface ServiceApi {

    @RequestLine("GET /actuator/health")
    JsonNode health();

    @RequestLine("POST /graphql")
    @Headers({
      "Accept: application/json",
      "Content-Type: application/json",
    })
    JsonNode graphql(GraphqlRequest request);

    @RequestLine("POST /api/v1/ingest/{resourceType}")
    @Headers({
      "Accept: application/json",
      "Content-Type: application/json",
    })
    Response ingestSingle(@Param("resourceType") String resourceType, Map<String, Object> doc);
  }

  private interface OpenSearchApi {

    @RequestLine("GET /{index}/_count")
    JsonNode count(@Param("index") String index);

    @RequestLine("POST /{index}/_search")
    @Headers({
      "Accept: application/json",
      "Content-Type: application/json",
    })
    JsonNode search(@Param("index") String index, JsonNode query);
  }

  private record GraphqlRequest(String query) {}
}
