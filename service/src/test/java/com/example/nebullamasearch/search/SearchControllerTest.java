package com.example.nebullamasearch.search;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.nebullamasearch.config.IndexInitializer;
import com.example.nebullamasearch.domain.ResourceType;
import java.util.List;
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

  @MockBean IndexInitializer indexInitializer;

  private static final SearchResponse EMPTY_RESPONSE = new SearchResponse(0, List.of());

  @Test
  void queryOnlyUsesSemanticMode() {
    when(searchService.search(any(), any())).thenReturn(EMPTY_RESPONSE);

    tester
        .document(
            """
            query {
              search(input: { query: "pulsars" }) {
                total
                interpretation { searchMode }
              }
            }
            """)
        .execute()
        .path("search.interpretation.searchMode")
        .entity(String.class)
        .isEqualTo("SEMANTIC");

    verify(searchService).search(eq(SearchMode.SEMANTIC), any());
  }

  @Test
  void queryWithFiltersUsesHybridMode() {
    when(searchService.search(any(), any())).thenReturn(EMPTY_RESPONSE);

    tester
        .document(
            """
            query {
              search(input: {
                query: "telescopes",
                filters: { agency: "NASA" }
              }) {
                total
                interpretation { searchMode }
              }
            }
            """)
        .execute()
        .path("search.interpretation.searchMode")
        .entity(String.class)
        .isEqualTo("HYBRID");

    verify(searchService)
        .search(eq(SearchMode.HYBRID), argThat(req -> "NASA".equals(req.filters().agency())));
  }

  @Test
  void noQueryUsesKeywordMode() {
    when(searchService.search(any(), any())).thenReturn(EMPTY_RESPONSE);

    tester
        .document(
            """
            query {
              search(input: {
                filters: { agency: "ESA" }
              }) {
                total
                interpretation { searchMode }
              }
            }
            """)
        .execute()
        .path("search.interpretation.searchMode")
        .entity(String.class)
        .isEqualTo("KEYWORD");

    verify(searchService)
        .search(eq(SearchMode.KEYWORD), argThat(req -> "ESA".equals(req.filters().agency())));
  }

  @Test
  void resourceTypesFilterOnlyDoesNotTriggerHybrid() {
    when(searchService.search(any(), any())).thenReturn(EMPTY_RESPONSE);

    tester
        .document(
            """
            query {
              search(input: {
                query: "nebula",
                filters: { resourceTypes: [CELESTIAL_OBJECTS] }
              }) {
                total
                interpretation { searchMode }
              }
            }
            """)
        .execute()
        .path("search.interpretation.searchMode")
        .entity(String.class)
        .isEqualTo("SEMANTIC");

    verify(searchService)
        .search(
            eq(SearchMode.SEMANTIC),
            argThat(req -> req.resourceTypes().equals(List.of(ResourceType.CELESTIAL_OBJECTS))));
  }

  @Test
  void searchIndexForcesResourceType() {
    when(searchService.search(any(), any())).thenReturn(EMPTY_RESPONSE);

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
        .search(
            eq(SearchMode.SEMANTIC),
            argThat(req -> req.resourceTypes().equals(List.of(ResourceType.ASTRONOMERS))));
  }

  @Test
  void paginationPassedThrough() {
    when(searchService.search(any(), any())).thenReturn(EMPTY_RESPONSE);

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
        .search(
            eq(SearchMode.SEMANTIC),
            argThat(req -> req.pagination().from() == 5 && req.pagination().size() == 3));
  }

  @Test
  void interpretationIncludedInResponse() {
    when(searchService.search(any(), any())).thenReturn(EMPTY_RESPONSE);

    tester
        .document(
            """
            query {
              search(input: {
                query: "Jupiter missions",
                filters: { agency: "NASA" }
              }) {
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
}
