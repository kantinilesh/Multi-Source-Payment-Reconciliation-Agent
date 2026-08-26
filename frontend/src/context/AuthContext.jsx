import React, { createContext, useContext, useState, useEffect } from 'react';
import { apiLogin, apiSignup } from '../api/client';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(() => {
    try {
      const saved = localStorage.getItem('recon_auth_user');
      return saved ? JSON.parse(saved) : { email: 'controller@razorpay.com', name: 'Nilesh Kanti', role: 'Finance Controller' };
    } catch (e) {
      return { email: 'controller@razorpay.com', name: 'Nilesh Kanti', role: 'Finance Controller' };
    }
  });

  useEffect(() => {
    if (user) {
      localStorage.setItem('recon_auth_user', JSON.stringify(user));
    } else {
      localStorage.removeItem('recon_auth_user');
    }
  }, [user]);

  const login = async (email, password) => {
    try {
      const res = await apiLogin(email, password);
      if (res.user) {
        setUser(res.user);
        return res.user;
      }
    } catch (e) {
      console.warn('Backend login fallback:', e.message);
    }
    const name = email.split('@')[0].replace('.', ' ');
    const formattedName = name.charAt(0).toUpperCase() + name.slice(1);
    const u = { email, name: formattedName, role: 'Finance Controller' };
    setUser(u);
    return u;
  };

  const signup = async (email, password, name) => {
    try {
      const res = await apiSignup(email, password, name);
      if (res.user) {
        setUser(res.user);
        return res.user;
      }
    } catch (e) {
      console.warn('Backend signup fallback:', e.message);
    }
    const u = { email, name: name || 'Finance Analyst', role: 'Finance Controller' };
    setUser(u);
    return u;
  };

  const quickDemoLogin = (role = 'Finance Controller') => {
    const demoUsers = {
      'Finance Controller': { email: 'controller@razorpay.com', name: 'Nilesh Kanti', role: 'Finance Controller' },
      'Audit Analyst': { email: 'audit@finops.org', name: 'Sarah Jenkins', role: 'Audit Analyst' },
      'Store Manager': { email: 'store.manager@retail.com', name: 'Alex Rivera', role: 'Store Manager' },
    };
    const u = demoUsers[role] || demoUsers['Finance Controller'];
    setUser(u);
    return u;
  };

  const logout = () => {
    setUser(null);
  };

  return (
    <AuthContext.Provider value={{ user, login, signup, logout, quickDemoLogin }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
}
