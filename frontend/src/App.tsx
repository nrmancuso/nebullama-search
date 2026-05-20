import { useRef, useState } from 'react';
import SearchForm from './components/SearchForm';
import ResultsTable from './components/ResultsTable';
import DetailsDrawer from './components/DetailsDrawer';
import { runSearch } from './api/searchApi';
import type {
  SearchFormFiltersState,
  ResourceType,
  SearchFormState,
  SearchRequestInput,
  SearchFilters,
  SearchResponse,
} from './types/search';

const DEFAULT_PAGE_SIZE = 10;

function buildRequestInput(
  state: SearchFormState,
  from = 0,
): SearchRequestInput {
  const filters = buildSearchFilters(state);

  return {
    query: state.query.trim() || undefined,
    filters,
    pagination: {
      from,
      size: DEFAULT_PAGE_SIZE,
    },
  };
}

function buildSearchFilters(state: SearchFormState): SearchFilters | undefined {
  const filters: SearchFilters = {};

  if (state.resourceTypes.length > 0) {
    filters.resourceTypes = state.resourceTypes;
  }

  addStringFilter(filters, 'objectType', state.filters.objectType);
  addStringFilter(filters, 'agency', state.filters.agency);
  addStringFilter(filters, 'status', state.filters.status);
  addStringFilter(filters, 'wavelengthBand', state.filters.wavelengthBand);
  addStringFilter(filters, 'journal', state.filters.journal);
  addNumberFilter(filters, 'yearFrom', state.filters.yearFrom);
  addNumberFilter(filters, 'yearTo', state.filters.yearTo);

  return Object.keys(filters).length > 0 ? filters : undefined;
}

function addStringFilter<K extends keyof SearchFilters>(
  filters: SearchFilters,
  key: K,
  value: string,
) {
  const trimmedValue = value.trim();
  if (trimmedValue.length > 0) {
    filters[key] = trimmedValue as SearchFilters[K];
  }
}

function addNumberFilter<K extends 'yearFrom' | 'yearTo'>(
  filters: SearchFilters,
  key: K,
  value: SearchFormFiltersState[K],
) {
  const trimmedValue = value.trim();
  if (trimmedValue.length === 0) {
    return;
  }

  const parsedValue = Number(trimmedValue);
  if (Number.isFinite(parsedValue) && Number.isInteger(parsedValue)) {
    filters[key] = parsedValue as SearchFilters[K];
  }
}

export default function App() {
  const [formState, setFormState] = useState<SearchFormState>({
    query: '',
    resourceTypes: [],
    filters: {
      objectType: '',
      agency: '',
      status: '',
      wavelengthBand: '',
      journal: '',
      yearFrom: '',
      yearTo: '',
    },
  });
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [lastResponse, setLastResponse] = useState<SearchResponse | null>(null);
  const [pageStart, setPageStart] = useState(0);
  const [selectedHitKey, setSelectedHitKey] = useState<string | null>(null);
  const lastSubmittedInputRef = useRef<SearchRequestInput | null>(null);
  const searchSequenceRef = useRef(0);
  const activeSearchIdRef = useRef(0);

  function handleResourceTypeToggle(resourceType: ResourceType) {
    setFormState((currentState) => {
      const resourceTypes = currentState.resourceTypes.includes(resourceType)
        ? currentState.resourceTypes.filter((value) => value !== resourceType)
        : [...currentState.resourceTypes, resourceType];

      return {
        ...currentState,
        resourceTypes,
      };
    });
  }

  async function executeSearch(requestInput: SearchRequestInput) {
    const requestId = ++searchSequenceRef.current;
    activeSearchIdRef.current = requestId;
    const nextPageStart = requestInput.pagination?.from ?? 0;

    setIsLoading(true);
    setError(null);
    setLastResponse(null);
    setSelectedHitKey(null);
    setPageStart(nextPageStart);

    try {
      const response = await runSearch(requestInput);

      if (requestId === activeSearchIdRef.current) {
        setLastResponse(response);
      }
    } catch (errorValue) {
      if (requestId === activeSearchIdRef.current) {
        setError(
          errorValue instanceof Error
            ? errorValue.message
            : 'Search request failed',
        );
      }
    } finally {
      if (requestId === activeSearchIdRef.current) {
        setIsLoading(false);
      }
    }
  }

  async function handleSubmit() {
    const requestInput = buildRequestInput(formState, 0);
    lastSubmittedInputRef.current = requestInput;
    await executeSearch(requestInput);
  }

  async function handlePreviousPage() {
    const lastSubmittedInput = lastSubmittedInputRef.current;
    if (pageStart === 0 || !lastSubmittedInput) {
      return;
    }

    await executeSearch({
      ...lastSubmittedInput,
      pagination: {
        from: Math.max(0, pageStart - DEFAULT_PAGE_SIZE),
        size: lastSubmittedInput.pagination?.size ?? DEFAULT_PAGE_SIZE,
      },
    });
  }

  async function handleNextPage() {
    const lastSubmittedInput = lastSubmittedInputRef.current;
    if (!lastResponse || !lastSubmittedInput) {
      return;
    }

    if (pageStart + DEFAULT_PAGE_SIZE >= lastResponse.total) {
      return;
    }

    await executeSearch({
      ...lastSubmittedInput,
      pagination: {
        from: pageStart + DEFAULT_PAGE_SIZE,
        size: lastSubmittedInput.pagination?.size ?? DEFAULT_PAGE_SIZE,
      },
    });
  }

  const selectedHit =
    lastResponse?.hits.find(
      (hit) => `${hit.resourceType}:${hit.id}` === selectedHitKey,
    ) ?? null;

  const total = lastResponse?.total ?? 0;
  const hasResults = Boolean(lastResponse?.hits.length);
  const visibleResults = !isLoading && !error && hasResults ? lastResponse : null;
  const canGoPrevious = pageStart > 0;
  const canGoNext = Boolean(
    lastResponse && pageStart + DEFAULT_PAGE_SIZE < lastResponse.total,
  );
  const currentRangeLabel =
    total === 0
      ? 'No results found.'
      : `Showing ${pageStart + 1}-${Math.min(
          pageStart + (lastResponse?.hits.length ?? 0),
          total,
        )} of ${total}`;

  return (
    <main>
      <header className="page-header">
        <h1>Nebullama Search</h1>
        <p>A local search demo for exploring live GraphQL results.</p>
      </header>

      <section role="search" aria-label="Search controls">
        <SearchForm
          isLoading={isLoading}
          state={formState}
          onQueryChange={(query) =>
            setFormState((currentState) => ({
              ...currentState,
              query,
            }))
          }
          onResourceTypeToggle={handleResourceTypeToggle}
          onFilterChange={(field, value) =>
            setFormState((currentState) => ({
              ...currentState,
              filters: {
                ...currentState.filters,
                [field]: value,
              },
            }))
          }
          onSubmit={handleSubmit}
        />
      </section>

      <section aria-label="Search results" className="results-shell">
        {isLoading ? <p role="status">Searching...</p> : null}
        {error ? <p role="alert">{error}</p> : null}
        {!isLoading && !error && lastResponse && !hasResults ? (
          <p role="status">{currentRangeLabel}</p>
        ) : null}
        {visibleResults ? (
          <ResultsTable
            hits={visibleResults.hits}
            currentRangeLabel={currentRangeLabel}
            canGoPrevious={canGoPrevious}
            canGoNext={canGoNext}
            selectedHitKey={selectedHitKey}
            onSelectHit={(hit) => {
              setSelectedHitKey(`${hit.resourceType}:${hit.id}`);
            }}
            onPreviousPage={handlePreviousPage}
            onNextPage={handleNextPage}
          />
        ) : null}
      </section>
      {selectedHit ? (
        <DetailsDrawer
          hit={selectedHit}
          interpretation={lastResponse?.interpretation ?? null}
          onClose={() => setSelectedHitKey(null)}
        />
      ) : null}
    </main>
  );
}
