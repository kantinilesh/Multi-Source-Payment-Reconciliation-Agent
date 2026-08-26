import React, { useEffect, useState } from 'react';
import { Brain, Search, Zap, CheckCircle, AlertTriangle, FileQuestion } from 'lucide-react';
import StatusBadge from '../components/StatusBadge';
import ConfidenceGauge from '../components/ConfidenceGauge';
import LoadingSpinner from '../components/LoadingSpinner';
import { getMatches, aiExplain } from '../api/client';

function formatAmount(amount) {
  if (amount == null) return '—';
  return `₹${Number(amount).toLocaleString('en-IN', { minimumFractionDigits: 2 })}`;
}

export default function AiInvestigationPage({ runId }) {
  const [matches, setMatches] = useState([]);
  const [loading, setLoading] = useState(true);
  const [selected, setSelected] = useState(null);
  const [explaining, setExplaining] = useState(false);
  const [error, setError] = useState(null);

  useEffect(() => {
    if (!runId) { setLoading(false); return; }
    setLoading(true);
    getMatches(runId)
      .then((data) => {
        // Show REVIEW_REQUIRED and AI_ASSISTED cases
        const relevant = data.filter(m =>
          m.status === 'REVIEW_REQUIRED' || m.method === 'AI_ASSISTED' ||
          m.status === 'EXCEPTION'
        );
        setMatches(relevant);
        if (relevant.length > 0) setSelected(relevant[0]);
      })
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false));
  }, [runId]);

  const handleAiExplain = async (match) => {
    if (!match) return;
    setExplaining(true);
    try {
      const updated = await aiExplain(runId, match.id);
      setSelected(updated);
      setMatches(prev => prev.map(m => m.id === updated.id ? updated : m));
    } catch (e) {
      setError(e.message);
    } finally {
      setExplaining(false);
    }
  };

  if (!runId) return (
    <div>
      <div className="page-header"><h2>AI Investigation</h2></div>
      <div className="empty-state">
        <Brain className="empty-icon" />
        <h3>No data available</h3>
        <p>Upload and reconcile files to see AI investigation results.</p>
      </div>
    </div>
  );

  if (loading) return <LoadingSpinner message="Loading AI investigation cases..." />;

  return (
    <div>
      <div className="page-header">
        <h2>AI Investigation</h2>
        <p>Ambiguous cases analyzed by the AI Exception Reasoning Agent</p>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: '300px 1fr', gap: 20 }}>
        {/* Case List */}
        <div className="glass-card no-hover" style={{ padding: 0, maxHeight: '70vh', overflowY: 'auto' }}>
          {matches.length === 0 ? (
            <div className="empty-state" style={{ padding: 32 }}>
              <CheckCircle className="empty-icon" style={{ width: 32, height: 32 }} />
              <h3 style={{ fontSize: 14 }}>All Clear</h3>
              <p style={{ fontSize: 12 }}>No ambiguous cases requiring AI investigation.</p>
            </div>
          ) : matches.map((m) => (
            <div
              key={m.id}
              onClick={() => setSelected(m)}
              style={{
                padding: '12px 16px',
                borderBottom: '1px solid var(--border-subtle)',
                cursor: 'pointer',
                background: selected?.id === m.id ? 'var(--bg-card)' : 'transparent',
                transition: 'background 150ms',
              }}
            >
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 4 }}>
                <span style={{ fontFamily: 'var(--font-mono)', fontSize: 12, color: 'var(--text-primary)' }}>
                  {m.gatewayTxn?.externalRef || `#${m.id}`}
                </span>
                <StatusBadge status={m.status} />
              </div>
              <div style={{ fontSize: 11, color: 'var(--text-muted)' }}>
                {formatAmount(m.gatewayTxn?.amount)} · {m.method || 'DETERMINISTIC'}
              </div>
            </div>
          ))}
        </div>

        {/* Investigation Detail */}
        {selected ? (
          <div className="glass-card no-hover">
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 20 }}>
              <div>
                <h3 style={{ fontSize: 16, fontWeight: 700 }}>
                  {selected.gatewayTxn?.externalRef || `Match #${selected.id}`}
                </h3>
                <div style={{ display: 'flex', gap: 8, alignItems: 'center', marginTop: 6 }}>
                  <StatusBadge status={selected.status} />
                  {selected.confidence != null && <ConfidenceGauge value={selected.confidence} />}
                </div>
              </div>
              <button
                className="btn btn-primary btn-sm"
                disabled={explaining}
                onClick={() => handleAiExplain(selected)}
              >
                {explaining ? <><div className="spinner" style={{ width: 14, height: 14 }} /> Analyzing...</> : <><Brain size={14} /> Re-analyze with AI</>}
              </button>
            </div>

            {/* Evidence Pipeline */}
            <div className="evidence-pipeline">
              <div className="pipeline-step system">
                <div className="step-label">System Evidence</div>
                <div className="step-content">
                  <div className="info-grid" style={{ gap: 8 }}>
                    <div className="info-item">
                      <span className="info-label">Gateway</span>
                      <span className="info-value mono">{selected.gatewayTxn?.externalRef || 'Missing'}</span>
                    </div>
                    <div className="info-item">
                      <span className="info-label">Amount</span>
                      <span className="info-value mono">{formatAmount(selected.gatewayTxn?.amount)}</span>
                    </div>
                    <div className="info-item">
                      <span className="info-label">Bank</span>
                      <span className="info-value mono">{selected.bankTxn?.externalRef || 'Missing'}</span>
                    </div>
                    <div className="info-item">
                      <span className="info-label">Ledger</span>
                      <span className="info-value mono">{selected.ledgerTxn?.externalRef || 'Missing'}</span>
                    </div>
                  </div>
                </div>
              </div>

              <div className="pipeline-step ai">
                <div className="step-label">AI Agent Investigation</div>
                <div className="step-content">
                  {selected.reasoning || 'No AI reasoning available yet. Click "Re-analyze with AI" to generate.'}
                </div>
              </div>

              {selected.exceptionCategory && selected.exceptionCategory !== 'NONE' && (
                <div className="pipeline-step ai">
                  <div className="step-label">Exception Category</div>
                  <div className="step-content" style={{ color: 'var(--accent-rose)' }}>
                    {selected.exceptionCategory.replace(/_/g, ' ')}
                  </div>
                </div>
              )}

              <div className="pipeline-step decision">
                <div className="step-label">Decision</div>
                <div className="step-content">
                  <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                    <StatusBadge status={selected.status} />
                    <span style={{ fontSize: 12, color: 'var(--text-muted)' }}>
                      Method: {selected.method || 'DETERMINISTIC'}
                    </span>
                  </div>
                </div>
              </div>

              <div className="pipeline-step decision">
                <div className="step-label">Recommended Action</div>
                <div className="step-content">
                  {selected.status === 'RECONCILED' ? (
                    <span style={{ color: 'var(--accent-green)' }}>✓ No action required — transaction successfully reconciled</span>
                  ) : selected.status === 'EXCEPTION' ? (
                    <span style={{ color: 'var(--accent-rose)' }}>⚠ Requires manual investigation by finance team</span>
                  ) : (
                    <span style={{ color: 'var(--accent-amber)' }}>⏳ Under review — additional evidence needed</span>
                  )}
                </div>
              </div>
            </div>
          </div>
        ) : (
          <div className="empty-state">
            <FileQuestion className="empty-icon" />
            <h3>Select a case</h3>
            <p>Click an item from the list to see the AI investigation pipeline.</p>
          </div>
        )}
      </div>

      {error && <div className="glass-card" style={{ marginTop: 16, color: 'var(--accent-rose)', fontSize: 13 }}>⚠ {error}</div>}
    </div>
  );
}
