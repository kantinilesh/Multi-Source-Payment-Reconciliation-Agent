import React from 'react';
import { NavLink, useNavigate } from 'react-router-dom';
import {
  Upload, LayoutDashboard, ArrowLeftRight, Brain,
  AlertTriangle, ClipboardList, Database
} from 'lucide-react';

const navItems = [
  { to: '/',              icon: Upload,          label: 'Upload & Run' },
  { to: '/dashboard',     icon: LayoutDashboard, label: 'Dashboard' },
  { to: '/transactions',  icon: ArrowLeftRight,  label: 'Transactions' },
  { to: '/ai-investigation', icon: Brain,        label: 'AI Investigation' },
  { to: '/exceptions',    icon: AlertTriangle,   label: 'Exception Queue' },
  { to: '/audit',         icon: ClipboardList,   label: 'Audit Trail' },
];

export default function Layout({ children, runId, runs, onRunChange }) {
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
