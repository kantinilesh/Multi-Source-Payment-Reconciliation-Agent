import React from 'react';

export default function ConfidenceGauge({ value }) {
  const pct = Math.round((value || 0) * 100);
  const level = pct >= 85 ? 'high' : pct >= 50 ? 'medium' : 'low';
  const color = level === 'high' ? 'var(--accent-green)' : level === 'medium' ? 'var(--accent-amber)' : 'var(--accent-rose)';

  return (
    <div className="confidence-gauge">
      <div className="gauge-bar">
        <div className={`gauge-fill ${level}`} style={{ width: `${pct}%` }} />
      </div>
      <span className="gauge-value" style={{ color }}>{pct}%</span>
    </div>
  );
}
