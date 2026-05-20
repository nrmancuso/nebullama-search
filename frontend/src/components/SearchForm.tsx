import { useState, type FormEvent } from 'react';
import type {
  ResourceType,
  SearchFormFiltersState,
  SearchFormState,
} from '../types/search';

const RESOURCE_TYPE_OPTIONS: ResourceType[] = [
  'CELESTIAL_OBJECTS',
  'MISSIONS',
  'OBSERVATIONS',
  'ASTRONOMERS',
  'PUBLICATIONS',
];

type SearchFormProps = {
  isLoading: boolean;
  state: SearchFormState;
  onQueryChange: (query: string) => void;
  onResourceTypeToggle: (resourceType: ResourceType) => void;
  onFilterChange: <K extends keyof SearchFormFiltersState>(
    field: K,
    value: SearchFormFiltersState[K],
  ) => void;
  onSubmit: () => void | Promise<void>;
};

function toDisplayLabel(resourceType: ResourceType): string {
  return resourceType
    .toLowerCase()
    .replace(/_/g, ' ')
    .replace(/(^|\s)\w/g, (match) => match.toUpperCase());
}

export default function SearchForm({
  isLoading,
  state,
  onQueryChange,
  onResourceTypeToggle,
  onFilterChange,
  onSubmit,
}: SearchFormProps) {
  const [isFiltersOpen, setIsFiltersOpen] = useState(false);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    await onSubmit();
    setIsFiltersOpen(false);
  }

  return (
    <form className="search-toolbar" onSubmit={handleSubmit}>
      <div className="search-query-field">
        <label htmlFor="search-query">Search Query</label>
        <input
          id="search-query"
          type="text"
          placeholder="Search the local universe"
          value={state.query}
          onChange={(event) => onQueryChange(event.target.value)}
        />
      </div>

      <fieldset className="resource-type-strip">
        <legend>Resource Types</legend>
        {RESOURCE_TYPE_OPTIONS.map((resourceType) => (
          <label key={resourceType} htmlFor={`resource-type-${resourceType}`}>
            <input
              id={`resource-type-${resourceType}`}
              type="checkbox"
              checked={state.resourceTypes.includes(resourceType)}
              onChange={() => onResourceTypeToggle(resourceType)}
            />
            {toDisplayLabel(resourceType)}
          </label>
        ))}
      </fieldset>

      <div className="toolbar-actions">
        <button type="button" onClick={() => setIsFiltersOpen(true)}>
          More filters
        </button>
        <button type="submit" disabled={isLoading}>
          {isLoading ? 'Searching...' : 'Search'}
        </button>
      </div>

      {isFiltersOpen ? (
        <div
          className="filters-popover-backdrop"
          onClick={() => setIsFiltersOpen(false)}
        >
          <section
            aria-label="Advanced filters"
            aria-modal="true"
            className="filters-popover"
            onClick={(event) => event.stopPropagation()}
            role="dialog"
          >
            <header className="filters-popover-header">
              <h2>Advanced filters</h2>
              <button type="button" onClick={() => setIsFiltersOpen(false)}>
                Close
              </button>
            </header>

            <div>
              <label htmlFor="filter-object-type">Object Type</label>
              <input
                id="filter-object-type"
                type="text"
                value={state.filters.objectType}
                onChange={(event) => onFilterChange('objectType', event.target.value)}
              />
            </div>
            <div>
              <label htmlFor="filter-agency">Agency</label>
              <input
                id="filter-agency"
                type="text"
                value={state.filters.agency}
                onChange={(event) => onFilterChange('agency', event.target.value)}
              />
            </div>
            <div>
              <label htmlFor="filter-status">Status</label>
              <input
                id="filter-status"
                type="text"
                value={state.filters.status}
                onChange={(event) => onFilterChange('status', event.target.value)}
              />
            </div>
            <div>
              <label htmlFor="filter-wavelength-band">Wavelength Band</label>
              <input
                id="filter-wavelength-band"
                type="text"
                value={state.filters.wavelengthBand}
                onChange={(event) =>
                  onFilterChange('wavelengthBand', event.target.value)
                }
              />
            </div>
            <div>
              <label htmlFor="filter-journal">Journal</label>
              <input
                id="filter-journal"
                type="text"
                value={state.filters.journal}
                onChange={(event) => onFilterChange('journal', event.target.value)}
              />
            </div>
            <div>
              <label htmlFor="filter-year-from">Year From</label>
              <input
                id="filter-year-from"
                type="number"
                value={state.filters.yearFrom}
                onChange={(event) => onFilterChange('yearFrom', event.target.value)}
              />
            </div>
            <div>
              <label htmlFor="filter-year-to">Year To</label>
              <input
                id="filter-year-to"
                type="number"
                value={state.filters.yearTo}
                onChange={(event) => onFilterChange('yearTo', event.target.value)}
              />
            </div>
          </section>
        </div>
      ) : null}
    </form>
  );
}
