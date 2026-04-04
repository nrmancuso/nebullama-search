import type { SearchHit, SearchInterpretation } from '../types/search';

type DetailsDrawerProps = {
  hit: SearchHit;
  interpretation: SearchInterpretation | null;
  onClose: () => void;
};

function formatValue(value: unknown): string {
  if (typeof value === 'string') {
    return value;
  }

  return JSON.stringify(value, null, 2);
}

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

function getFullDocumentText(source: Record<string, unknown>): string {
  return getSourceString(source, ['description', 'abstract', 'notes', 'biography']);
}

export default function DetailsDrawer({
  hit,
  interpretation,
  onClose,
}: DetailsDrawerProps) {
  const resultLabel = getResultLabel(hit);
  const fullDocumentText = getFullDocumentText(hit.source);

  return (
    <div className="details-modal-backdrop" onClick={onClose}>
      <section
        aria-labelledby="result-details-heading"
        aria-modal="true"
        className="details-modal"
        onClick={(event) => event.stopPropagation()}
        role="dialog"
      >
        <header>
          <h2 id="result-details-heading">{resultLabel}</h2>
          <button type="button" onClick={onClose}>
            Close details
          </button>
        </header>

        <section aria-labelledby="result-details-metadata">
          <h3 id="result-details-metadata">Result metadata</h3>
          <dl>
            <div>
              <dt>id</dt>
              <dd>{hit.id}</dd>
            </div>
            <div>
              <dt>resourceType</dt>
              <dd>{hit.resourceType}</dd>
            </div>
            <div>
              <dt>score</dt>
              <dd>{hit.score.toFixed(2)}</dd>
            </div>
          </dl>
        </section>

        {fullDocumentText ? (
          <section aria-labelledby="result-details-source">
            <h3 id="result-details-source">Full document</h3>
            <div className="full-document-content">{fullDocumentText}</div>
          </section>
        ) : null}

        <section aria-labelledby="result-details-raw-source">
          <h3 id="result-details-raw-source">Raw source</h3>
          <pre className="raw-source-content">{JSON.stringify(hit.source, null, 2)}</pre>
        </section>

        <section aria-labelledby="result-details-interpretation">
          <h3 id="result-details-interpretation">Interpretation metadata</h3>
          {interpretation ? (
            <dl>
              <div>
                <dt>Search mode</dt>
                <dd>{interpretation.searchMode}</dd>
              </div>
              <div>
                <dt>Rewritten query</dt>
                <dd>{interpretation.rewrittenQuery || 'n/a'}</dd>
              </div>
              <div>
                <dt>Extracted filters</dt>
                <dd>
                  <pre>{formatValue(interpretation.extractedFilters ?? {})}</pre>
                </dd>
              </div>
            </dl>
          ) : (
            <p>No interpretation metadata returned.</p>
          )}
        </section>
      </section>
    </div>
  );
}
