import React, { useEffect, useState } from 'react';
import { ClipboardList, Bot, Cpu } from 'lucide-react';
import ConfidenceGauge from '../components/ConfidenceGauge';
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
    <div>
      <div className="page-header"><h2>Audit Trail</h2></div>
      <div className="empty-state">
        <ClipboardList className="empty-icon" />
        <h3>No data available</h3>
        <p>Upload and reconcile files to see the audit trail.</p>
      </div>
    </div>
  );

  if (loading) return <LoadingSpinner message="Loading audit trail..." />;
  if (error) return <div className="glass-card" style={{ color: 'var(--accent-rose)' }}>Error: {error}</div>;

  return (
    <div>
      <div className="page-header">
        <h2>Audit Trail</h2>
        <p>{entries.length} audit entries — immutable decision record</p>
      </div>

      {entries.length === 0 ? (
        <div className="empty-state">
          <ClipboardList className="empty-icon" />
          <h3>No audit entries</h3>
          <p>Audit entries are created when the reconciliation engine makes decisions.</p>
        </div>
      ) : (
        <div className="glass-card no-hover">
          <div className="audit-timeline">
            {entries.map((entry) => {
              const isAi = entry.method === 'AI_ASSISTED';
              return (
                <div key={entry.id} className={`audit-entry ${isAi ? 'ai' : 'rule'}`}>
                  <div className="audit-time">{formatTime(entry.createdAt)}</div>
                  <div className="audit-method">
                    {isAi ? (
                      <><Bot size={12} style={{ verticalAlign: 'middle', marginRight: 4 }} /> AI-Assisted</>
                    ) : (
                      <><Cpu size={12} style={{ verticalAlign: 'middle', marginRight: 4 }} /> Deterministic Rule</>
                    )}
                    {' · '}
                    Match #{entry.matchResultId}
                  </div>
                  <div className="audit-body">
                    <div style={{ marginBottom: 8 }}>
                      {entry.reasoning || 'Decision recorded'}
                    </div>
                    <div style={{ display: 'flex', gap: 16, alignItems: 'center', fontSize: 12, color: 'var(--text-muted)' }}>
                      {entry.confidence != null && (
                        <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                          Confidence: <ConfidenceGauge value={entry.confidence} />
                        </div>
                      )}
                      {entry.inputsConsidered && (
                        <div style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', maxWidth: 300 }}>
                          Inputs: {entry.inputsConsidered}
                        </div>
                      )}
                    </div>
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      )}
    </div>
  );
}
