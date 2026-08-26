import React, { useEffect, useState } from 'react';
import { AlertTriangle, ArrowUpDown } from 'lucide-react';
import StatusBadge from '../components/StatusBadge';
import ConfidenceGauge from '../components/ConfidenceGauge';
import LoadingSpinner from '../components/LoadingSpinner';
import { getExceptions } from '../api/client';

function formatAmount(amount) {
  if (amount == null) return '—';
  return `₹${Number(amount).toLocaleString('en-IN', { minimumFractionDigits: 2 })}`;
}

const CATEGORY_LABELS = {
  MISSING_IN_BANK_FILE: 'Missing in Bank',
  MISSING_IN_LEDGER: 'Missing in Ledger',
  MISSING_IN_GATEWAY: 'Missing in Gateway',
  AMOUNT_MISMATCH_BEYOND_TOLERANCE: 'Amount Mismatch',
  DUPLICATE_CANDIDATE: 'Duplicate Candidate',
  DUPLICATE_DETECTED: 'Duplicate Detected',
  AMBIGUOUS_MULTI_MATCH: 'Ambiguous Match',
  REFUND_MISMATCH: 'Refund Mismatch',
  NONE: '—',
};

const PRIORITY_MAP = {
  AMOUNT_MISMATCH_BEYOND_TOLERANCE: { level: 'High', color: 'var(--accent-rose)' },
  MISSING_IN_BANK_FILE: { level: 'Medium', color: 'var(--accent-amber)' },
  MISSING_IN_LEDGER: { level: 'Medium', color: 'var(--accent-amber)' },
  DUPLICATE_DETECTED: { level: 'High', color: 'var(--accent-rose)' },
  REFUND_MISMATCH: { level: 'High', color: 'var(--accent-rose)' },
};

export default function ExceptionsPage({ runId }) {
  const [exceptions, setExceptions] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [categoryFilter, setCategoryFilter] = useState('ALL');
  const [expanded, setExpanded] = useState(null);

  useEffect(() => {
    if (!runId) { setLoading(false); return; }
    setLoading(true);
    getExceptions(runId)
      .then(setExceptions)
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false));
  }, [runId]);

  if (!runId) return (
    <div>
      <div className="page-header"><h2>Exception Queue</h2></div>
      <div className="empty-state">
        <AlertTriangle className="empty-icon" />
        <h3>No data available</h3>
        <p>Upload and reconcile files to see exceptions.</p>
      </div>
    </div>
  );

  if (loading) return <LoadingSpinner message="Loading exceptions..." />;
  if (error) return <div className="glass-card" style={{ color: 'var(--accent-rose)' }}>Error: {error}</div>;

  const categories = ['ALL', ...new Set(exceptions.map(e => e.exceptionCategory).filter(c => c && c !== 'NONE'))];
  const filtered = categoryFilter === 'ALL'
    ? exceptions
    : exceptions.filter(e => e.exceptionCategory === categoryFilter);

  return (
    <div>
      <div className="page-header">
        <h2>Exception Queue</h2>
        <p>{exceptions.length} exceptions requiring attention</p>
      </div>

      <div className="filter-bar">
        {categories.map((c) => (
          <button
            key={c}
            className={`filter-chip ${categoryFilter === c ? 'active' : ''}`}
            onClick={() => setCategoryFilter(c)}
          >
            {c === 'ALL' ? `All (${exceptions.length})` : `${CATEGORY_LABELS[c] || c} (${exceptions.filter(e => e.exceptionCategory === c).length})`}
          </button>
        ))}
      </div>

      <div className="glass-card no-hover" style={{ padding: 0 }}>
        <div className="data-table-wrapper">
          <table className="data-table">
            <thead>
              <tr>
                <th>ID</th>
                <th>Reference</th>
                <th>Exception Type</th>
                <th>Amount</th>
                <th>Confidence</th>
                <th>Priority</th>
                <th>Probable Reason</th>
              </tr>
            </thead>
            <tbody>
              {filtered.map((e) => {
                const priority = PRIORITY_MAP[e.exceptionCategory] || { level: 'Low', color: 'var(--text-muted)' };
                return (
                  <React.Fragment key={e.id}>
                    <tr onClick={() => setExpanded(expanded === e.id ? null : e.id)}>
                      <td className="mono">#{e.id}</td>
                      <td className="mono">{e.gatewayTxn?.externalRef || '—'}</td>
                      <td>
                        <span style={{ color: 'var(--accent-rose)', fontSize: 12, fontWeight: 500 }}>
                          {CATEGORY_LABELS[e.exceptionCategory] || e.exceptionCategory}
                        </span>
                      </td>
                      <td className="amount">{formatAmount(e.gatewayTxn?.amount)}</td>
                      <td>{e.confidence != null ? <ConfidenceGauge value={e.confidence} /> : '—'}</td>
                      <td>
                        <span style={{ fontSize: 12, fontWeight: 600, color: priority.color }}>
                          {priority.level}
                        </span>
                      </td>
                      <td style={{ fontSize: 12, color: 'var(--text-secondary)', maxWidth: 300, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                        {e.reasoning || '—'}
                      </td>
                    </tr>
                    {expanded === e.id && (
                      <tr>
                        <td colSpan={7} style={{ padding: '16px 24px', background: 'var(--bg-card)' }}>
                          <div className="info-grid" style={{ gap: 12 }}>
                            <div className="info-item">
                              <span className="info-label">Method</span>
                              <span className="info-value">{e.method || 'DETERMINISTIC'}</span>
                            </div>
                            <div className="info-item">
                              <span className="info-label">Bank Record</span>
                              <span className="info-value mono">{e.bankTxn?.externalRef || 'Missing'}</span>
                            </div>
                            <div className="info-item">
                              <span className="info-label">Ledger Record</span>
                              <span className="info-value mono">{e.ledgerTxn?.externalRef || 'Missing'}</span>
                            </div>
                            <div className="info-item">
                              <span className="info-label">Recommended Action</span>
                              <span className="info-value" style={{ color: 'var(--accent-amber)' }}>
                                Manual investigation required
                              </span>
                            </div>
                          </div>
                          {e.reasoning && (
                            <div style={{ marginTop: 12, padding: '12px 16px', background: 'var(--bg-input)', borderRadius: 'var(--radius-sm)', fontSize: 13, color: 'var(--text-secondary)', lineHeight: 1.6 }}>
                              <strong style={{ color: 'var(--text-primary)' }}>Full Reasoning:</strong> {e.reasoning}
                            </div>
                          )}
                        </td>
                      </tr>
                    )}
                  </React.Fragment>
                );
              })}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}
