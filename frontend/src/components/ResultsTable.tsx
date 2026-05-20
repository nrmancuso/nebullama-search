import type { SearchHit } from '../types/search';

type ResultsTableProps = {
  hits: SearchHit[];
  currentRangeLabel: string;
  canGoPrevious: boolean;
  canGoNext: boolean;
  selectedHitKey: string | null;
  onSelectHit: (hit: SearchHit) => void;
  onPreviousPage: () => void;
  onNextPage: () => void;
};

const MAX_SUMMARY_LENGTH = 250;

function getSourceString(
  source: Record<string, unknown>,
  keys: string[],
): string {
  for (const key of keys) {
    const value = source[key];
    if (typeof value === 'string' && value.trim().length > 0) {
      return value;
    }
  }

  return '';
}

function getResultLabel(hit: SearchHit): string {
  const label = getSourceString(hit.source, ['name', 'title', 'mission_name']);
  return label || hit.id;
}

function getSummary(hit: SearchHit): string {
  const summary = getSourceString(hit.source, [
    'description',
    'abstract',
    'notes',
    'biography',
  ]);

  if (summary.length <= MAX_SUMMARY_LENGTH) {
    return summary;
  }

  return `${summary.slice(0, MAX_SUMMARY_LENGTH)}…`;
}

export default function ResultsTable({
  hits,
  currentRangeLabel,
  canGoPrevious,
  canGoNext,
  selectedHitKey,
  onSelectHit,
  onPreviousPage,
  onNextPage,
}: ResultsTableProps) {
  return (
    <div className="results-table-shell">
      <p role="status">{currentRangeLabel}</p>
      <div>
        <button type="button" onClick={onPreviousPage} disabled={!canGoPrevious}>
          Previous page
        </button>
        <button type="button" onClick={onNextPage} disabled={!canGoNext}>
          Next page
        </button>
      </div>
      <table>
        <thead>
          <tr>
            <th>Name</th>
            <th>Resource Type</th>
            <th>Score</th>
            <th>Summary</th>
          </tr>
        </thead>
        <tbody>
          {hits.map((hit) => {
            const rowKey = `${hit.resourceType}:${hit.id}`;
            const isSelected = selectedHitKey === rowKey;
            const resultLabel = getResultLabel(hit);

            return (
              <tr key={rowKey} aria-selected={isSelected}>
                <td>
                  <button type="button" onClick={() => onSelectHit(hit)}>
                    {resultLabel}
                  </button>
                </td>
                <td>{hit.resourceType}</td>
                <td>{hit.score.toFixed(2)}</td>
                <td>{getSummary(hit)}</td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
