import { afterEach, describe, expect, it, vi } from 'vitest';
import type { SearchRequestInput } from '../types/search';
import { runSearch } from './searchApi';

describe('runSearch', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('posts the search query and variables to /graphql', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({
        data: {
          search: {
            total: 0,
            hits: [],
            interpretation: {
              rewrittenQuery: '',
              extractedFilters: {},
              searchMode: 'SEMANTIC',
            },
          },
        },
      }),
    });

    vi.stubGlobal('fetch', fetchMock);

    const input: SearchRequestInput = {
      query: 'Crab Nebula',
      filters: { resourceTypes: ['CELESTIAL_OBJECTS'] },
      pagination: { from: 0, size: 10 },
    };

    await runSearch(input);

    expect(fetchMock).toHaveBeenCalledTimes(1);

    const [requestUrl, requestInit] = fetchMock.mock.calls[0] ?? [];
    expect(requestUrl).toBe('/graphql');
    expect(requestInit).toEqual(
      expect.objectContaining({
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
      }),
    );

    const body = JSON.parse(String(requestInit?.body));
    expect(body.query).toContain('search(input: $input)');
    expect(body.query).toContain('query Search($input: SearchInput!)');
    expect(body.variables).toEqual({ input });
  });

  it('throws when the HTTP response is not ok', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: false,
      status: 503,
      json: async () => ({}),
    });

    vi.stubGlobal('fetch', fetchMock);

    await expect(
      runSearch({
        query: 'Crab Nebula',
      }),
    ).rejects.toThrow('Search request failed with status 503');
  });

  it('throws when GraphQL returns errors', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({
        errors: [{ message: 'GraphQL exploded' }],
      }),
    });

    vi.stubGlobal('fetch', fetchMock);

    await expect(
      runSearch({
        query: 'Crab Nebula',
      }),
    ).rejects.toThrow('GraphQL exploded');
  });

  it('throws when the search payload is missing', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({
        data: {},
      }),
    });

    vi.stubGlobal('fetch', fetchMock);

    await expect(
      runSearch({
        query: 'Crab Nebula',
      }),
    ).rejects.toThrow('Search response was missing data.search');
  });

  it('does not leak the fetch stub between tests', () => {
    expect(vi.isMockFunction(globalThis.fetch)).toBe(false);
  });
});
