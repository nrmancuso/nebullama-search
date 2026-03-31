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

  private final IntentExtractionService intentService;
  private final SearchService searchService;

  public SearchController(IntentExtractionService intentService, SearchService searchService) {
    this.intentService = intentService;
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
    final QueryInterpretation interpretation = intentService.extract(input.query());

    final List<ResourceType> resourceTypes =
        resolveResourceTypes(
            forcedResourceTypes,
            input.filters() != null ? input.filters().resourceTypes() : null,
            interpretation);

    final SearchFilters mergedFilters = mergeFilters(input.filters(), interpretation);
    final SearchMode mode = interpretation.searchMode();
    final Pagination pagination = toPagination(input.pagination());

    final SearchRequest request =
        new SearchRequest(
            interpretation.rewrittenQuery(), resourceTypes, mergedFilters, pagination);

    final SearchResponse response =
        switch (mode) {
          case KEYWORD -> searchService.searchBM25(request);
          case SEMANTIC -> searchService.searchKNN(request);
          case HYBRID -> searchService.searchHybrid(request);
        };

    return toResultsDto(response, interpretation);
  }

  /** Precedence: forced (searchIndex) > explicit input.filters.resourceTypes > extracted hints. */
  @SuppressWarnings("unchecked")
  private List<ResourceType> resolveResourceTypes(
      List<ResourceType> forced, List<ResourceType> explicit, QueryInterpretation interpretation) {
    if (forced != null && !forced.isEmpty()) {
      return forced;
    }
    if (explicit != null && !explicit.isEmpty()) {
      return explicit;
    }
    final List<ResourceType> hints =
        (List<ResourceType>) interpretation.extractedFilters().get("resourceTypeHints");
    if (hints != null && !hints.isEmpty()) {
      return hints;
    }
    return List.of();
  }

  private SearchFilters mergeFilters(
      SearchFiltersDto explicit, QueryInterpretation interpretation) {
    final Map<String, Object> extracted = interpretation.extractedFilters();

    final String objectType =
        firstNonNull(explicit != null ? explicit.objectType() : null, extracted.get("objectType"));
    final String agency =
        firstNonNull(explicit != null ? explicit.agency() : null, extracted.get("agency"));
    final String status =
        firstNonNull(explicit != null ? explicit.status() : null, extracted.get("status"));
    final String wavelengthBand =
        firstNonNull(
            explicit != null ? explicit.wavelengthBand() : null, extracted.get("wavelengthBand"));
    final String journal =
        firstNonNull(explicit != null ? explicit.journal() : null, extracted.get("journal"));
    final String nationality =
        firstNonNull(
            explicit != null ? explicit.nationality() : null, extracted.get("nationality"));
    final Integer yearFrom =
        firstNonNullInt(explicit != null ? explicit.yearFrom() : null, extracted.get("yearFrom"));
    final Integer yearTo =
        firstNonNullInt(explicit != null ? explicit.yearTo() : null, extracted.get("yearTo"));

    return new SearchFilters(
        objectType, agency, status, wavelengthBand, journal, nationality, yearFrom, yearTo);
  }

  private Pagination toPagination(PaginationDto dto) {
    if (dto == null) {
      return Pagination.defaultPagination();
    }
    return new Pagination(dto.resolvedFrom(), dto.resolvedSize());
  }

  private String firstNonNull(String explicit, Object extracted) {
    if (explicit != null) {
      return explicit;
    }
    return extracted instanceof String s ? s : null;
  }

  private Integer firstNonNullInt(Integer explicit, Object extracted) {
    if (explicit != null) {
      return explicit;
    }
    return extracted instanceof Integer i ? i : null;
  }

  private SearchResultsDto toResultsDto(
      SearchResponse response, QueryInterpretation interpretation) {
    final List<SearchHitDto> hits =
        response.hits().stream()
            .map(h -> new SearchHitDto(h.id(), h.resourceType(), h.score(), h.source()))
            .toList();

    final QueryInterpretationResultDto interpDto =
        new QueryInterpretationResultDto(
            interpretation.rewrittenQuery(),
            interpretation.extractedFilters(),
            interpretation.searchMode());

    return new SearchResultsDto((int) response.total(), hits, interpDto);
  }
}
