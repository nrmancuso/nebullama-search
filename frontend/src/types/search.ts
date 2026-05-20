export type ResourceType =
  | 'CELESTIAL_OBJECTS'
  | 'MISSIONS'
  | 'OBSERVATIONS'
  | 'ASTRONOMERS'
  | 'PUBLICATIONS';

export type SearchMode = 'KEYWORD' | 'SEMANTIC' | 'HYBRID';

export type SearchFilters = {
  resourceTypes?: ResourceType[];
  objectType?: string;
  agency?: string;
  status?: string;
  wavelengthBand?: string;
  journal?: string;
  nationality?: string;
  yearFrom?: number;
  yearTo?: number;
};

export type Pagination = {
  from: number;
  size: number;
};

export type SearchRequestInput = {
  query?: string;
  filters?: SearchFilters;
  pagination?: Pagination;
};

export type SearchFormFiltersState = {
  objectType: string;
  agency: string;
  status: string;
  wavelengthBand: string;
  journal: string;
  yearFrom: string;
  yearTo: string;
};

export type SearchFormState = {
  query: string;
  resourceTypes: ResourceType[];
  filters: SearchFormFiltersState;
};

export type SearchHit = {
  id: string;
  resourceType: ResourceType;
  score: number;
  source: Record<string, unknown>;
};

export type SearchInterpretation = {
  rewrittenQuery?: string | null;
  extractedFilters?: Record<string, unknown> | null;
  searchMode: SearchMode;
};

export type SearchResponse = {
  total: number;
  hits: SearchHit[];
  interpretation: SearchInterpretation | null;
};
