package com.example.nebullamasearch.search;

import com.example.nebullamasearch.domain.ResourceType;
import com.example.nebullamasearch.search.dto.PaginationDto;
import com.example.nebullamasearch.search.dto.QueryInterpretationResultDto;
import com.example.nebullamasearch.search.dto.SearchFiltersDto;
import com.example.nebullamasearch.search.dto.SearchHitDto;
import com.example.nebullamasearch.search.dto.SearchInputDto;
import com.example.nebullamasearch.search.dto.SearchResultsDto;
import java.util.List;
import java.util.Map;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

@Controller
public class SearchController {

  private final SearchService searchService;

  public SearchController(SearchService searchService) {
    this.searchService = searchService;
  }

  @QueryMapping
  public SearchResultsDto search(@Argument SearchInputDto input) {
    return executeSearch(input, null);
  }

  @QueryMapping
  public SearchResultsDto searchIndex(
      @Argument ResourceType resourceType, @Argument SearchInputDto input) {
    return executeSearch(input, List.of(resourceType));
  }

  private SearchResultsDto executeSearch(
      SearchInputDto input, List<ResourceType> forcedResourceTypes) {
    final String query = input.query();
    final SearchFiltersDto filtersDto = input.filters();
    final boolean hasQuery = query != null && !query.isBlank();
    final boolean hasFilters = hasStructuredFilters(filtersDto);

    final SearchMode mode = resolveMode(hasQuery, hasFilters);
    final SearchFilters filters = toSearchFilters(filtersDto);
    final List<ResourceType> resourceTypes = resolveResourceTypes(forcedResourceTypes, filtersDto);
    final Pagination pagination = toPagination(input.pagination());

    final SearchRequest request =
        new SearchRequest(hasQuery ? query : "", resourceTypes, filters, pagination);

    final SearchResponse response =
        switch (mode) {
          case KEYWORD -> searchService.searchBM25(request);
          case SEMANTIC -> searchService.searchKNN(request);
          case HYBRID -> searchService.searchHybrid(request);
        };

    return toResultsDto(response, query, mode);
  }

  /**
   * Deterministic mode selection based on request shape:
   *
   * <ul>
   *   <li>query + filters → HYBRID
   *   <li>query only → SEMANTIC
   *   <li>no query (filters only) → KEYWORD
   * </ul>
   */
  private SearchMode resolveMode(boolean hasQuery, boolean hasFilters) {
    if (hasQuery && hasFilters) {
      return SearchMode.HYBRID;
    }
    if (hasQuery) {
      return SearchMode.SEMANTIC;
    }
    return SearchMode.KEYWORD;
  }

  private boolean hasStructuredFilters(SearchFiltersDto dto) {
    if (dto == null) {
      return false;
    }
    return dto.objectType() != null
        || dto.agency() != null
        || dto.status() != null
        || dto.wavelengthBand() != null
        || dto.journal() != null
        || dto.nationality() != null
        || dto.yearFrom() != null
        || dto.yearTo() != null;
  }

  private List<ResourceType> resolveResourceTypes(
      List<ResourceType> forced, SearchFiltersDto filtersDto) {
    if (forced != null && !forced.isEmpty()) {
      return forced;
    }
    if (filtersDto != null
        && filtersDto.resourceTypes() != null
        && !filtersDto.resourceTypes().isEmpty()) {
      return filtersDto.resourceTypes();
    }
    return List.of();
  }

  private SearchFilters toSearchFilters(SearchFiltersDto dto) {
    if (dto == null) {
      return null;
    }
    return new SearchFilters(
        dto.objectType(),
        dto.agency(),
        dto.status(),
        dto.wavelengthBand(),
        dto.journal(),
        dto.nationality(),
        dto.yearFrom(),
        dto.yearTo());
  }

  private Pagination toPagination(PaginationDto dto) {
    if (dto == null) {
      return Pagination.defaultPagination();
    }
    return new Pagination(dto.resolvedFrom(), dto.resolvedSize());
  }

  private SearchResultsDto toResultsDto(SearchResponse response, String query, SearchMode mode) {
    final List<SearchHitDto> hits =
        response.hits().stream()
            .map(h -> new SearchHitDto(h.id(), h.resourceType(), h.score(), h.source()))
            .toList();

    final QueryInterpretationResultDto interpDto =
        new QueryInterpretationResultDto(query, Map.of(), mode);

    return new SearchResultsDto((int) response.total(), hits, interpDto);
  }
}
