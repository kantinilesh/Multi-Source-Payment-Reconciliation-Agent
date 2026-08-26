import React from 'react';

export default function MetricCard({ label, value, sub, icon: Icon, accent = 'blue' }) {
  const accentMap = {
    green:  { color: 'var(--accent-green)', glow: 'var(--accent-green-glow)' },
    amber:  { color: 'var(--accent-amber)', glow: 'var(--accent-amber-glow)' },
    rose:   { color: 'var(--accent-rose)',   glow: 'var(--accent-rose-glow)' },
    blue:   { color: 'var(--accent-blue)',   glow: 'var(--accent-blue-glow)' },
    purple: { color: 'var(--accent-purple)', glow: 'var(--accent-purple-glow)' },
  };

  const a = accentMap[accent] || accentMap.blue;

  return (
    <div
      className="metric-card animate-in"
      style={{ '--metric-accent': a.color, '--metric-accent-glow': a.glow }}
    >
      <div className="metric-label">{label}</div>
      <div className="metric-value">{value}</div>
      {sub && <div className="metric-sub">{sub}</div>}
      {Icon && (
        <div className="metric-icon">
          <Icon size={18} />
        </div>
      )}
    </div>
  );
}
