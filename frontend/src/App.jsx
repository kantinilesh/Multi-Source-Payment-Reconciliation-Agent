import React, { useState, useCallback } from 'react';
import { Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider, useAuth } from './context/AuthContext';
import Layout from './components/Layout';
import LoginPage from './pages/LoginPage';
import UploadPage from './pages/UploadPage';
import DashboardPage from './pages/DashboardPage';
import TransactionsPage from './pages/TransactionsPage';
import AiInvestigationPage from './pages/AiInvestigationPage';
import ExceptionsPage from './pages/ExceptionsPage';
import AuditPage from './pages/AuditPage';

function ProtectedLayout({ children, runId, runs, onRunChange }) {
  const { user } = useAuth();
  if (!user) {
    return <Navigate to="/login" replace />;
  }
  return (
    <Layout runId={runId} runs={runs} onRunChange={onRunChange}>
      {children}
    </Layout>
  );
}

export default function App() {
  const [runId, setRunId] = useState(null);
  const [runs, setRuns] = useState([]);

  const handleRunCreated = useCallback((id) => {
    setRunId(id);
    setRuns((prev) => {
      if (prev.find((r) => r.id === id)) return prev;
      return [...prev, { id }];
    });
  }, []);

  const handleRunChange = useCallback((id) => {
    setRunId(id);
  }, []);

  return (
    <AuthProvider>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route
          path="/*"
          element={
            <ProtectedLayout runId={runId} runs={runs} onRunChange={handleRunChange}>
              <Routes>
                <Route path="/" element={<UploadPage onRunCreated={handleRunCreated} />} />
                <Route path="/dashboard" element={<DashboardPage runId={runId} />} />
                <Route path="/transactions" element={<TransactionsPage runId={runId} />} />
                <Route path="/ai-investigation" element={<AiInvestigationPage runId={runId} />} />
                <Route path="/exceptions" element={<ExceptionsPage runId={runId} />} />
                <Route path="/audit" element={<AuditPage runId={runId} />} />
              </Routes>
            </ProtectedLayout>
          }
        />
      </Routes>
    </AuthProvider>
  );
}
