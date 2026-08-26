import React from 'react';
import { NavLink, useNavigate } from 'react-router-dom';
import {
  Upload, LayoutDashboard, ArrowLeftRight, Brain,
  AlertTriangle, ClipboardList, Database, LogOut, UserCheck
} from 'lucide-react';
import { useAuth } from '../context/AuthContext';

const navItems = [
  { to: '/',              icon: Upload,          label: 'Upload & Run' },
  { to: '/dashboard',     icon: LayoutDashboard, label: 'Dashboard' },
  { to: '/transactions',  icon: ArrowLeftRight,  label: 'Transactions' },
  { to: '/ai-investigation', icon: Brain,        label: 'AI Investigation' },
  { to: '/exceptions',    icon: AlertTriangle,   label: 'Exception Queue' },
  { to: '/audit',         icon: ClipboardList,   label: 'Audit Trail' },
];

export default function Layout({ children, runId, runs, onRunChange }) {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  return (
    <div className="app-layout">
      <aside className="sidebar">
        <div className="sidebar-brand">
          <h1>AI Finance Controller</h1>
          <div className="brand-sub">Payment Reconciliation</div>
        </div>

        <nav className="sidebar-nav">
          {navItems.map(({ to, icon: Icon, label }) => (
            <NavLink
              key={to}
              to={to}
              className={({ isActive }) => `nav-link${isActive ? ' active' : ''}`}
              end={to === '/'}
            >
              <Icon className="nav-icon" />
              {label}
            </NavLink>
          ))}
        </nav>

        <div className="sidebar-footer">
          {user && (
            <div style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              padding: '8px 12px',
              background: 'var(--bg-card)',
              border: '1px solid var(--border-card)',
              borderRadius: 'var(--radius-sm)',
              marginBottom: 8
            }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 8, overflow: 'hidden' }}>
                <UserCheck size={16} style={{ color: 'var(--accent-green)', flexShrink: 0 }} />
                <div style={{ overflow: 'hidden' }}>
                  <div style={{ fontSize: 12, fontWeight: 600, color: 'var(--text-primary)', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                    {user.name}
                  </div>
                  <div style={{ fontSize: 10, color: 'var(--text-muted)' }}>
                    {user.role}
                  </div>
                </div>
              </div>
              <button
                onClick={handleLogout}
                title="Sign Out"
                style={{ background: 'none', border: 'none', color: 'var(--text-muted)', cursor: 'pointer', padding: 4 }}
              >
                <LogOut size={14} />
              </button>
            </div>
          )}

          <div className="run-selector">
            <Database size={14} />
            <select
              value={runId || ''}
              onChange={(e) => onRunChange?.(Number(e.target.value))}
            >
              {!runId && <option value="">No runs yet</option>}
              {(runs || []).map((r) => (
                <option key={r.id} value={r.id}>
                  Run #{r.id}
                </option>
              ))}
            </select>
          </div>
        </div>
      </aside>

      <main className="main-content">
        {children}
      </main>
    </div>
  );
}
