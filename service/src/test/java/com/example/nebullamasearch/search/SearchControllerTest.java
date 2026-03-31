package com.example.nebullamasearch.search;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.nebullamasearch.config.IndexInitializer;
import com.example.nebullamasearch.domain.ResourceType;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.graphql.tester.AutoConfigureHttpGraphQlTester;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.graphql.test.tester.HttpGraphQlTester;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureHttpGraphQlTester
@Execution(ExecutionMode.SAME_THREAD)
class SearchControllerTest {

  @Autowired HttpGraphQlTester tester;

  @MockBean SearchService searchService;

  @MockBean IntentExtractionService intentService;

  @MockBean IndexInitializer indexInitializer;

  private static final SearchResponse EMPTY_RESPONSE = new SearchResponse(0, List.of());

  @Test
  void searchUsesHybridByDefault() {
    when(intentService.extract(any()))
        .thenReturn(new QueryInterpretation("pulsars", Map.of(), SearchMode.HYBRID));
    when(searchService.searchHybrid(any())).thenReturn(EMPTY_RESPONSE);

    tester
        .document(
            """
            query {
              search(input: { query: "pulsars" }) {
                total
              }
            }
            """)
        .execute()
        .path("search.total")
        .entity(Integer.class)
        .isEqualTo(0);

    verify(searchService).searchHybrid(any());
  }

  @Test
  void searchUsesBm25WhenKeywordMode() {
    when(intentService.extract(any()))
        .thenReturn(new QueryInterpretation("Crab Nebula", Map.of(), SearchMode.KEYWORD));
    when(searchService.searchBM25(any())).thenReturn(EMPTY_RESPONSE);

    tester
        .document(
            """
            query {
              search(input: { query: "Crab Nebula" }) {
                total
              }
            }
            """)
        .execute()
        .path("search.total")
        .entity(Integer.class)
        .isEqualTo(0);

    verify(searchService).searchBM25(any());
  }

  @Test
  void searchIndexForcesResourceType() {
    when(intentService.extract(any()))
        .thenReturn(new QueryInterpretation("pulsar", Map.of(), SearchMode.HYBRID));
    when(searchService.searchHybrid(any())).thenReturn(EMPTY_RESPONSE);

    tester
        .document(
            """
            query {
              searchIndex(resourceType: ASTRONOMERS, input: { query: "pulsar" }) {
                total
              }
            }
            """)
        .execute()
        .path("searchIndex.total")
        .entity(Integer.class)
        .isEqualTo(0);

    verify(searchService)
        .searchHybrid(
            argThat(req -> req.resourceTypes().equals(List.of(ResourceType.ASTRONOMERS))));
  }

  @Test
  void interpretationIncludedInResponse() {
    when(intentService.extract(any()))
        .thenReturn(
            new QueryInterpretation(
                "Jupiter missions", Map.of("agency", "NASA"), SearchMode.HYBRID));
    when(searchService.searchHybrid(any())).thenReturn(EMPTY_RESPONSE);

    tester
        .document(
            """
            query {
              search(input: { query: "Jupiter missions" }) {
                total
                interpretation {
                  rewrittenQuery
                  searchMode
                  extractedFilters
                }
              }
            }
            """)
        .execute()
        .path("search.interpretation.rewrittenQuery")
        .entity(String.class)
        .isEqualTo("Jupiter missions")
        .path("search.interpretation.searchMode")
        .entity(String.class)
        .isEqualTo("HYBRID");
  }

  @Test
  void explicitFiltersOverrideExtracted() {
    when(intentService.extract(any()))
        .thenReturn(
            new QueryInterpretation("missions", Map.of("agency", "NASA"), SearchMode.HYBRID));
    when(searchService.searchHybrid(any())).thenReturn(EMPTY_RESPONSE);

    tester
        .document(
            """
            query {
              search(input: {
                query: "missions",
                filters: { agency: "ESA" }
              }) {
                total
              }
            }
            """)
        .execute()
        .path("search.total")
        .entity(Integer.class)
        .isEqualTo(0);

    verify(searchService).searchHybrid(argThat(req -> "ESA".equals(req.filters().agency())));
  }

  @Test
  void paginationPassedThrough() {
    when(intentService.extract(any()))
        .thenReturn(new QueryInterpretation("galaxy", Map.of(), SearchMode.HYBRID));
    when(searchService.searchHybrid(any())).thenReturn(EMPTY_RESPONSE);

    tester
        .document(
            """
            query {
              search(input: {
                query: "galaxy",
                pagination: { from: 5, size: 3 }
              }) {
                total
              }
            }
            """)
        .execute()
        .path("search.total")
        .entity(Integer.class)
        .isEqualTo(0);

    verify(searchService)
        .searchHybrid(argThat(req -> req.pagination().from() == 5 && req.pagination().size() == 3));
  }
}
