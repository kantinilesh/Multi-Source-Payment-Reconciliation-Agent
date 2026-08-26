import React, { useEffect, useState } from 'react';
import { ArrowLeftRight, X, ExternalLink } from 'lucide-react';
import StatusBadge from '../components/StatusBadge';
import ConfidenceGauge from '../components/ConfidenceGauge';
import LoadingSpinner from '../components/LoadingSpinner';
import { getMatches } from '../api/client';

function formatAmount(amount) {
  if (amount == null) return '—';
  return `₹${Number(amount).toLocaleString('en-IN', { minimumFractionDigits: 2 })}`;
}

function formatDate(ts) {
  if (!ts) return '—';
  return new Date(ts).toLocaleString('en-IN', { dateStyle: 'medium', timeStyle: 'short' });
}

function TxnCard({ label, txn, accent }) {
  if (!txn) return (
    <div className="glass-card no-hover" style={{ opacity: 0.5 }}>
      <div className="section-title">{label}</div>
      <p style={{ fontSize: 13, color: 'var(--text-muted)' }}>No matching record found</p>
    </div>
  );
  return (
    <div className="glass-card no-hover" style={{ borderColor: accent }}>
      <div className="section-title">{label}</div>
      <div className="info-grid">
        <div className="info-item">
          <span className="info-label">Reference</span>
          <span className="info-value mono">{txn.externalRef}</span>
        </div>
        <div className="info-item">
          <span className="info-label">Amount</span>
          <span className="info-value mono">{formatAmount(txn.amount)}</span>
        </div>
        <div className="info-item">
          <span className="info-label">Source</span>
          <span className="info-value">{txn.sourceType}</span>
        </div>
        <div className="info-item">
          <span className="info-label">Timestamp</span>
          <span className="info-value mono" style={{ fontSize: 11 }}>{formatDate(txn.timestamp)}</span>
        </div>
      </div>
    </div>
  );
}

function DetailPanel({ match, onClose }) {
  if (!match) return null;
  return (
    <>
      <div className="panel-overlay" onClick={onClose} />
      <div className="detail-panel">
        <div className="panel-header">
          <div>
            <h3>Transaction Detail</h3>
            <p style={{ fontSize: 12, color: 'var(--text-muted)', marginTop: 4 }}>
              Match #{match.id}
            </p>
          </div>
          <button className="close-btn" onClick={onClose}>
            <X size={16} />
          </button>
        </div>

        <div className="section-block">
          <div className="section-title">Decision</div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 12 }}>
            <StatusBadge status={match.status} />
            {match.confidence != null && <ConfidenceGauge value={match.confidence} />}
          </div>
          {match.method && (
            <div className="info-item" style={{ marginBottom: 8 }}>
              <span className="info-label">Method</span>
              <span className="info-value">{match.method}</span>
            </div>
          )}
          {match.exceptionCategory && match.exceptionCategory !== 'NONE' && (
            <div className="info-item" style={{ marginBottom: 8 }}>
              <span className="info-label">Exception Category</span>
              <span className="info-value" style={{ color: 'var(--accent-rose)' }}>
                {match.exceptionCategory.replace(/_/g, ' ')}
              </span>
            </div>
          )}
        </div>

        <div className="section-block">
          <div className="section-title">Matching Evidence</div>
          {match.reasoning && (
            <div style={{ background: 'var(--bg-card)', border: '1px solid var(--border-card)', borderRadius: 'var(--radius-sm)', padding: '12px 16px', fontSize: 13, color: 'var(--text-secondary)', lineHeight: 1.6 }}>
              {match.reasoning}
            </div>
          )}
        </div>

        <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
          <TxnCard label="Gateway Record" txn={match.gatewayTxn} accent="var(--accent-blue)" />
          <TxnCard label="Bank Record" txn={match.bankTxn} accent="var(--accent-green)" />
          <TxnCard label="Ledger Record" txn={match.ledgerTxn} accent="var(--accent-purple)" />
        </div>
      </div>
    </>
  );
}

export default function TransactionsPage({ runId }) {
  const [matches, setMatches] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [selected, setSelected] = useState(null);
  const [filter, setFilter] = useState('ALL');

  useEffect(() => {
    if (!runId) { setLoading(false); return; }
    setLoading(true);
    getMatches(runId)
      .then(setMatches)
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false));
  }, [runId]);

  if (!runId) return (
    <div>
      <div className="page-header"><h2>Transaction Explorer</h2></div>
      <div className="empty-state">
        <ArrowLeftRight className="empty-icon" />
        <h3>No data available</h3>
        <p>Upload and reconcile files to explore individual transactions.</p>
      </div>
    </div>
  );

  if (loading) return <LoadingSpinner message="Loading transactions..." />;
  if (error) return <div className="glass-card" style={{ color: 'var(--accent-rose)' }}>Error: {error}</div>;

  const filtered = filter === 'ALL' ? matches : matches.filter(m => m.status === filter);

  return (
    <div>
      <div className="page-header">
        <h2>Transaction Explorer</h2>
        <p>{matches.length} match results — click to inspect</p>
      </div>

      <div className="filter-bar">
        {['ALL', 'RECONCILED', 'EXCEPTION', 'REVIEW_REQUIRED'].map((f) => (
          <button
            key={f}
            className={`filter-chip ${filter === f ? 'active' : ''}`}
            onClick={() => setFilter(f)}
          >
            {f === 'ALL' ? `All (${matches.length})` : `${f.replace(/_/g, ' ')} (${matches.filter(m => m.status === f).length})`}
          </button>
        ))}
      </div>

      <div className="glass-card no-hover" style={{ padding: 0 }}>
        <div className="data-table-wrapper">
          <table className="data-table">
            <thead>
              <tr>
                <th>ID</th>
                <th>Gateway Ref</th>
                <th>Amount</th>
                <th>Status</th>
                <th>Confidence</th>
                <th>Method</th>
                <th>Exception</th>
              </tr>
            </thead>
            <tbody>
              {filtered.map((m) => (
                <tr key={m.id} onClick={() => setSelected(m)}>
                  <td className="mono">#{m.id}</td>
                  <td className="mono">{m.gatewayTxn?.externalRef || '—'}</td>
                  <td className="amount">{formatAmount(m.gatewayTxn?.amount)}</td>
                  <td><StatusBadge status={m.status} /></td>
                  <td>{m.confidence != null ? <ConfidenceGauge value={m.confidence} /> : '—'}</td>
                  <td style={{ fontSize: 12, color: 'var(--text-muted)' }}>{m.method || '—'}</td>
                  <td style={{ fontSize: 12, color: m.exceptionCategory !== 'NONE' ? 'var(--accent-rose)' : 'var(--text-muted)' }}>
                    {m.exceptionCategory !== 'NONE' ? m.exceptionCategory?.replace(/_/g, ' ') : '—'}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      <DetailPanel match={selected} onClose={() => setSelected(null)} />
    </div>
  );
}
