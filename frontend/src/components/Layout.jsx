import React, { useState, useEffect } from 'react';
import { NavLink, useNavigate } from 'react-router-dom';
import {
  Upload, LayoutDashboard, ArrowLeftRight, Brain,
  AlertTriangle, ClipboardList, ShieldAlert, LogOut, UserCheck, ShieldCheck
} from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import Header from './Header';
import CommandPalette from './CommandPalette';

const navItems = [
  { to: '/',              icon: Upload,          label: 'Upload & Reconcile' },
  { to: '/dashboard',     icon: LayoutDashboard, label: 'Overview' },
  { to: '/transactions',  icon: ArrowLeftRight,  label: 'Transactions' },
  { to: '/ai-investigation', icon: Brain,        label: 'AI Investigation' },
  { to: '/exceptions',    icon: AlertTriangle,   label: 'Exception Queue' },
  { to: '/audit',         icon: ClipboardList,   label: 'Audit Trail' },
  { to: '/admin',         icon: ShieldAlert,     label: 'Admin Governance' },
];

export default function Layout({ children, runId, runs, onRunChange }) {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const [commandOpen, setCommandOpen] = useState(false);

  useEffect(() => {
    const handleKeyDown = (e) => {
      if ((e.metaKey || e.ctrlKey) && e.key === 'k') {
        e.preventDefault();
        setCommandOpen(prev => !prev);
      }
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, []);

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  return (
    <div className="app-layout">
      {/* Sidebar */}
      <aside className="sidebar">
        <div className="sidebar-brand">
          <div className="sidebar-brand-icon">
            <ShieldCheck size={16} />
          </div>
          <div>
            <h1>Finance Controller</h1>
            <div className="brand-sub">Reconciliation Agent</div>
          </div>
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
              padding: '8px 10px',
              background: 'var(--bg-surface-hover)',
              border: '1px solid var(--border-subtle)',
              borderRadius: 'var(--radius-sm)'
            }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 8, overflow: 'hidden' }}>
                <UserCheck size={14} style={{ color: 'var(--accent-primary)', flexShrink: 0 }} />
                <div style={{ overflow: 'hidden' }}>
                  <div style={{ fontSize: 11, fontWeight: 600, color: 'var(--text-primary)', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                    {user.name}
                  </div>
                  <div style={{ fontSize: 9, color: 'var(--text-muted)' }}>
                    {user.role}
                  </div>
                </div>
              </div>
              <button
                onClick={handleLogout}
                title="Sign Out"
                style={{ background: 'none', border: 'none', color: 'var(--text-muted)', cursor: 'pointer', padding: 4 }}
              >
                <LogOut size={12} />
              </button>
            </div>
          )}
        </div>
      </aside>

      {/* Main Wrapper */}
      <div className="main-wrapper">
        <Header
          runId={runId}
          runs={runs}
          onRunChange={onRunChange}
          onOpenCommand={() => setCommandOpen(true)}
        />
        <main className="main-content fade-in">
          {children}
        </main>
      </div>

      {/* Command Palette Modal */}
      <CommandPalette isOpen={commandOpen} onClose={() => setCommandOpen(false)} />
    </div>
  );
}
