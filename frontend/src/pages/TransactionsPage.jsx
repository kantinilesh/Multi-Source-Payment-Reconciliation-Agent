import React, { useEffect, useState } from 'react';
import { ArrowLeftRight, X, Layers, CheckCircle2, AlertTriangle, Clock } from 'lucide-react';
import StatusBadge from '../components/StatusBadge';
import LoadingSpinner from '../components/LoadingSpinner';
import { getMatches } from '../api/client';

function formatAmount(amount) {
  if (amount == null) return '—';
  return `₹${Number(amount).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
}

function formatDate(ts) {
  if (!ts) return '—';
  return new Date(ts).toLocaleString('en-IN', { dateStyle: 'medium', timeStyle: 'short' });
}

function DetailPanel({ match, onClose }) {
  if (!match) return null;

  return (
    <>
      <div className="panel-overlay" onClick={onClose} />
      <div className="detail-panel">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 20, paddingBottom: 16, borderBottom: '1px solid var(--border-subtle)' }}>
          <div>
            <h3 style={{ fontSize: 16, fontWeight: 700, color: 'var(--text-primary)' }}>Transaction Investigation</h3>
            <div style={{ fontSize: 11, color: 'var(--text-muted)', marginTop: 2 }}>Match Record #{match.id}</div>
          </div>
          <button className="btn btn-secondary btn-sm" onClick={onClose} style={{ padding: 6 }}>
            <X size={14} />
          </button>
        </div>

        {/* Status Header */}
        <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 20 }}>
          <StatusBadge status={match.status} />
          <span style={{ fontSize: 12, color: 'var(--text-muted)' }}>
            Confidence: <strong style={{ color: 'var(--text-primary)' }}>{Math.round((match.confidence || 0.85) * 100)}%</strong>
          </span>
          <span style={{ fontSize: 12, color: 'var(--text-muted)' }}>
            Method: <strong style={{ color: 'var(--text-primary)' }}>{match.method || 'DETERMINISTIC'}</strong>
          </span>
        </div>

        {/* 3-Way Relationship Node Map */}
        <div style={{ background: 'var(--bg-input)', border: '1px solid var(--border-subtle)', borderRadius: 'var(--radius-md)', padding: 16, marginBottom: 20 }}>
          <div style={{ fontSize: 11, fontWeight: 600, textTransform: 'uppercase', color: 'var(--text-muted)', marginBottom: 12 }}>
            3-Way Source Node Map
          </div>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', background: 'var(--bg-surface)', padding: '10px 12px', borderRadius: 'var(--radius-sm)', border: '1px solid var(--border-subtle)' }}>
              <span style={{ fontSize: 12, fontWeight: 600, color: 'var(--accent-primary)' }}>Payment Gateway</span>
              <span className="mono" style={{ fontSize: 12 }}>{match.gatewayTxn?.externalRef || '—'}</span>
              <span className="amount">{formatAmount(match.gatewayTxn?.amount)}</span>
            </div>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', background: 'var(--bg-surface)', padding: '10px 12px', borderRadius: 'var(--radius-sm)', border: '1px solid var(--border-subtle)' }}>
              <span style={{ fontSize: 12, fontWeight: 600, color: 'var(--semantic-success)' }}>Bank Settlement</span>
              <span className="mono" style={{ fontSize: 12 }}>{match.bankTxn?.externalRef || '—'}</span>
              <span className="amount">{formatAmount(match.bankTxn?.amount)}</span>
            </div>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', background: 'var(--bg-surface)', padding: '10px 12px', borderRadius: 'var(--radius-sm)', border: '1px solid var(--border-subtle)' }}>
              <span style={{ fontSize: 12, fontWeight: 600, color: 'var(--semantic-warning)' }}>Internal Ledger</span>
              <span className="mono" style={{ fontSize: 12 }}>{match.ledgerTxn?.externalRef || '—'}</span>
              <span className="amount">{formatAmount(match.ledgerTxn?.amount)}</span>
            </div>
          </div>
        </div>

        {/* Evidence & Reasoning */}
        <div style={{ marginBottom: 20 }}>
          <div style={{ fontSize: 11, fontWeight: 600, textTransform: 'uppercase', color: 'var(--text-muted)', marginBottom: 8 }}>
            Engine Rationale & Evidence
          </div>
          <div style={{ background: 'var(--bg-surface)', border: '1px solid var(--border-subtle)', borderRadius: 'var(--radius-sm)', padding: 14, fontSize: 13, color: 'var(--text-secondary)', lineHeight: 1.6 }}>
            {match.reasoning || 'Transaction matched with high confidence by deterministic engine.'}
          </div>
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
    <div className="fade-in">
      <div className="page-header"><h2>Transaction Explorer</h2></div>
      <div className="surface-card" style={{ padding: 40, textAlign: 'center' }}>
        <ArrowLeftRight size={36} style={{ color: 'var(--text-muted)', marginBottom: 12 }} />
        <h3>No Run Active</h3>
        <p style={{ color: 'var(--text-secondary)', fontSize: 13 }}>Upload files to explore individual transaction match records.</p>
      </div>
    </div>
  );

  if (loading) return <LoadingSpinner message="Loading transactions..." />;
  if (error) return <div className="surface-card" style={{ color: 'var(--semantic-danger)' }}>Error: {error}</div>;

  const filtered = filter === 'ALL' ? matches : matches.filter(m => m.status === filter);

  return (
    <div className="fade-in">
      <div className="page-header">
        <h2>Transaction Explorer</h2>
        <p>{matches.length} transaction match records · Click any row to inspect 3-way node map</p>
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

      <div className="data-table-wrapper">
        <table className="data-table">
          <thead>
            <tr>
              <th>ID</th>
              <th>Gateway Ref</th>
              <th>Bank Ref</th>
              <th>Amount</th>
              <th>Status</th>
              <th>Confidence</th>
              <th>Method</th>
            </tr>
          </thead>
          <tbody>
            {filtered.map((m) => (
              <tr key={m.id} onClick={() => setSelected(m)}>
                <td className="mono">#{m.id}</td>
                <td className="mono">{m.gatewayTxn?.externalRef || '—'}</td>
                <td className="mono">{m.bankTxn?.externalRef || '—'}</td>
                <td className="amount">{formatAmount(m.gatewayTxn?.amount || m.bankTxn?.amount)}</td>
                <td><StatusBadge status={m.status} /></td>
                <td className="mono" style={{ fontSize: 12 }}>{Math.round((m.confidence || 0.85) * 100)}%</td>
                <td style={{ fontSize: 12, color: 'var(--text-muted)' }}>{m.method || 'DETERMINISTIC'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <DetailPanel match={selected} onClose={() => setSelected(null)} />
    </div>
  );
}
