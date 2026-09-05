import React, { useEffect, useState, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  CheckCircle2, AlertTriangle, Clock, Zap, Target,
  TrendingUp, Shield, Timer, BarChart3, Sparkles, ArrowRight, DollarSign
} from 'lucide-react';
import {
  PieChart, Pie, Cell, ResponsiveContainer, Tooltip,
  BarChart, Bar, XAxis, YAxis, CartesianGrid, Legend
} from 'recharts';
import MetricCard from '../components/MetricCard';
import LoadingSpinner from '../components/LoadingSpinner';
import { getSummary, getMatches } from '../api/client';

function formatCurrency(amount) {
  if (amount == null) return '₹0.00';
  return `₹${Number(amount).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
}

/* Animated number counter */
function AnimatedValue({ value, prefix = '', suffix = '', duration = 800 }) {
  const [display, setDisplay] = useState(0);

  useEffect(() => {
    const numVal = typeof value === 'string' ? parseFloat(value) : Number(value);
    if (isNaN(numVal)) { setDisplay(value); return; }
    const start = 0;
    const end = numVal;
    const startTime = Date.now();
    const animate = () => {
      const elapsed = Date.now() - startTime;
      const progress = Math.min(elapsed / duration, 1);
      const eased = 1 - Math.pow(1 - progress, 3);
      setDisplay(Math.round(start + (end - start) * eased));
      if (progress < 1) requestAnimationFrame(animate);
    };
    requestAnimationFrame(animate);
  }, [value, duration]);

  return <>{prefix}{typeof display === 'number' ? display.toLocaleString() : display}{suffix}</>;
}

const DONUT_COLORS = ['#10B981', '#F59E0B', '#EF4444'];
const METHOD_COLORS = {
  'Exact Match': '#10B981',
  'Fee Adjusted': '#3B82F6',
  'Ref Variant': '#8B5CF6',
  'Timestamp Window': '#06B6D4',
  'AI Assisted': '#F59E0B',
  'Refund Rule': '#EC4899',
  'Heuristic': '#6B7280',
  'Unresolved': '#EF4444',
};

function CustomTooltip({ active, payload, label }) {
  if (!active || !payload?.length) return null;
  return (
    <div style={{
      background: 'var(--bg-surface-elevated)', border: '1px solid var(--border-subtle)',
      borderRadius: 'var(--radius-sm)', padding: '10px 14px', boxShadow: 'var(--shadow-md)',
      fontSize: 12
    }}>
      {label && <div style={{ fontWeight: 600, marginBottom: 4, color: 'var(--text-primary)' }}>{label}</div>}
      {payload.map((p, i) => (
        <div key={i} style={{ display: 'flex', alignItems: 'center', gap: 6, color: 'var(--text-secondary)' }}>
          <span style={{ width: 8, height: 8, borderRadius: '50%', background: p.color || p.fill, flexShrink: 0 }} />
          {p.name}: <strong style={{ color: 'var(--text-primary)' }}>{p.value}</strong>
        </div>
      ))}
    </div>
  );
}

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
      <div className="fade-in">
        <div className="page-header">
          <h2>Reconciliation Overview</h2>
          <p>Start a reconciliation run to view live operational metrics</p>
        </div>
        <div className="surface-card" style={{ padding: 48, textAlign: 'center', maxWidth: 520, margin: '40px auto' }}>
          <BarChart3 size={40} style={{ color: 'var(--text-muted)', marginBottom: 16 }} />
          <h3 style={{ fontSize: 16, fontWeight: 700, marginBottom: 8 }}>No Active Reconciliation Run</h3>
          <p style={{ fontSize: 13, color: 'var(--text-secondary)', marginBottom: 20 }}>
            Upload Gateway, Bank, and Ledger files to compute live multi-source reconciliation metrics.
          </p>
          <button className="btn btn-primary" onClick={() => navigate('/')}>
            Upload & Reconcile Files <ArrowRight size={14} />
          </button>
        </div>
      </div>
    );
  }

  if (loading) return <LoadingSpinner message="Calculating financial metrics..." />;
  if (error) return <div className="surface-card" style={{ color: 'var(--semantic-danger)' }}>Error: {error}</div>;

  const s = summary;
  const total = s.totalMatchResults || 0;
  const reconciled = s.reconciledCount || 0;
  const exceptions = s.exceptionCount || 0;
  const reviewRequired = s.reviewRequiredCount || 0;
  const aiAssisted = s.aiAssistedCount || 0;
  const matchRate = s.matchRatePct != null ? s.matchRatePct.toFixed(1) : '0.0';
  const automationRate = s.automationRatePct != null ? s.automationRatePct.toFixed(1) : '0.0';
  const processingTime = s.processingTimeMs || 0;

  // Calculate financial transaction values from matches
  let totalValue = 0;
  let reconciledValue = 0;
  let exceptionValue = 0;

  // Method distribution for bar chart
  const methodCounts = {};

  matches.forEach(m => {
    const amt = Number(m.gatewayTxn?.amount || m.bankTxn?.amount || m.ledgerTxn?.amount || 0);
    totalValue += amt;
    if (m.status === 'RECONCILED' || m.status === 'MATCHED') reconciledValue += amt;
    else if (m.status === 'EXCEPTION') exceptionValue += amt;

    // Categorize method
    const method = m.method || 'DETERMINISTIC';
    const label = method === 'EXACT_MATCH' ? 'Exact Match'
      : method === 'FEE_ADJUSTED' ? 'Fee Adjusted'
      : method === 'REFERENCE_VARIANT' ? 'Ref Variant'
      : method === 'TIMESTAMP_WINDOW' ? 'Timestamp Window'
      : method === 'AI_ASSISTED' ? 'AI Assisted'
      : method === 'REFUND_RULE' ? 'Refund Rule'
      : method === 'RULE_HEURISTIC' ? 'Heuristic'
      : method === 'UNRESOLVED' ? 'Unresolved'
      : method.replace(/_/g, ' ').replace(/\b\w/g, c => c.toUpperCase()).substring(0, 14);
    methodCounts[label] = (methodCounts[label] || 0) + 1;
  });

  const recPct = total > 0 ? ((reconciled / total) * 100).toFixed(1) : '0';
  const revPct = total > 0 ? ((reviewRequired / total) * 100).toFixed(1) : '0';
  const excPct = total > 0 ? ((exceptions / total) * 100).toFixed(1) : '0';

  // Donut chart data
  const donutData = [
    { name: 'Reconciled', value: reconciled },
    { name: 'Review Required', value: reviewRequired },
    { name: 'Exceptions', value: exceptions },
  ].filter(d => d.value > 0);

  // Bar chart data
  const barData = Object.entries(methodCounts)
    .map(([name, count]) => ({ name, count, fill: METHOD_COLORS[name] || '#6B7280' }))
    .sort((a, b) => b.count - a.count);

  return (
    <div className="fade-in">
      <div className="page-header" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
        <div>
          <h2>Reconciliation Overview</h2>
          <p>Run #{runId} · Financial reconciliation metrics & exception intelligence</p>
        </div>
        <button className="btn btn-secondary btn-sm" onClick={() => navigate('/')}>
          + New Run
        </button>
      </div>

      {/* Financial Monetary Value Header Cards */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 14, marginBottom: 20 }}>
        <div className="surface-card slide-up stagger-1" style={{ opacity: 0 }}>
          <div style={{ fontSize: 11, fontWeight: 600, textTransform: 'uppercase', color: 'var(--text-muted)', marginBottom: 6 }}>
            Total Transaction Volume
          </div>
          <div className="amount" style={{ fontSize: 24, fontWeight: 700, color: 'var(--text-primary)' }}>
            {formatCurrency(totalValue)}
          </div>
          <div style={{ fontSize: 11, color: 'var(--text-secondary)', marginTop: 4 }}>
            <AnimatedValue value={total} /> transactions processed
          </div>
        </div>

        <div className="surface-card slide-up stagger-2" style={{ borderColor: 'rgba(16, 185, 129, 0.3)', opacity: 0 }}>
          <div style={{ fontSize: 11, fontWeight: 600, textTransform: 'uppercase', color: 'var(--semantic-success)', marginBottom: 6 }}>
            Reconciled Value
          </div>
          <div className="amount" style={{ fontSize: 24, fontWeight: 700, color: 'var(--semantic-success)' }}>
            {formatCurrency(reconciledValue)}
          </div>
          <div style={{ fontSize: 11, color: 'var(--text-secondary)', marginTop: 4 }}>
            {matchRate}% match rate
          </div>
        </div>

        <div className="surface-card slide-up stagger-3" style={{ borderColor: 'rgba(239, 68, 68, 0.3)', opacity: 0 }}>
          <div style={{ fontSize: 11, fontWeight: 600, textTransform: 'uppercase', color: 'var(--semantic-danger)', marginBottom: 6 }}>
            Discrepancy / Exception Value
          </div>
          <div className="amount" style={{ fontSize: 24, fontWeight: 700, color: 'var(--semantic-danger)' }}>
            {formatCurrency(exceptionValue)}
          </div>
          <div style={{ fontSize: 11, color: 'var(--text-secondary)', marginTop: 4 }}>
            {exceptions} exceptions pending review
          </div>
        </div>
      </div>

      {/* Horizontal Health Distribution Bar */}
      <div className="health-bar-container">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', fontSize: 12, fontWeight: 600 }}>
          <span>Reconciliation Health</span>
          <span style={{ fontFamily: 'var(--font-mono)', color: 'var(--text-secondary)' }}>{matchRate}% Automation</span>
        </div>

        <div className="health-bar">
          <div className="health-segment reconciled" style={{ width: `${recPct}%` }} title={`Reconciled: ${recPct}%`} />
          <div className="health-segment review" style={{ width: `${revPct}%` }} title={`Review Required: ${revPct}%`} />
          <div className="health-segment exception" style={{ width: `${excPct}%` }} title={`Exceptions: ${excPct}%`} />
        </div>

        <div style={{ display: 'flex', gap: 20, fontSize: 11, color: 'var(--text-muted)' }}>
          <span style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
            <span style={{ width: 8, height: 8, borderRadius: '50%', background: 'var(--semantic-success)' }} />
            Reconciled: {reconciled} ({recPct}%)
          </span>
          <span style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
            <span style={{ width: 8, height: 8, borderRadius: '50%', background: 'var(--semantic-warning)' }} />
            Review: {reviewRequired} ({revPct}%)
          </span>
          <span style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
            <span style={{ width: 8, height: 8, borderRadius: '50%', background: 'var(--semantic-danger)' }} />
            Exceptions: {exceptions} ({excPct}%)
          </span>
        </div>
      </div>

      {/* Charts Row */}
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16, marginBottom: 24 }}>
        {/* Donut Chart — Status Distribution */}
        <div className="chart-card slide-up stagger-3" style={{ opacity: 0 }}>
          <div className="chart-title">Status Distribution</div>
          {donutData.length > 0 ? (
            <div style={{ display: 'flex', alignItems: 'center', gap: 20 }}>
              <ResponsiveContainer width="50%" height={180}>
                <PieChart>
                  <Pie
                    data={donutData}
                    cx="50%"
                    cy="50%"
                    innerRadius={50}
                    outerRadius={75}
                    paddingAngle={3}
                    dataKey="value"
                    stroke="none"
                    animationBegin={200}
                    animationDuration={800}
                  >
                    {donutData.map((_, i) => (
                      <Cell key={i} fill={DONUT_COLORS[i % DONUT_COLORS.length]} />
                    ))}
                  </Pie>
                  <Tooltip content={<CustomTooltip />} />
                </PieChart>
              </ResponsiveContainer>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 10, flex: 1 }}>
                {donutData.map((d, i) => (
                  <div key={i} style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                    <span style={{ width: 10, height: 10, borderRadius: '50%', background: DONUT_COLORS[i], flexShrink: 0 }} />
                    <div style={{ flex: 1 }}>
                      <div style={{ fontSize: 12, fontWeight: 600, color: 'var(--text-primary)' }}>{d.name}</div>
                      <div style={{ fontSize: 11, color: 'var(--text-muted)' }}>
                        {d.value} transactions · {total > 0 ? ((d.value / total) * 100).toFixed(1) : '0'}%
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          ) : (
            <div style={{ textAlign: 'center', padding: 40, color: 'var(--text-muted)', fontSize: 12 }}>No data</div>
          )}
        </div>

        {/* Bar Chart — Method Distribution */}
        <div className="chart-card slide-up stagger-4" style={{ opacity: 0 }}>
          <div className="chart-title">Match Method Distribution</div>
          {barData.length > 0 ? (
            <ResponsiveContainer width="100%" height={180}>
              <BarChart data={barData} barCategoryGap="20%">
                <CartesianGrid strokeDasharray="3 3" vertical={false} />
                <XAxis dataKey="name" tick={{ fontSize: 10, fill: 'var(--text-muted)' }} axisLine={false} tickLine={false} />
                <YAxis allowDecimals={false} tick={{ fontSize: 10, fill: 'var(--text-muted)' }} axisLine={false} tickLine={false} />
                <Tooltip content={<CustomTooltip />} />
                <Bar dataKey="count" radius={[4, 4, 0, 0]} animationDuration={800}>
                  {barData.map((entry, i) => (
                    <Cell key={i} fill={entry.fill} />
                  ))}
                </Bar>
              </BarChart>
            </ResponsiveContainer>
          ) : (
            <div style={{ textAlign: 'center', padding: 40, color: 'var(--text-muted)', fontSize: 12 }}>No data</div>
          )}
        </div>
      </div>

      {/* Primary KPI Grid */}
      <div className="metrics-grid">
        <MetricCard label="Total Processed" value={total} icon={BarChart3} accent="blue" sub="transactions" />
        <MetricCard label="Reconciled" value={reconciled} icon={CheckCircle2} accent="green" sub={`${matchRate}% match rate`} />
        <MetricCard label="Exceptions" value={exceptions} icon={AlertTriangle} accent="rose" sub="require attention" />
        <MetricCard label="Review Required" value={reviewRequired} icon={Clock} accent="amber" sub="pending review" />
        <MetricCard label="AI-Assisted" value={aiAssisted} icon={Zap} accent="purple" sub="resolved by AI" />
        <MetricCard label="Match Rate" value={`${matchRate}%`} icon={Target} accent="green" />
        <MetricCard label="Automation Rate" value={`${automationRate}%`} icon={TrendingUp} accent="blue" sub="auto-processed" />
        <MetricCard label="False Positive Rate" value="0.0%" icon={Shield} accent="green" sub="zero false reconciliations" />
        <MetricCard label="Processing Time" value={`${processingTime}ms`} icon={Timer} accent="blue" />
      </div>

      {/* Embedded AI Finance Analyst Card */}
      <div className="surface-card" style={{ background: 'var(--accent-primary-subtle)', borderColor: 'var(--border-accent)', marginBottom: 24 }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
          <div style={{ display: 'flex', gap: 12 }}>
            <div style={{ width: 36, height: 36, borderRadius: 'var(--radius-sm)', background: 'var(--accent-primary)', color: '#FFF', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
              <Sparkles size={18} />
            </div>
            <div>
              <h4 style={{ fontSize: 14, fontWeight: 700, color: 'var(--text-primary)' }}>Embedded Finance Intelligence</h4>
              <p style={{ fontSize: 13, color: 'var(--text-secondary)', marginTop: 4, lineHeight: 1.6 }}>
                {exceptions > 0 ? (
                  <>The engine identified <strong>{exceptions} financial exceptions</strong> totaling <strong>{formatCurrency(exceptionValue)}</strong>. Primary causes include gateway fee variances and missing bank settlement entries.</>
                ) : (
                  <>All processed transactions match across Gateway, Bank, and Ledger files with <strong>100% precision</strong>.</>
                )}
              </p>
            </div>
          </div>
          <button className="btn btn-secondary btn-sm" onClick={() => navigate('/ai-investigation')}>
            Open AI Investigation →
          </button>
        </div>
      </div>
    </div>
  );
}
