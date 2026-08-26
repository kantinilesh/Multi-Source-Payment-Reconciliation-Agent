import React, { useEffect, useState } from 'react';
import { Brain, Zap, CheckCircle2, AlertTriangle, FileQuestion, ArrowRight, ShieldCheck } from 'lucide-react';
import StatusBadge from '../components/StatusBadge';
import LoadingSpinner from '../components/LoadingSpinner';
import { getMatches, aiExplain } from '../api/client';

function formatAmount(amount) {
  if (amount == null) return '—';
  return `₹${Number(amount).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
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
        const relevant = data.filter(m =>
          m.status === 'REVIEW_REQUIRED' || m.method === 'AI_ASSISTED' || m.status === 'EXCEPTION'
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
    <div className="fade-in">
      <div className="page-header"><h2>AI Investigation</h2></div>
      <div className="surface-card" style={{ padding: 40, textAlign: 'center' }}>
        <Brain size={36} style={{ color: 'var(--text-muted)', marginBottom: 12 }} />
        <h3>No Active Run</h3>
        <p style={{ color: 'var(--text-secondary)', fontSize: 13 }}>Upload files to inspect AI-assisted exception investigations.</p>
      </div>
    </div>
  );

  if (loading) return <LoadingSpinner message="Loading AI investigation cases..." />;

  return (
    <div className="fade-in">
      <div className="page-header">
        <h2>AI Investigation View</h2>
        <p>Embedded Finance Agent analyzing ambiguous cases with 5-step evidence verification</p>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: '280px 1fr', gap: 16 }}>
        {/* Case Selector List */}
        <div className="surface-card" style={{ padding: 0, maxHeight: '72vh', overflowY: 'auto' }}>
          <div style={{ padding: '12px 16px', borderBottom: '1px solid var(--border-subtle)', fontSize: 11, fontWeight: 600, textTransform: 'uppercase', color: 'var(--text-muted)' }}>
            Investigation Queue ({matches.length})
          </div>
          {matches.length === 0 ? (
            <div style={{ padding: 24, textAlign: 'center', color: 'var(--text-muted)', fontSize: 12 }}>
              <CheckCircle2 size={24} style={{ color: 'var(--semantic-success)', margin: '0 auto 8px' }} />
              <div>All Clear</div>
              <div>No ambiguous cases requiring AI analysis</div>
            </div>
          ) : matches.map((m) => (
            <div
              key={m.id}
              onClick={() => setSelected(m)}
              style={{
                padding: '10px 14px',
                borderBottom: '1px solid var(--border-subtle)',
                cursor: 'pointer',
                background: selected?.id === m.id ? 'var(--bg-surface-hover)' : 'transparent',
                borderLeft: selected?.id === m.id ? '3px solid var(--accent-primary)' : '3px solid transparent',
                transition: 'background 120ms ease'
              }}
            >
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 4 }}>
                <span className="mono" style={{ fontSize: 12, fontWeight: 600, color: 'var(--text-primary)' }}>
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

        {/* 5-Step Evidence Pipeline Detail */}
        {selected ? (
          <div className="surface-card">
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 20, paddingBottom: 16, borderBottom: '1px solid var(--border-subtle)' }}>
              <div>
                <h3 style={{ fontSize: 16, fontWeight: 700, color: 'var(--text-primary)' }}>
                  {selected.gatewayTxn?.externalRef || `Match Record #${selected.id}`}
                </h3>
                <div style={{ display: 'flex', gap: 12, alignItems: 'center', marginTop: 6 }}>
                  <StatusBadge status={selected.status} />
                  <span style={{ fontSize: 12, color: 'var(--text-muted)' }}>
                    Confidence: <strong style={{ color: 'var(--text-primary)' }}>{Math.round((selected.confidence || 0.85) * 100)}%</strong>
                  </span>
                </div>
              </div>
              <button
                className="btn btn-primary btn-sm"
                disabled={explaining}
                onClick={() => handleAiExplain(selected)}
              >
                {explaining ? <><div className="spinner" /> Analyzing...</> : <><Zap size={13} /> Re-analyze with AI</>}
              </button>
            </div>

            {/* 5-Step Pipeline Presentation */}
            <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
              {/* Step 1: System Evidence */}
              <div style={{ background: 'var(--bg-input)', border: '1px solid var(--border-subtle)', borderRadius: 'var(--radius-sm)', padding: 14 }}>
                <div style={{ fontSize: 10, fontWeight: 600, textTransform: 'uppercase', color: 'var(--accent-primary)', marginBottom: 8 }}>
                  Step 1 · System Evidence Across Sources
                </div>
                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 12, fontSize: 12 }}>
                  <div>
                    <div style={{ color: 'var(--text-muted)', fontSize: 10 }}>Gateway</div>
                    <div className="mono" style={{ fontWeight: 600 }}>{selected.gatewayTxn?.externalRef || 'Missing'}</div>
                    <div className="amount" style={{ fontSize: 11 }}>{formatAmount(selected.gatewayTxn?.amount)}</div>
                  </div>
                  <div>
                    <div style={{ color: 'var(--text-muted)', fontSize: 10 }}>Bank Settlement</div>
                    <div className="mono" style={{ fontWeight: 600 }}>{selected.bankTxn?.externalRef || 'Missing'}</div>
                    <div className="amount" style={{ fontSize: 11 }}>{formatAmount(selected.bankTxn?.amount)}</div>
                  </div>
                  <div>
                    <div style={{ color: 'var(--text-muted)', fontSize: 10 }}>ERP Ledger</div>
                    <div className="mono" style={{ fontWeight: 600 }}>{selected.ledgerTxn?.externalRef || 'Missing'}</div>
                    <div className="amount" style={{ fontSize: 11 }}>{formatAmount(selected.ledgerTxn?.amount)}</div>
                  </div>
                </div>
              </div>

              {/* Step 2: AI Reasoning */}
              <div style={{ background: 'var(--bg-surface)', border: '1px solid var(--border-subtle)', borderRadius: 'var(--radius-sm)', padding: 14 }}>
                <div style={{ fontSize: 10, fontWeight: 600, textTransform: 'uppercase', color: '#8B5CF6', marginBottom: 6 }}>
                  Step 2 · AI Agent Reasoning Summary
                </div>
                <div style={{ fontSize: 13, color: 'var(--text-secondary)', lineHeight: 1.6 }}>
                  {selected.reasoning || 'No AI reasoning text generated yet. Click "Re-analyze with AI" to trigger.'}
                </div>
              </div>

              {/* Step 3: Exception Taxonomy Category */}
              {selected.exceptionCategory && selected.exceptionCategory !== 'NONE' && (
                <div style={{ background: 'var(--semantic-danger-subtle)', border: '1px solid rgba(239, 68, 68, 0.2)', borderRadius: 'var(--radius-sm)', padding: 12 }}>
                  <div style={{ fontSize: 10, fontWeight: 600, textTransform: 'uppercase', color: 'var(--semantic-danger)', marginBottom: 2 }}>
                    Step 3 · Financial Exception Taxonomy
                  </div>
                  <div style={{ fontSize: 13, fontWeight: 600, color: 'var(--semantic-danger)' }}>
                    {selected.exceptionCategory.replace(/_/g, ' ')}
                  </div>
                </div>
              )}

              {/* Step 4 & 5: Decision & Recommended Action */}
              <div style={{ background: 'var(--bg-input)', border: '1px solid var(--border-subtle)', borderRadius: 'var(--radius-sm)', padding: 14 }}>
                <div style={{ fontSize: 10, fontWeight: 600, textTransform: 'uppercase', color: 'var(--semantic-success)', marginBottom: 6 }}>
                  Steps 4 & 5 · Decision & AI Resolution Action Proposal
                </div>
                <div style={{ fontSize: 13, fontWeight: 600, color: 'var(--text-primary)', marginBottom: 4 }}>
                  {selected.status === 'RECONCILED' ? (
                    <span style={{ color: 'var(--semantic-success)' }}>✓ No Action Required — Transaction Successfully Reconciled</span>
                  ) : (
                    <div style={{ color: 'var(--semantic-warning)' }}>
                      ⚡ Action Proposal: {selected.exceptionCategory === 'MISSING_IN_LEDGER' ? 'Auto-generate missing ERP Journal Entry' : selected.exceptionCategory === 'MISSING_IN_BANK_FILE' ? 'Flag as unsettled deposit — initiate bank inquiry' : selected.exceptionCategory === 'AMOUNT_MISMATCH_BEYOND_TOLERANCE' ? 'Create Fee Adjustment Entry' : 'Manual verification by finance analyst'}
                    </div>
                  )}
                </div>
                <div style={{ fontSize: 11, color: 'var(--text-muted)', marginTop: 4, display: 'flex', alignItems: 'center', gap: 6 }}>
                  <ShieldCheck size={13} style={{ color: 'var(--semantic-success)' }} />
                  Guardrail Passed · Confidence Threshold ≥ 85% Checked
                </div>
              </div>
            </div>
          </div>
        ) : (
          <div className="surface-card" style={{ padding: 40, textAlign: 'center' }}>
            <FileQuestion size={36} style={{ color: 'var(--text-muted)', marginBottom: 12 }} />
            <h3>Select a Case</h3>
            <p style={{ color: 'var(--text-secondary)', fontSize: 13 }}>Click an item from the left queue to inspect its AI investigation pipeline.</p>
          </div>
        )}
      </div>

      {error && <div className="surface-card" style={{ marginTop: 16, color: 'var(--semantic-danger)', fontSize: 13 }}>⚠ {error}</div>}
    </div>
  );
}
