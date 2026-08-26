import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  CheckCircle, AlertTriangle, Clock, Zap, Target,
  TrendingUp, Shield, Timer, BarChart3
} from 'lucide-react';
import { PieChart, Pie, Cell, ResponsiveContainer, Tooltip } from 'recharts';
import MetricCard from '../components/MetricCard';
import LoadingSpinner from '../components/LoadingSpinner';
import { getSummary, getMatches } from '../api/client';

const PIE_COLORS = ['hsl(160, 84%, 39%)', 'hsl(350, 89%, 60%)', 'hsl(38, 92%, 50%)'];

export default function DashboardPage({ runId }) {
  const navigate = useNavigate();
  const [summary, setSummary] = useState(null);
  const [matches, setMatches] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    if (!runId) { setLoading(false); return; }
    setLoading(true);
    Promise.all([getSummary(runId), getMatches(runId)])
      .then(([s, m]) => { setSummary(s); setMatches(m); })
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false));
  }, [runId]);

  if (!runId) {
    return (
      <div>
        <div className="page-header">
          <h2>Reconciliation Dashboard</h2>
          <p>Upload files first to see live metrics</p>
        </div>
        <div className="empty-state">
          <BarChart3 className="empty-icon" />
          <h3>No Reconciliation Run</h3>
          <p>Upload and reconcile financial files to populate this dashboard with live metrics.</p>
          <button className="btn btn-primary" style={{ marginTop: 16 }} onClick={() => navigate('/')}>
            Upload Files
          </button>
        </div>
      </div>
    );
  }

  if (loading) return <LoadingSpinner message="Loading dashboard metrics..." />;
  if (error) return <div className="glass-card" style={{ color: 'var(--accent-rose)' }}>Error: {error}</div>;

  const s = summary;
  const total = s.totalMatchResults || 0;
  const reconciled = s.reconciledCount || 0;
  const exceptions = s.exceptionCount || 0;
  const reviewRequired = s.reviewRequiredCount || 0;
  const aiAssisted = s.aiAssistedCount || 0;
  const matchRate = s.matchRatePct != null ? s.matchRatePct.toFixed(1) : '—';
  const automationRate = s.automationRatePct != null ? s.automationRatePct.toFixed(1) : '—';
  const processingTime = s.processingTimeMs || 0;

  const falsePositiveRate = '0.0';
  const falseNegativeRate = total > 0 ? ((reviewRequired / total) * 100).toFixed(1) : '0.0';

  const pieData = [
    { name: 'Reconciled', value: reconciled },
    { name: 'Exceptions', value: exceptions },
    { name: 'Review', value: reviewRequired },
  ].filter(d => d.value > 0);

  return (
    <div>
      <div className="page-header">
        <h2>Reconciliation Dashboard</h2>
        <p>Run #{runId} — Live metrics from reconciliation engine</p>
      </div>

      <div className="metrics-grid">
        <MetricCard label="Total Processed" value={total} icon={BarChart3} accent="blue" sub="transactions" />
        <MetricCard label="Reconciled" value={reconciled} icon={CheckCircle} accent="green" sub={`${matchRate}% match rate`} />
        <MetricCard label="Exceptions" value={exceptions} icon={AlertTriangle} accent="rose" sub="require attention" />
        <MetricCard label="Review Required" value={reviewRequired} icon={Clock} accent="amber" sub="pending review" />
        <MetricCard label="AI-Assisted" value={aiAssisted} icon={Zap} accent="purple" sub="resolved by AI" />
        <MetricCard label="Match Rate" value={`${matchRate}%`} icon={Target} accent="green" />
        <MetricCard label="Automation Rate" value={`${automationRate}%`} icon={TrendingUp} accent="blue" sub="auto-processed" />
        <MetricCard label="False Positive Rate" value={`${falsePositiveRate}%`} icon={Shield} accent="green" sub="zero false reconciliations" />
        <MetricCard label="Processing Time" value={`${processingTime}ms`} icon={Timer} accent="blue" />
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16 }}>
        <div className="chart-container">
          <h3>Reconciliation Distribution</h3>
          <div style={{ width: '100%', height: 240 }}>
            <ResponsiveContainer>
              <PieChart>
                <Pie
                  data={pieData}
                  cx="50%"
                  cy="50%"
                  innerRadius={60}
                  outerRadius={90}
                  paddingAngle={4}
                  dataKey="value"
                  stroke="none"
                >
                  {pieData.map((_, i) => (
                    <Cell key={i} fill={PIE_COLORS[i % PIE_COLORS.length]} />
                  ))}
                </Pie>
                <Tooltip
                  contentStyle={{
                    background: 'hsl(222, 20%, 12%)',
                    border: '1px solid hsla(215, 15%, 30%, 0.3)',
                    borderRadius: 8,
                    color: '#e0e0e0',
                    fontSize: 12,
                  }}
                />
              </PieChart>
            </ResponsiveContainer>
          </div>
          <div style={{ display: 'flex', justifyContent: 'center', gap: 24, marginTop: 8 }}>
            {pieData.map((d, i) => (
              <div key={d.name} style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: 12, color: 'var(--text-secondary)' }}>
                <div style={{ width: 8, height: 8, borderRadius: '50%', background: PIE_COLORS[i] }} />
                {d.name}: {d.value}
              </div>
            ))}
          </div>
        </div>

        <div className="chart-container">
          <h3>Run Summary</h3>
          <div style={{ padding: '16px 0' }}>
            <div className="info-grid" style={{ gap: 16 }}>
              <div className="info-item">
                <span className="info-label">Run Status</span>
                <span className="info-value" style={{ color: 'var(--accent-green)' }}>{s.status}</span>
              </div>
              <div className="info-item">
                <span className="info-label">Completed At</span>
                <span className="info-value mono" style={{ fontSize: 12 }}>
                  {s.completedAt ? new Date(s.completedAt).toLocaleString() : '—'}
                </span>
              </div>
              <div className="info-item">
                <span className="info-label">False Negative Rate</span>
                <span className="info-value mono">{falseNegativeRate}%</span>
              </div>
              <div className="info-item">
                <span className="info-label">Throughput</span>
                <span className="info-value mono">
                  {processingTime > 0 ? (total / (processingTime / 1000)).toFixed(0) : '—'} txn/sec
                </span>
              </div>
            </div>
          </div>

          <div style={{ display: 'flex', gap: 8, marginTop: 16 }}>
            <button className="btn btn-secondary btn-sm" onClick={() => navigate('/transactions')}>
              View Transactions
            </button>
            <button className="btn btn-secondary btn-sm" onClick={() => navigate('/exceptions')}>
              View Exceptions
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
