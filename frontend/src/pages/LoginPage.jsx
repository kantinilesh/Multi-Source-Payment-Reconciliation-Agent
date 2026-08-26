import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { ShieldCheck, Lock, Mail, User, Zap, ArrowRight, AlertCircle } from 'lucide-react';
import { useAuth } from '../context/AuthContext';

export default function LoginPage() {
  const navigate = useNavigate();
  const { login, signup, quickDemoLogin } = useAuth();
  const [isSignUp, setIsSignUp] = useState(false);
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [name, setName] = useState('');
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError(null);

    if (!email || !email.trim()) {
      setError('Please enter your work email address.');
      return;
    }
    if (!password || password.length < 6) {
      setError('Password must be at least 6 characters long.');
      return;
    }
    if (isSignUp && (!name || !name.trim())) {
      setError('Please enter your full name.');
      return;
    }

    setLoading(true);
    try {
      if (isSignUp) {
        await signup(email.trim(), password, name.trim());
      } else {
        await login(email.trim(), password);
      }
      navigate('/');
    } catch (err) {
      setError(err.message || 'Authentication failed. Please check your credentials.');
    } finally {
      setLoading(false);
    }
  };

  const handleQuickDemo = async (role) => {
    setError(null);
    setLoading(true);
    try {
      const demoEmails = {
        'Finance Controller': 'controller@razorpay.com',
        'Audit Analyst': 'audit@finops.org',
      };
      const demoEmail = demoEmails[role] || 'controller@razorpay.com';
      setEmail(demoEmail);
      setPassword('password123');
      await login(demoEmail, 'password123');
      navigate('/');
    } catch (err) {
      quickDemoLogin(role);
      navigate('/');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div style={{
      minHeight: '100vh',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      background: 'var(--bg-app)',
      padding: 24
    }}>
      <div className="surface-card" style={{ width: 440, maxWidth: '100%', padding: 36 }}>
        <div style={{ textAlign: 'center', marginBottom: 28 }}>
          <div style={{
            width: 48,
            height: 48,
            borderRadius: 'var(--radius-md)',
            background: 'var(--accent-primary-subtle)',
            color: 'var(--accent-primary)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            margin: '0 auto 16px',
            border: '1px solid var(--border-accent)'
          }}>
            <ShieldCheck size={26} />
          </div>
          <h2 style={{ fontSize: 20, fontWeight: 700, letterSpacing: '-0.02em', marginBottom: 4 }}>
            {isSignUp ? 'Create Controller Account' : 'Sign In to AI Finance Controller'}
          </h2>
          <p style={{ fontSize: 13, color: 'var(--text-secondary)' }}>
            Multi-source payment reconciliation & AI exception reasoning
          </p>
        </div>

        {error && (
          <div style={{
            display: 'flex',
            alignItems: 'center',
            gap: 8,
            padding: '10px 14px',
            background: 'var(--semantic-danger-subtle)',
            border: '1px solid rgba(239, 68, 68, 0.3)',
            borderRadius: 'var(--radius-sm)',
            color: 'var(--semantic-danger)',
            fontSize: 12,
            marginBottom: 20
          }}>
            <AlertCircle size={16} style={{ flexShrink: 0 }} />
            <span>{error}</span>
          </div>
        )}

        <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
          {isSignUp && (
            <div>
              <label style={{ fontSize: 11, fontWeight: 600, textTransform: 'uppercase', color: 'var(--text-muted)', letterSpacing: '0.04em', display: 'block', marginBottom: 4 }}>
                Full Name
              </label>
              <div style={{ position: 'relative' }}>
                <User size={16} style={{ position: 'absolute', left: 12, top: 11, color: 'var(--text-muted)' }} />
                <input
                  type="text"
                  placeholder="e.g. Nilesh Kanti"
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  style={{
                    width: '100%',
                    padding: '9px 12px 9px 36px',
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
            <label style={{ fontSize: 11, fontWeight: 600, textTransform: 'uppercase', color: 'var(--text-muted)', letterSpacing: '0.04em', display: 'block', marginBottom: 4 }}>
              Work Email
            </label>
            <div style={{ position: 'relative' }}>
              <Mail size={16} style={{ position: 'absolute', left: 12, top: 11, color: 'var(--text-muted)' }} />
              <input
                type="email"
                placeholder="controller@razorpay.com"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                style={{
                  width: '100%',
                  padding: '9px 12px 9px 36px',
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
            <label style={{ fontSize: 11, fontWeight: 600, textTransform: 'uppercase', color: 'var(--text-muted)', letterSpacing: '0.04em', display: 'block', marginBottom: 4 }}>
              Password
            </label>
            <div style={{ position: 'relative' }}>
              <Lock size={16} style={{ position: 'absolute', left: 12, top: 11, color: 'var(--text-muted)' }} />
              <input
                type="password"
                placeholder="••••••••••••"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                style={{
                  width: '100%',
                  padding: '9px 12px 9px 36px',
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

          <button type="submit" className="btn btn-primary" disabled={loading} style={{ width: '100%', marginTop: 6 }}>
            {loading ? 'Authenticating...' : isSignUp ? 'Sign Up' : 'Sign In'} <ArrowRight size={15} />
          </button>
        </form>

        <div style={{ margin: '20px 0 16px', textAlign: 'center', position: 'relative' }}>
          <hr style={{ borderColor: 'var(--border-subtle)' }} />
          <span style={{
            position: 'absolute',
            top: -9,
            left: '50%',
            transform: 'translateX(-50%)',
            background: 'var(--bg-surface)',
            padding: '0 10px',
            fontSize: 10,
            fontWeight: 600,
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
            <Zap size={12} style={{ color: 'var(--semantic-success)' }} /> Finance Controller
          </button>
          <button
            className="btn btn-secondary btn-sm"
            onClick={() => handleQuickDemo('Audit Analyst')}
          >
            <Zap size={12} style={{ color: 'var(--accent-primary)' }} /> Audit Analyst
          </button>
        </div>

        <div style={{ marginTop: 20, textAlign: 'center', fontSize: 12, color: 'var(--text-secondary)' }}>
          {isSignUp ? 'Already have an account? ' : "Don't have an account? "}
          <button
            style={{ background: 'none', border: 'none', color: 'var(--accent-primary)', fontWeight: 600, cursor: 'pointer', fontFamily: 'var(--font-sans)' }}
            onClick={() => { setIsSignUp(!isSignUp); setError(null); }}
          >
            {isSignUp ? 'Sign In' : 'Sign Up'}
          </button>
        </div>
      </div>
    </div>
  );
}
