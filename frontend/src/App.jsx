import React, { useState, useCallback } from 'react';
import { Routes, Route } from 'react-router-dom';
import Layout from './components/Layout';
import UploadPage from './pages/UploadPage';
import DashboardPage from './pages/DashboardPage';
import TransactionsPage from './pages/TransactionsPage';
import AiInvestigationPage from './pages/AiInvestigationPage';
import ExceptionsPage from './pages/ExceptionsPage';
import AuditPage from './pages/AuditPage';

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
    <Layout runId={runId} runs={runs} onRunChange={handleRunChange}>
      <Routes>
        <Route path="/" element={<UploadPage onRunCreated={handleRunCreated} />} />
        <Route path="/dashboard" element={<DashboardPage runId={runId} />} />
        <Route path="/transactions" element={<TransactionsPage runId={runId} />} />
        <Route path="/ai-investigation" element={<AiInvestigationPage runId={runId} />} />
        <Route path="/exceptions" element={<ExceptionsPage runId={runId} />} />
        <Route path="/audit" element={<AuditPage runId={runId} />} />
      </Routes>
    </Layout>
  );
}
