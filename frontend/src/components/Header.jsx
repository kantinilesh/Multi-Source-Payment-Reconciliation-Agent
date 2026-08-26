import React from 'react';
import { useLocation } from 'react-router-dom';
import { Sun, Moon, Monitor, Command, Database, UserCheck, ChevronRight } from 'lucide-react';
import { useTheme } from '../context/ThemeContext';
import { useAuth } from '../context/AuthContext';

const routeNames = {
  '/': 'Upload & Reconcile',
  '/dashboard': 'Reconciliation Overview',
  '/transactions': 'Transaction Explorer',
  '/ai-investigation': 'AI Investigation View',
  '/exceptions': 'Exception Queue Workbench',
  '/audit': 'Immutable Audit Trail',
  '/admin': 'Admin Governance & Parameters',
};

export default function Header({ runId, runs, onRunChange, onOpenCommand }) {
  const location = useLocation();
  const { theme, setTheme } = useTheme();
  const { user } = useAuth();

  const pageTitle = routeNames[location.pathname] || 'Dashboard';

  const toggleTheme = () => {
    if (theme === 'dark') setTheme('light');
    else if (theme === 'light') setTheme('system');
    else setTheme('dark');
  };

  return (
    <header className="top-header">
      <div className="header-title">
        <span style={{ color: 'var(--text-muted)' }}>AI Finance Controller</span>
        <ChevronRight size={14} style={{ color: 'var(--text-muted)' }} />
        <span style={{ color: 'var(--text-primary)', fontWeight: 600 }}>{pageTitle}</span>
      </div>

      <div className="header-actions">
        {/* Run Selector */}
        <div style={{ display: 'flex', alignItems: 'center', gap: 6, background: 'var(--bg-input)', border: '1px solid var(--border-subtle)', borderRadius: 'var(--radius-sm)', padding: '4px 8px', fontSize: 12 }}>
          <Database size={13} style={{ color: 'var(--text-muted)' }} />
          <select
            value={runId || ''}
            onChange={(e) => onRunChange?.(Number(e.target.value))}
            style={{ background: 'transparent', border: 'none', color: 'var(--text-primary)', fontFamily: 'var(--font-mono)', fontSize: 12, outline: 'none', cursor: 'pointer' }}
          >
            {!runId && <option value="">No active run</option>}
            {(runs || []).map((r) => (
              <option key={r.id} value={r.id}>
                Run #{r.id}
              </option>
            ))}
          </select>
        </div>

        {/* Command Palette Trigger (⌘K) */}
        <button className="command-trigger-btn" onClick={onOpenCommand} title="Open Command Menu">
          <Command size={13} />
          <span>Search...</span>
          <kbd>⌘K</kbd>
        </button>

        {/* Theme Toggle Button */}
        <button
          className="btn btn-secondary btn-sm"
          onClick={toggleTheme}
          title={`Theme: ${theme}. Click to toggle.`}
          style={{ padding: '6px 8px' }}
        >
          {theme === 'dark' ? <Moon size={14} /> : theme === 'light' ? <Sun size={14} /> : <Monitor size={14} />}
        </button>

        {/* User Avatar */}
        {user && (
          <div style={{ display: 'flex', alignItems: 'center', gap: 6, background: 'var(--bg-surface-hover)', padding: '4px 8px', borderRadius: 'var(--radius-sm)', border: '1px solid var(--border-subtle)' }}>
            <UserCheck size={14} style={{ color: 'var(--accent-primary)' }} />
            <span style={{ fontSize: 12, fontWeight: 600, color: 'var(--text-primary)' }}>{user.name}</span>
          </div>
        )}
      </div>
    </header>
  );
}
