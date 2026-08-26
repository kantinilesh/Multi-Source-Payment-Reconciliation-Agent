import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Search, Upload, LayoutDashboard, ArrowLeftRight, Brain,
  AlertTriangle, ClipboardList, ShieldAlert, Sun, Moon, Sparkles, X
} from 'lucide-react';
import { useTheme } from '../context/ThemeContext';

export default function CommandPalette({ isOpen, onClose }) {
  const navigate = useNavigate();
  const { theme, setTheme } = useTheme();
  const [query, setQuery] = useState('');
  const [selectedIndex, setSelectedIndex] = useState(0);

  const commands = [
    { id: 'nav-dashboard', label: 'Go to Dashboard', icon: LayoutDashboard, category: 'Navigation', action: () => navigate('/dashboard') },
    { id: 'nav-upload', label: 'Go to Upload & Reconcile', icon: Upload, category: 'Navigation', action: () => navigate('/') },
    { id: 'nav-transactions', label: 'Explore Transactions', icon: ArrowLeftRight, category: 'Navigation', action: () => navigate('/transactions') },
    { id: 'nav-ai', label: 'Open AI Investigation', icon: Brain, category: 'Navigation', action: () => navigate('/ai-investigation') },
    { id: 'nav-exceptions', label: 'View Exception Queue', icon: AlertTriangle, category: 'Navigation', action: () => navigate('/exceptions') },
    { id: 'nav-audit', label: 'View Audit Trail', icon: ClipboardList, category: 'Navigation', action: () => navigate('/audit') },
    { id: 'nav-admin', label: 'Admin Governance & Parameters', icon: ShieldAlert, category: 'Navigation', action: () => navigate('/admin') },
    { id: 'theme-light', label: 'Switch to Light Theme', icon: Sun, category: 'Theme', action: () => setTheme('light') },
    { id: 'theme-dark', label: 'Switch to Dark Theme', icon: Moon, category: 'Theme', action: () => setTheme('dark') },
  ];

  const filtered = commands.filter(c =>
    c.label.toLowerCase().includes(query.toLowerCase()) ||
    c.category.toLowerCase().includes(query.toLowerCase())
  );

  useEffect(() => {
    setSelectedIndex(0);
  }, [query]);

  useEffect(() => {
    if (!isOpen) return;
    const handleKeyDown = (e) => {
      if (e.key === 'ArrowDown') {
        e.preventDefault();
        setSelectedIndex(prev => (prev + 1) % (filtered.length || 1));
      } else if (e.key === 'ArrowUp') {
        e.preventDefault();
        setSelectedIndex(prev => (prev - 1 + (filtered.length || 1)) % (filtered.length || 1));
      } else if (e.key === 'Enter' && filtered[selectedIndex]) {
        e.preventDefault();
        filtered[selectedIndex].action();
        onClose();
      } else if (e.key === 'Escape') {
        onClose();
      }
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [isOpen, filtered, selectedIndex, onClose]);

  if (!isOpen) return null;

  return (
    <div className="command-modal-overlay" onClick={onClose}>
      <div className="command-modal" onClick={e => e.stopPropagation()}>
        <div className="command-input-wrapper">
          <Search size={16} style={{ color: 'var(--text-muted)' }} />
          <input
            type="text"
            placeholder="Type a command or search..."
            value={query}
            onChange={e => setQuery(e.target.value)}
            autoFocus
          />
          <button onClick={onClose} style={{ background: 'none', border: 'none', color: 'var(--text-muted)', cursor: 'pointer' }}>
            <X size={16} />
          </button>
        </div>

        <div className="command-list">
          {filtered.length === 0 ? (
            <div style={{ padding: 20, textAlign: 'center', color: 'var(--text-muted)', fontSize: 13 }}>
              No commands found
            </div>
          ) : (
            filtered.map((item, idx) => {
              const Icon = item.icon;
              return (
                <div
                  key={item.id}
                  className={`command-item ${idx === selectedIndex ? 'selected' : ''}`}
                  onClick={() => { item.action(); onClose(); }}
                  onMouseEnter={() => setSelectedIndex(idx)}
                >
                  <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                    <Icon size={14} style={{ color: 'var(--text-muted)' }} />
                    <span>{item.label}</span>
                  </div>
                  <span style={{ fontSize: 10, color: 'var(--text-muted)', textTransform: 'uppercase' }}>
                    {item.category}
                  </span>
                </div>
              );
            })
          )}
        </div>
      </div>
    </div>
  );
}
