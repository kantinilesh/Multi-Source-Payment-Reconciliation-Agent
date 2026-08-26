import React, { useEffect, useState } from 'react';
import { AlertTriangle, Filter, ChevronDown, ChevronUp } from 'lucide-react';
import StatusBadge from '../components/StatusBadge';
import LoadingSpinner from '../components/LoadingSpinner';
import { getExceptions } from '../api/client';

function formatAmount(amount) {
  if (amount == null) return '—';
  return `₹${Number(amount).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
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
  AMOUNT_MISMATCH_BEYOND_TOLERANCE: { level: 'High', color: 'var(--semantic-danger)' },
  MISSING_IN_BANK_FILE: { level: 'Medium', color: 'var(--semantic-warning)' },
  MISSING_IN_LEDGER: { level: 'Medium', color: 'var(--semantic-warning)' },
  DUPLICATE_DETECTED: { level: 'High', color: 'var(--semantic-danger)' },
  REFUND_MISMATCH: { level: 'High', color: 'var(--semantic-danger)' },
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
    <div className="fade-in">
      <div className="page-header"><h2>Exception Queue Workbench</h2></div>
      <div className="surface-card" style={{ padding: 40, textAlign: 'center' }}>
        <AlertTriangle size={36} style={{ color: 'var(--text-muted)', marginBottom: 12 }} />
        <h3>No Active Run</h3>
        <p style={{ color: 'var(--text-secondary)', fontSize: 13 }}>Upload files to inspect the exception queue.</p>
      </div>
    </div>
  );

  if (loading) return <LoadingSpinner message="Loading exception queue..." />;
  if (error) return <div className="surface-card" style={{ color: 'var(--semantic-danger)' }}>Error: {error}</div>;

  const categories = ['ALL', ...new Set(exceptions.map(e => e.exceptionCategory).filter(c => c && c !== 'NONE'))];
  const filtered = categoryFilter === 'ALL'
    ? exceptions
    : exceptions.filter(e => e.exceptionCategory === categoryFilter);

  return (
    <div className="fade-in">
      <div className="page-header">
        <h2>Exception Queue Workbench</h2>
        <p>{exceptions.length} financial discrepancies requiring finance team investigation</p>
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

      <div className="data-table-wrapper">
        <table className="data-table">
          <thead>
            <tr>
              <th>ID</th>
              <th>Reference</th>
              <th>Issue Taxonomy</th>
              <th>Amount</th>
              <th>Priority</th>
              <th>Probable Cause Reasoning</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {filtered.map((e) => {
              const priority = PRIORITY_MAP[e.exceptionCategory] || { level: 'Low', color: 'var(--text-muted)' };
              const isExpanded = expanded === e.id;
              return (
                <React.Fragment key={e.id}>
                  <tr onClick={() => setExpanded(isExpanded ? null : e.id)}>
                    <td className="mono">#{e.id}</td>
                    <td className="mono">{e.gatewayTxn?.externalRef || '—'}</td>
                    <td>
                      <span style={{ color: 'var(--semantic-danger)', fontSize: 12, fontWeight: 600 }}>
                        {CATEGORY_LABELS[e.exceptionCategory] || e.exceptionCategory}
                      </span>
                    </td>
                    <td className="amount">{formatAmount(e.gatewayTxn?.amount || e.bankTxn?.amount)}</td>
                    <td>
                      <span style={{ fontSize: 11, fontWeight: 700, color: priority.color }}>
                        {priority.level}
                      </span>
                    </td>
                    <td style={{ fontSize: 12, color: 'var(--text-secondary)', maxWidth: 320, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                      {e.reasoning || '—'}
                    </td>
                    <td style={{ textAlign: 'right' }}>
                      {isExpanded ? <ChevronUp size={14} /> : <ChevronDown size={14} />}
                    </td>
                  </tr>
                  {isExpanded && (
                    <tr>
                      <td colSpan={7} style={{ padding: '16px 20px', background: 'var(--bg-surface-hover)' }}>
                        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 12, marginBottom: 12 }}>
                          <div>
                            <span style={{ fontSize: 10, color: 'var(--text-muted)', textTransform: 'uppercase' }}>Bank Record</span>
                            <div className="mono" style={{ fontSize: 12 }}>{e.bankTxn?.externalRef || 'Missing'}</div>
                          </div>
                          <div>
                            <span style={{ fontSize: 10, color: 'var(--text-muted)', textTransform: 'uppercase' }}>Ledger Record</span>
                            <div className="mono" style={{ fontSize: 12 }}>{e.ledgerTxn?.externalRef || 'Missing'}</div>
                          </div>
                          <div>
                            <span style={{ fontSize: 10, color: 'var(--text-muted)', textTransform: 'uppercase' }}>Resolution Guidance</span>
                            <div style={{ fontSize: 12, color: 'var(--semantic-warning)', fontWeight: 600 }}>Manual inspection required</div>
                          </div>
                        </div>
                        {e.reasoning && (
                          <div style={{ background: 'var(--bg-input)', padding: 12, borderRadius: 'var(--radius-sm)', fontSize: 12, color: 'var(--text-secondary)', lineHeight: 1.5 }}>
                            <strong style={{ color: 'var(--text-primary)' }}>Full Investigation Detail:</strong> {e.reasoning}
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
  );
}
