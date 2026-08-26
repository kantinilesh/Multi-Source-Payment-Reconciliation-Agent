import React, { useEffect, useState } from 'react';
import { ClipboardList, Bot, Cpu } from 'lucide-react';
import LoadingSpinner from '../components/LoadingSpinner';
import { getAuditLog } from '../api/client';

function formatTime(ts) {
  if (!ts) return '—';
  return new Date(ts).toLocaleString('en-IN', { dateStyle: 'medium', timeStyle: 'medium' });
}

export default function AuditPage({ runId }) {
  const [entries, setEntries] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    if (!runId) { setLoading(false); return; }
    setLoading(true);
    getAuditLog(runId)
      .then(setEntries)
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false));
  }, [runId]);

  if (!runId) return (
    <div className="fade-in">
      <div className="page-header"><h2>Immutable Audit Trail</h2></div>
      <div className="surface-card" style={{ padding: 40, textAlign: 'center' }}>
        <ClipboardList size={36} style={{ color: 'var(--text-muted)', marginBottom: 12 }} />
        <h3>No Active Run</h3>
        <p style={{ color: 'var(--text-secondary)', fontSize: 13 }}>Upload files to inspect system audit trail logs.</p>
      </div>
    </div>
  );

  if (loading) return <LoadingSpinner message="Loading audit trail..." />;
  if (error) return <div className="surface-card" style={{ color: 'var(--semantic-danger)' }}>Error: {error}</div>;

  return (
    <div className="fade-in">
      <div className="page-header">
        <h2>Immutable Audit Trail</h2>
        <p>{entries.length} chronological audit entries logged for compliance and traceability</p>
      </div>

      <div className="surface-card">
        <div style={{ position: 'relative', paddingLeft: 24 }}>
          <div style={{ position: 'absolute', top: 8, bottom: 8, left: 7, width: 2, background: 'var(--border-subtle)' }} />
          {entries.map((entry) => {
            const isAi = entry.method === 'AI_ASSISTED';
            return (
              <div key={entry.id} style={{ position: 'relative', marginBottom: 20 }}>
                <div style={{
                  position: 'absolute',
                  left: -21,
                  top: 4,
                  width: 10,
                  height: 10,
                  borderRadius: '50%',
                  background: isAi ? '#8B5CF6' : 'var(--accent-primary)',
                  border: '2px solid var(--bg-surface)'
                }} />
                <div className="mono" style={{ fontSize: 11, color: 'var(--text-muted)', marginBottom: 2 }}>
                  {formatTime(entry.createdAt)}
                </div>
                <div style={{ fontSize: 12, fontWeight: 600, color: isAi ? '#8B5CF6' : 'var(--accent-primary)', marginBottom: 4 }}>
                  {isAi ? <Bot size={12} style={{ verticalAlign: 'middle', marginRight: 4 }} /> : <Cpu size={12} style={{ verticalAlign: 'middle', marginRight: 4 }} />}
                  {isAi ? 'AI Agent Resolution' : 'Deterministic Rule Match'} · Match Record #{entry.matchResultId}
                </div>
                <div style={{ background: 'var(--bg-input)', border: '1px solid var(--border-subtle)', borderRadius: 'var(--radius-sm)', padding: 12, fontSize: 13, color: 'var(--text-secondary)', lineHeight: 1.5 }}>
                  <div>{entry.reasoning || 'Decision recorded'}</div>
                  <div style={{ display: 'flex', gap: 16, marginTop: 6, fontSize: 11, color: 'var(--text-muted)' }}>
                    <span>Confidence: {Math.round((entry.confidence || 0.85) * 100)}%</span>
                    {entry.inputsConsidered && <span>Inputs: {entry.inputsConsidered}</span>}
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
}
