import type { SearchRequestInput, SearchResponse } from '../types/search';

const SEARCH_QUERY = `
  query Search($input: SearchInput!) {
    search(input: $input) {
      total
      hits {
        id
        resourceType
        score
        source
      }
      interpretation {
        rewrittenQuery
        extractedFilters
        searchMode
      }
    }
  }
`;

type GraphQlResponse = {
  data?: {
    search?: SearchResponse;
  };
  errors?: Array<{
    message?: string;
  }>;
};

export async function runSearch(
  input: SearchRequestInput,
): Promise<SearchResponse> {
  const response = await fetch('/graphql', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({
      query: SEARCH_QUERY,
      variables: {
        input,
      },
    }),
  });

  if (!response.ok) {
    throw new Error(`Search request failed with status ${response.status}`);
  }

  const payload = (await response.json()) as GraphQlResponse;

  if (payload.errors?.length) {
    throw new Error(payload.errors[0]?.message ?? 'Search request failed');
  }

  if (!payload.data?.search) {
    throw new Error('Search response was missing data.search');
  }

  return payload.data.search;
}
