import React from 'react';
import { CheckCircle2, AlertTriangle, Clock, HelpCircle } from 'lucide-react';

const STATUS_CONFIG = {
  RECONCILED:        { className: 'reconciled', icon: CheckCircle2, label: 'Reconciled' },
  EXCEPTION:         { className: 'exception',  icon: AlertTriangle, label: 'Exception' },
  REVIEW_REQUIRED:   { className: 'review',     icon: Clock, label: 'Review Required' },
  MATCHED:           { className: 'reconciled', icon: CheckCircle2, label: 'Matched' },
  PARTIALLY_MATCHED: { className: 'review',     icon: Clock, label: 'Partial Match' },
};

export default function StatusBadge({ status }) {
  const config = STATUS_CONFIG[status] || { className: 'review', icon: HelpCircle, label: status || 'Unknown' };
  const Icon = config.icon;

  return (
    <span className={`status-badge ${config.className}`}>
      <Icon size={11} />
      {config.label}
    </span>
  );
}
