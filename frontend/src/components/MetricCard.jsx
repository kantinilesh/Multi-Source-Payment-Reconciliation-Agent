import React from 'react';

export default function MetricCard({ label, value, sub, icon: Icon, accent = 'blue' }) {
  const accentColors = {
    green:  { color: 'var(--semantic-success)', bg: 'var(--semantic-success-subtle)' },
    amber:  { color: 'var(--semantic-warning)', bg: 'var(--semantic-warning-subtle)' },
    rose:   { color: 'var(--semantic-danger)',  bg: 'var(--semantic-danger-subtle)' },
    blue:   { color: 'var(--accent-primary)',  bg: 'var(--accent-primary-subtle)' },
    purple: { color: '#8B5CF6',                bg: 'rgba(139, 92, 246, 0.1)' },
  };

  const a = accentColors[accent] || accentColors.blue;

  return (
    <div className="metric-card">
      <div className="metric-header">
        <span className="metric-label">{label}</span>
        {Icon && (
          <div style={{ width: 26, height: 26, borderRadius: 'var(--radius-xs)', background: a.bg, color: a.color, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
            <Icon size={14} />
          </div>
        )}
      </div>
      <div className="metric-value">{value}</div>
      {sub && <div className="metric-sub">{sub}</div>}
    </div>
  );
}
