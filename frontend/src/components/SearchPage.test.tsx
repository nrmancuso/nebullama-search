import {
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import App from '../App';
import ResultsTable from './ResultsTable';

type SearchSuccessResponse = ReturnType<typeof createSearchSuccessResponse>;

function createSearchSuccessResponse({
  total,
  hits,
  interpretation = {
    rewrittenQuery: 'Crab Nebula',
    extractedFilters: {
      objectType: 'supernova remnant',
    },
    searchMode: 'SEMANTIC',
  },
}: {
  total: number;
  hits: Array<{
    id: string;
    resourceType: 'CELESTIAL_OBJECTS' | 'MISSIONS' | 'OBSERVATIONS' | 'ASTRONOMERS' | 'PUBLICATIONS';
    score: number;
    source: Record<string, unknown>;
  }>;
  interpretation?: {
    rewrittenQuery?: string | null;
    extractedFilters?: Record<string, unknown> | null;
    searchMode: 'KEYWORD' | 'SEMANTIC' | 'HYBRID';
  };
}) {
  return {
    ok: true,
    json: async () => ({
      data: {
        search: {
          total,
          hits,
          interpretation,
        },
      },
    }),
  };
}

describe('SearchPage', () => {
  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
  });

  it('submits a query and renders the results table', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      createSearchSuccessResponse({
        total: 1,
        hits: [
          {
            id: 'co-1',
            resourceType: 'CELESTIAL_OBJECTS',
            score: 0.98,
            source: {
              name: 'Crab Nebula',
              description: 'A supernova remnant in Taurus',
            },
          },
        ],
        interpretation: {
          rewrittenQuery: 'Crab Nebula',
          extractedFilters: {},
          searchMode: 'SEMANTIC',
        },
      }),
    );

    vi.stubGlobal('fetch', fetchMock);

    render(<App />);

    fireEvent.change(screen.getByLabelText(/search query/i), {
      target: { value: 'Crab Nebula' },
    });
    fireEvent.click(screen.getByRole('button', { name: /more filters/i }));
    fireEvent.change(screen.getByLabelText(/object type/i), {
      target: { value: 'supernova remnant' },
    });
    fireEvent.change(screen.getByLabelText(/year from/i), {
      target: { value: '1054' },
    });
    fireEvent.click(screen.getByLabelText(/celestial objects/i));
    fireEvent.click(screen.getByRole('button', { name: /^search$/i }));

    await waitFor(() => {
      expect(fetchMock).toHaveBeenCalled();
    });

    const requestInit = fetchMock.mock.calls[0]?.[1];
    expect(requestInit).toMatchObject({
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
    });
    expect(
      JSON.parse((requestInit as RequestInit).body as string),
    ).toMatchObject({
      variables: {
        input: {
          query: 'Crab Nebula',
          filters: {
            resourceTypes: ['CELESTIAL_OBJECTS'],
            objectType: 'supernova remnant',
            yearFrom: 1054,
          },
          pagination: {
            from: 0,
            size: 10,
          },
        },
      },
    });

    await waitFor(() => {
      expect(screen.getByRole('table')).toBeInTheDocument();
    });

    expect(screen.getByRole('columnheader', { name: 'Name' })).toBeInTheDocument();
    expect(
      screen.getByRole('columnheader', { name: 'Resource Type' }),
    ).toBeInTheDocument();
    expect(screen.getByRole('columnheader', { name: 'Score' })).toBeInTheDocument();
    expect(screen.getByRole('columnheader', { name: 'Summary' })).toBeInTheDocument();
    expect(screen.getByText('Crab Nebula')).toBeInTheDocument();
    expect(screen.getByText('CELESTIAL_OBJECTS')).toBeInTheDocument();
  });

  it('opens a details modal when a row is clicked', async () => {
    const longDescription = `${'A supernova remnant in Taurus. '.repeat(14)}Full detail text.`;
    const fetchMock = vi.fn().mockResolvedValue(
      createSearchSuccessResponse({
        total: 1,
        hits: [
          {
            id: 'co-1',
            resourceType: 'CELESTIAL_OBJECTS',
            score: 0.98,
            source: {
              name: 'Crab Nebula',
              description: longDescription,
              discoveryYear: 1054,
            },
          },
        ],
        interpretation: {
          rewrittenQuery: 'Crab Nebula',
          extractedFilters: { objectType: 'supernova remnant' },
          searchMode: 'SEMANTIC',
        },
      }),
    );

    vi.stubGlobal('fetch', fetchMock);

    render(<App />);

    fireEvent.change(screen.getByLabelText(/search query/i), {
      target: { value: 'Crab Nebula' },
    });
    fireEvent.click(screen.getByRole('button', { name: /^search$/i }));

    await waitFor(() => {
      expect(screen.getByText('Crab Nebula')).toBeInTheDocument();
    });

    fireEvent.click(screen.getByText('Crab Nebula'));

    const drawer = screen.getByRole('dialog', {
      name: /crab nebula/i,
    });
    expect(drawer).toBeInTheDocument();
    expect(
      within(drawer).getByRole('heading', { name: 'Crab Nebula' }),
    ).toBeInTheDocument();
    expect(within(drawer).getByText('id')).toBeInTheDocument();
    expect(within(drawer).getByText('co-1')).toBeInTheDocument();
    expect(within(drawer).getByText('resourceType')).toBeInTheDocument();
    expect(within(drawer).getByText('CELESTIAL_OBJECTS')).toBeInTheDocument();
    expect(within(drawer).getByText('score')).toBeInTheDocument();
    expect(within(drawer).getByText('0.98')).toBeInTheDocument();
    expect(within(drawer).getByRole('heading', { name: /full document/i })).toBeInTheDocument();
    expect(drawer).toHaveTextContent(longDescription);
    expect(within(drawer).getByText(longDescription)).toHaveClass('full-document-content');
    expect(drawer).toHaveTextContent('SEMANTIC');
    expect(drawer).toHaveTextContent('supernova remnant');
    expect(drawer).toHaveTextContent('1054');
  });

  it('keeps advanced filters in a popup panel and removes nationality from the UI', () => {
    render(<App />);

    expect(screen.queryByLabelText(/nationality/i)).not.toBeInTheDocument();
    expect(screen.queryByLabelText(/object type/i)).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: /more filters/i }));

    expect(screen.getByRole('dialog', { name: /advanced filters/i })).toBeInTheDocument();
    expect(screen.getByLabelText(/object type/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/agency/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/status/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/wavelength band/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/journal/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/year from/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/year to/i)).toBeInTheDocument();
    expect(screen.queryByLabelText(/nationality/i)).not.toBeInTheDocument();
  });

  it('shows an explicit empty state when a search returns no hits', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      createSearchSuccessResponse({
        total: 0,
        hits: [],
        interpretation: {
          rewrittenQuery: 'Empty query',
          extractedFilters: {},
          searchMode: 'KEYWORD',
        },
      }),
    );

    vi.stubGlobal('fetch', fetchMock);

    render(<App />);

    fireEvent.change(screen.getByLabelText(/search query/i), {
      target: { value: 'Empty query' },
    });
    fireEvent.click(screen.getByRole('button', { name: /^search$/i }));

    await waitFor(() => {
      expect(screen.getByRole('status')).toHaveTextContent(/no results found/i);
    });

    expect(screen.queryByRole('table')).not.toBeInTheDocument();
  });

  it('shows an explicit error state when the backend fails', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: false,
      status: 500,
      json: async () => ({}),
    });

    vi.stubGlobal('fetch', fetchMock);

    render(<App />);

    fireEvent.change(screen.getByLabelText(/search query/i), {
      target: { value: 'Broken query' },
    });
    fireEvent.click(screen.getByRole('button', { name: /^search$/i }));

    await waitFor(() => {
      expect(screen.getByRole('alert')).toHaveTextContent(
        /search request failed/i,
      );
    });

    expect(screen.queryByRole('table')).not.toBeInTheDocument();
  });

  it('truncates long table summaries instead of rendering full document text', async () => {
    const longDescription = 'A'.repeat(320);
    const fetchMock = vi.fn().mockResolvedValue(
      createSearchSuccessResponse({
        total: 1,
        hits: [
          {
            id: 'co-1',
            resourceType: 'CELESTIAL_OBJECTS',
            score: 0.98,
            source: {
              name: 'Crab Nebula',
              description: longDescription,
            },
          },
        ],
      }),
    );

    vi.stubGlobal('fetch', fetchMock);

    render(<App />);

    fireEvent.change(screen.getByLabelText(/search query/i), {
      target: { value: 'Crab Nebula' },
    });
    fireEvent.click(screen.getByRole('button', { name: /^search$/i }));

    await waitFor(() => {
      expect(screen.getByRole('table')).toBeInTheDocument();
    });

    expect(screen.getByText(`${'A'.repeat(250)}…`)).toBeInTheDocument();
    expect(screen.queryByText(longDescription)).not.toBeInTheDocument();
  });

  it('paginates results and updates the current range', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(
        createSearchSuccessResponse({
          total: 13,
          hits: Array.from({ length: 10 }, (_, index) => ({
            id: `co-${index + 1}`,
            resourceType: 'CELESTIAL_OBJECTS',
            score: 1 - index * 0.01,
            source: {
              name: `Result ${index + 1}`,
            },
          })),
          interpretation: {
            rewrittenQuery: 'Paged query',
            extractedFilters: {},
            searchMode: 'SEMANTIC',
          },
        }),
      )
      .mockResolvedValueOnce(
        createSearchSuccessResponse({
          total: 13,
          hits: Array.from({ length: 3 }, (_, index) => ({
            id: `co-${index + 11}`,
            resourceType: 'CELESTIAL_OBJECTS',
            score: 0.8 - index * 0.01,
            source: {
              name: `Result ${index + 11}`,
            },
          })),
          interpretation: {
            rewrittenQuery: 'Paged query',
            extractedFilters: {},
            searchMode: 'SEMANTIC',
          },
        }),
      );

    vi.stubGlobal('fetch', fetchMock);

    render(<App />);

    fireEvent.change(screen.getByLabelText(/search query/i), {
      target: { value: 'Paged query' },
    });
    fireEvent.click(screen.getByRole('button', { name: /more filters/i }));
    fireEvent.change(screen.getByLabelText(/object type/i), {
      target: { value: 'supernova remnant' },
    });
    fireEvent.click(screen.getByRole('button', { name: /^search$/i }));

    await waitFor(() => {
      expect(screen.getByText('Result 1')).toBeInTheDocument();
    });

    expect(screen.getByRole('status')).toHaveTextContent('Showing 1-10 of 13');

    fireEvent.change(screen.getByLabelText(/search query/i), {
      target: { value: 'Edited but not submitted' },
    });
    fireEvent.click(screen.getByRole('button', { name: /more filters/i }));
    fireEvent.change(screen.getByLabelText(/object type/i), {
      target: { value: 'planetary nebula' },
    });

    fireEvent.click(screen.getByRole('button', { name: /next page/i }));

    await waitFor(() => {
      expect(screen.getByText('Result 11')).toBeInTheDocument();
    });

    const secondRequestInit = fetchMock.mock.calls[1]?.[1];
    const secondRequestBody = JSON.parse(
      (secondRequestInit as RequestInit).body as string,
    );
    expect(secondRequestBody.variables.input).toMatchObject({
      query: 'Paged query',
      filters: {
        objectType: 'supernova remnant',
      },
      pagination: {
        from: 10,
        size: 10,
      },
    });
    expect(secondRequestBody.variables.input).not.toMatchObject({
      query: 'Edited but not submitted',
      filters: {
        objectType: 'planetary nebula',
      },
    });
    expect(screen.getByRole('status')).toHaveTextContent('Showing 11-13 of 13');
  });

  it('clears stale results when a later search fails', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(
        createSearchSuccessResponse({
          total: 1,
          hits: [
            {
              id: 'co-1',
              resourceType: 'CELESTIAL_OBJECTS',
              score: 0.98,
              source: {
                name: 'Crab Nebula',
                description: 'A supernova remnant in Taurus',
              },
            },
          ],
          interpretation: {
            rewrittenQuery: 'Crab Nebula',
            extractedFilters: {},
            searchMode: 'SEMANTIC',
          },
        }),
      )
      .mockResolvedValueOnce({
        ok: false,
        status: 500,
        json: async () => ({}),
      });

    vi.stubGlobal('fetch', fetchMock);

    render(<App />);

    fireEvent.change(screen.getByLabelText(/search query/i), {
      target: { value: 'Crab Nebula' },
    });
    fireEvent.click(screen.getByRole('button', { name: /^search$/i }));

    await waitFor(() => {
      expect(screen.getByText('Crab Nebula')).toBeInTheDocument();
    });

    fireEvent.change(screen.getByLabelText(/search query/i), {
      target: { value: 'Broken query' },
    });
    fireEvent.click(screen.getByRole('button', { name: /^search$/i }));

    await waitFor(() => {
      expect(screen.getByRole('alert')).toHaveTextContent(
        /search request failed/i,
      );
    });

    expect(screen.queryByRole('table')).not.toBeInTheDocument();
    expect(screen.queryByText('Crab Nebula')).not.toBeInTheDocument();
  });

  it('keeps the newest search response when requests overlap', async () => {
    let resolveFirst!: (value: SearchSuccessResponse) => void;
    let resolveSecond!: (value: SearchSuccessResponse) => void;

    const fetchMock = vi.fn().mockImplementation(() => {
      if (!resolveFirst) {
        return new Promise((resolve) => {
          resolveFirst = resolve;
        });
      }

      return new Promise((resolve) => {
        resolveSecond = resolve;
      });
    });

    vi.stubGlobal('fetch', fetchMock);

    const { container } = render(<App />);

    fireEvent.change(screen.getByLabelText(/search query/i), {
      target: { value: 'First query' },
    });
    fireEvent.click(screen.getByRole('button', { name: /more filters/i }));
    fireEvent.change(screen.getByLabelText(/object type/i), {
      target: { value: 'supernova remnant' },
    });

    const form = container.querySelector('form');
    expect(form).not.toBeNull();

    fireEvent.submit(form as HTMLFormElement);

    fireEvent.change(screen.getByLabelText(/search query/i), {
      target: { value: 'Second query' },
    });
    fireEvent.click(screen.getByRole('button', { name: /more filters/i }));
    fireEvent.change(screen.getByLabelText(/object type/i), {
      target: { value: 'planetary nebula' },
    });
    fireEvent.submit(form as HTMLFormElement);

    await waitFor(() => {
      expect(fetchMock).toHaveBeenCalledTimes(2);
    });

    const firstRequestInit = fetchMock.mock.calls[0]?.[1];
    expect(
      JSON.parse((firstRequestInit as RequestInit).body as string),
    ).toMatchObject({
      variables: {
        input: {
          query: 'First query',
          filters: {
            objectType: 'supernova remnant',
          },
        },
      },
    });

    const secondRequestInit = fetchMock.mock.calls[1]?.[1];
    expect(
      JSON.parse((secondRequestInit as RequestInit).body as string),
    ).toMatchObject({
      variables: {
        input: {
          query: 'Second query',
          filters: {
            objectType: 'planetary nebula',
          },
        },
      },
    });

    resolveSecond(
      createSearchSuccessResponse({
        total: 1,
        hits: [
          {
            id: 'second',
            resourceType: 'MISSIONS',
            score: 0.91,
            source: {
              name: 'Second result',
            },
          },
        ],
        interpretation: {
          rewrittenQuery: 'Second query',
          extractedFilters: {},
          searchMode: 'KEYWORD',
        },
      }),
    );

    await waitFor(() => {
      expect(screen.getByText('Second result')).toBeInTheDocument();
    });

    resolveFirst(
      createSearchSuccessResponse({
        total: 1,
        hits: [
          {
            id: 'first',
            resourceType: 'MISSIONS',
            score: 0.99,
            source: {
              name: 'First result',
            },
          },
        ],
        interpretation: {
          rewrittenQuery: 'First query',
          extractedFilters: {},
          searchMode: 'KEYWORD',
        },
      }),
    );

    await waitFor(() => {
      expect(screen.getByText('Second result')).toBeInTheDocument();
    });

    expect(screen.queryByText('First result')).not.toBeInTheDocument();
  });

  it('omits invalid year input from the request payload', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({
        data: {
          search: {
            total: 0,
            hits: [],
            interpretation: {
              rewrittenQuery: 'Crab Nebula',
              extractedFilters: {},
              searchMode: 'SEMANTIC',
            },
          },
        },
      }),
    });

    vi.stubGlobal('fetch', fetchMock);

    render(<App />);

    fireEvent.change(screen.getByLabelText(/search query/i), {
      target: { value: 'Crab Nebula' },
    });
    fireEvent.click(screen.getByRole('button', { name: /more filters/i }));
    fireEvent.change(screen.getByLabelText(/year from/i), {
      target: { value: '1054.5' },
    });
    fireEvent.click(screen.getByRole('button', { name: /search/i }));

    await waitFor(() => {
      expect(fetchMock).toHaveBeenCalled();
    });

    const requestInit = fetchMock.mock.calls[0]?.[1];
    const parsedBody = JSON.parse((requestInit as RequestInit).body as string);
    expect(parsedBody.variables.input.filters ?? {}).not.toHaveProperty(
      'yearFrom',
    );
  });
});

describe('ResultsTable', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('uses a unique row key across resource types', () => {
    const consoleErrorSpy = vi.spyOn(console, 'error').mockImplementation(() => {
      // suppress React duplicate key warnings if they occur
    });

    render(
      <ResultsTable
        hits={[
          {
            id: 'duplicate-id',
            resourceType: 'CELESTIAL_OBJECTS',
            score: 1,
            source: {
              name: 'Alpha',
            },
          },
          {
            id: 'duplicate-id',
            resourceType: 'MISSIONS',
            score: 0.9,
            source: {
              name: 'Beta',
            },
          },
        ]}
        currentRangeLabel="Showing 1-2 of 2"
        canGoPrevious={false}
        canGoNext={false}
        selectedHitKey={null}
        onSelectHit={() => {
          // no-op
        }}
        onPreviousPage={() => {
          // no-op
        }}
        onNextPage={() => {
          // no-op
        }}
      />,
    );

    expect(consoleErrorSpy).not.toHaveBeenCalled();
  });
});
