import React, { useState } from 'react';
import { ShieldAlert, Users, Sliders, CheckCircle2, XCircle, Key, Server } from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import MetricCard from '../components/MetricCard';

export default function AdminPage() {
  const { user } = useAuth();
  const [users, setUsers] = useState([
    { id: 1, name: 'Nilesh Kanti', email: 'controller@razorpay.com', role: 'Finance Controller', status: 'ACTIVE', authorizedAt: '2026-08-20' },
    { id: 2, name: 'Sarah Jenkins', email: 'audit@finops.org', role: 'Audit Analyst', status: 'ACTIVE', authorizedAt: '2026-08-22' },
    { id: 3, name: 'Alex Rivera', email: 'store.manager@retail.com', role: 'Store Manager', status: 'ACTIVE', authorizedAt: '2026-08-24' },
    { id: 4, name: 'David Kim', email: 'david.k@finance.org', role: 'Junior Analyst', status: 'PENDING', authorizedAt: '—' },
  ]);

  const [feeMin, setFeeMin] = useState(0.5);
  const [feeMax, setFeeMax] = useState(4.0);
  const [lagDays, setLagDays] = useState(5);
  const [aiGate, setAiGate] = useState(85);
  const [saveSuccess, setSaveSuccess] = useState(false);

  const toggleUserStatus = (id) => {
    setUsers(users.map(u => u.id === id ? { ...u, status: u.status === 'ACTIVE' ? 'REVOKED' : 'ACTIVE' } : u));
  };

  const handleSaveConfig = () => {
    setSaveSuccess(true);
    setTimeout(() => setSaveSuccess(false), 3000);
  };

  return (
    <div>
      <div className="page-header" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
        <div>
          <h2>Admin Control Panel & System Governance</h2>
          <p>Manage user authorizations, access controls, and live matching engine parameters</p>
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: 8, background: 'var(--accent-blue-glow)', color: 'var(--accent-blue)', padding: '6px 12px', borderRadius: 'var(--radius-sm)', fontSize: 12, border: '1px solid var(--border-subtle)' }}>
          <ShieldAlert size={14} /> Admin Privileges Enabled
        </div>
      </div>

      <div className="metrics-grid">
        <MetricCard label="Authorized Users" value={users.filter(u => u.status === 'ACTIVE').length} icon={Users} accent="blue" sub="active accounts" />
        <MetricCard label="Fee Tolerance Band" value={`${feeMin}% - ${feeMax}%`} icon={Sliders} accent="green" sub="gateway fee range" />
        <MetricCard label="Settlement Window" value={`${lagDays} Days`} icon={Server} accent="amber" sub="max settlement lag" />
        <MetricCard label="AI Confidence Gate" value={`${aiGate}%`} icon={Key} accent="purple" sub="auto-accept threshold" />
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 20, marginBottom: 24 }}>
        {/* User Management & Authorizations */}
        <div className="glass-card no-hover">
          <div className="section-title">User Accounts & Authorizations</div>
          <div className="data-table-wrapper" style={{ marginTop: 12 }}>
            <table className="data-table">
              <thead>
                <tr>
                  <th>User</th>
                  <th>Role</th>
                  <th>Status</th>
                  <th>Action</th>
                </tr>
              </thead>
              <tbody>
                {users.map((u) => (
                  <tr key={u.id}>
                    <td>
                      <div style={{ fontWeight: 600, color: 'var(--text-primary)' }}>{u.name}</div>
                      <div style={{ fontSize: 11, color: 'var(--text-muted)' }}>{u.email}</div>
                    </td>
                    <td style={{ fontSize: 12 }}>{u.role}</td>
                    <td>
                      <span className={`status-badge ${u.status === 'ACTIVE' ? 'reconciled' : 'exception'}`}>
                        {u.status === 'ACTIVE' ? <CheckCircle2 size={10} /> : <XCircle size={10} />}
                        {u.status}
                      </span>
                    </td>
                    <td>
                      <button
                        className="btn btn-secondary btn-sm"
                        style={{ fontSize: 11, padding: '4px 8px' }}
                        onClick={() => toggleUserStatus(u.id)}
                      >
                        {u.status === 'ACTIVE' ? 'Revoke' : 'Authorize'}
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>

        {/* Live Engine Tuning Parameters */}
        <div className="glass-card no-hover">
          <div className="section-title">Live Engine Thresholds & Weights</div>
          
          <div style={{ display: 'flex', flexDirection: 'column', gap: 16, marginTop: 16 }}>
            <div>
              <label style={{ fontSize: 12, color: 'var(--text-secondary)', display: 'block', marginBottom: 6 }}>
                Min / Max Fee Tolerance Band (%)
              </label>
              <div style={{ display: 'flex', gap: 12 }}>
                <input
                  type="number"
                  step="0.1"
                  value={feeMin}
                  onChange={(e) => setFeeMin(Number(e.target.value))}
                  style={{ width: '50%', padding: '8px 12px', background: 'var(--bg-input)', border: '1px solid var(--border-subtle)', borderRadius: 'var(--radius-sm)', color: 'var(--text-primary)', fontFamily: 'var(--font-mono)' }}
                />
                <input
                  type="number"
                  step="0.1"
                  value={feeMax}
                  onChange={(e) => setFeeMax(Number(e.target.value))}
                  style={{ width: '50%', padding: '8px 12px', background: 'var(--bg-input)', border: '1px solid var(--border-subtle)', borderRadius: 'var(--radius-sm)', color: 'var(--text-primary)', fontFamily: 'var(--font-mono)' }}
                />
              </div>
            </div>

            <div>
              <label style={{ fontSize: 12, color: 'var(--text-secondary)', display: 'block', marginBottom: 6 }}>
                Max Settlement Lag Window (Days)
              </label>
              <input
                type="number"
                value={lagDays}
                onChange={(e) => setLagDays(Number(e.target.value))}
                style={{ width: '100%', padding: '8px 12px', background: 'var(--bg-input)', border: '1px solid var(--border-subtle)', borderRadius: 'var(--radius-sm)', color: 'var(--text-primary)', fontFamily: 'var(--font-mono)' }}
              />
            </div>

            <div>
              <label style={{ fontSize: 12, color: 'var(--text-secondary)', display: 'block', marginBottom: 6 }}>
                AI Auto-Accept Minimum Confidence Gate (%)
              </label>
              <input
                type="number"
                value={aiGate}
                onChange={(e) => setAiGate(Number(e.target.value))}
                style={{ width: '100%', padding: '8px 12px', background: 'var(--bg-input)', border: '1px solid var(--border-subtle)', borderRadius: 'var(--radius-sm)', color: 'var(--text-primary)', fontFamily: 'var(--font-mono)' }}
              />
            </div>

            {saveSuccess && (
              <div style={{ color: 'var(--accent-green)', fontSize: 12, fontWeight: 600 }}>
                ✓ Live Engine Configuration Saved & Applied!
              </div>
            )}

            <button className="btn btn-primary" style={{ marginTop: 8 }} onClick={handleSaveConfig}>
              Save Live Parameters
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
