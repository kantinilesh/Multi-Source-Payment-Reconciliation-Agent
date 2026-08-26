import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { ShieldCheck, Lock, Mail, User, Zap, ArrowRight } from 'lucide-react';
import { useAuth } from '../context/AuthContext';

export default function LoginPage() {
  const navigate = useNavigate();
  const { login, signup, quickDemoLogin } = useAuth();
  const [isSignUp, setIsSignUp] = useState(false);
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [name, setName] = useState('');

  const handleSubmit = (e) => {
    e.preventDefault();
    if (isSignUp) {
      signup(email || 'analyst@finops.org', password || 'password', name || 'Finance Analyst');
    } else {
      login(email || 'controller@razorpay.com', password || 'password');
    }
    navigate('/');
  };

  const handleQuickDemo = (role) => {
    quickDemoLogin(role);
    navigate('/');
  };

  return (
    <div style={{
      minHeight: '100vh',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      background: 'var(--bg-primary)',
      padding: 24
    }}>
      <div className="glass-card no-hover" style={{ width: 440, maxWidth: '100%', padding: 36 }}>
        <div style={{ textAlign: 'center', marginBottom: 28 }}>
          <div style={{
            width: 48,
            height: 48,
            borderRadius: 12,
            background: 'var(--accent-green-glow)',
            color: 'var(--accent-green)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            margin: '0 auto 16px',
            border: '1px solid var(--border-glow-green)'
          }}>
            <ShieldCheck size={26} />
          </div>
          <h2 style={{ fontSize: 22, fontWeight: 700, letterSpacing: '-0.02em', marginBottom: 6 }}>
            {isSignUp ? 'Create Controller Account' : 'Sign In to AI Finance Controller'}
          </h2>
          <p style={{ fontSize: 13, color: 'var(--text-secondary)' }}>
            Multi-source payment reconciliation & AI exception reasoning
          </p>
        </div>

        <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
          {isSignUp && (
            <div>
              <label style={{ fontSize: 11, textTransform: 'uppercase', color: 'var(--text-muted)', letterSpacing: '0.04em', display: 'block', marginBottom: 6 }}>
                Full Name
              </label>
              <div style={{ position: 'relative' }}>
                <User size={16} style={{ position: 'absolute', left: 12, top: 12, color: 'var(--text-muted)' }} />
                <input
                  type="text"
                  placeholder="e.g. Nilesh Kanti"
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  style={{
                    width: '100%',
                    padding: '10px 12px 10px 38px',
                    background: 'var(--bg-input)',
                    border: '1px solid var(--border-subtle)',
                    borderRadius: 'var(--radius-sm)',
                    color: 'var(--text-primary)',
                    fontFamily: 'var(--font-sans)',
                    fontSize: 13,
                    outline: 'none'
                  }}
                />
              </div>
            </div>
          )}

          <div>
            <label style={{ fontSize: 11, textTransform: 'uppercase', color: 'var(--text-muted)', letterSpacing: '0.04em', display: 'block', marginBottom: 6 }}>
              Work Email
            </label>
            <div style={{ position: 'relative' }}>
              <Mail size={16} style={{ position: 'absolute', left: 12, top: 12, color: 'var(--text-muted)' }} />
              <input
                type="email"
                placeholder="controller@razorpay.com"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                style={{
                  width: '100%',
                  padding: '10px 12px 10px 38px',
                  background: 'var(--bg-input)',
                  border: '1px solid var(--border-subtle)',
                  borderRadius: 'var(--radius-sm)',
                  color: 'var(--text-primary)',
                  fontFamily: 'var(--font-sans)',
                  fontSize: 13,
                  outline: 'none'
                }}
              />
            </div>
          </div>

          <div>
            <label style={{ fontSize: 11, textTransform: 'uppercase', color: 'var(--text-muted)', letterSpacing: '0.04em', display: 'block', marginBottom: 6 }}>
              Password
            </label>
            <div style={{ position: 'relative' }}>
              <Lock size={16} style={{ position: 'absolute', left: 12, top: 12, color: 'var(--text-muted)' }} />
              <input
                type="password"
                placeholder="••••••••••••"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                style={{
                  width: '100%',
                  padding: '10px 12px 10px 38px',
                  background: 'var(--bg-input)',
                  border: '1px solid var(--border-subtle)',
                  borderRadius: 'var(--radius-sm)',
                  color: 'var(--text-primary)',
                  fontFamily: 'var(--font-sans)',
                  fontSize: 13,
                  outline: 'none'
                }}
              />
            </div>
          </div>

          <button type="submit" className="btn btn-primary" style={{ width: '100%', marginTop: 8 }}>
            {isSignUp ? 'Sign Up' : 'Sign In'} <ArrowRight size={16} />
          </button>
        </form>

        <div style={{ margin: '24px 0 16px', textAlign: 'center', position: 'relative' }}>
          <hr style={{ borderColor: 'var(--border-subtle)' }} />
          <span style={{
            position: 'absolute',
            top: -10,
            left: '50%',
            transform: 'translateX(-50%)',
            background: 'var(--bg-card)',
            padding: '0 12px',
            fontSize: 11,
            color: 'var(--text-muted)',
            textTransform: 'uppercase'
          }}>
            Quick Hackathon Demo Logins
          </span>
        </div>

        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 8 }}>
          <button
            className="btn btn-secondary btn-sm"
            onClick={() => handleQuickDemo('Finance Controller')}
          >
            <Zap size={12} style={{ color: 'var(--accent-green)' }} /> Finance Controller
          </button>
          <button
            className="btn btn-secondary btn-sm"
            onClick={() => handleQuickDemo('Audit Analyst')}
          >
            <Zap size={12} style={{ color: 'var(--accent-blue)' }} /> Audit Analyst
          </button>
        </div>

        <div style={{ marginTop: 24, textAlign: 'center', fontSize: 13, color: 'var(--text-secondary)' }}>
          {isSignUp ? 'Already have an account? ' : "Don't have an account? "}
          <button
            style={{ background: 'none', border: 'none', color: 'var(--accent-green)', fontWeight: 600, cursor: 'pointer', fontFamily: 'var(--font-sans)' }}
            onClick={() => setIsSignUp(!isSignUp)}
          >
            {isSignUp ? 'Sign In' : 'Sign Up'}
          </button>
        </div>
      </div>
    </div>
  );
}
