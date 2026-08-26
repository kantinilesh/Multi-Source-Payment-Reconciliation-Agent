/**
 * API Client — wraps all backend REST endpoints for the AI Finance Controller.
 * All data is fetched live from the Spring Boot backend. No hard-coded demo metrics.
 */

const BASE = '/api';

async function request(url, options = {}) {
  const res = await fetch(`${BASE}${url}`, {
    headers: { 'Accept': 'application/json', ...options.headers },
    ...options,
  });
  if (!res.ok) {
    const text = await res.text().catch(() => '');
    throw new Error(`API ${res.status}: ${text || res.statusText}`);
  }
  return res.json();
}

// ── Upload & Run ──
export async function uploadFiles(gatewayFile, bankFile, ledgerFile) {
  const form = new FormData();
  form.append('gatewayFile', gatewayFile);
  form.append('bankFile', bankFile);
  form.append('ledgerFile', ledgerFile);
  return request('/runs', { method: 'POST', body: form, headers: {} });
}

export async function triggerReconciliation(runId) {
  return request(`/runs/${runId}/reconcile`, { method: 'POST' });
}

// ── Run Status ──
export async function getRunStatus(runId) {
  return request(`/runs/${runId}`);
}

// ── Transactions ──
export async function getTransactions(runId) {
  return request(`/runs/${runId}/transactions`);
}

// ── Match Results ──
export async function getMatches(runId) {
  return request(`/runs/${runId}/matches`);
}

export async function getExceptions(runId) {
  return request(`/runs/${runId}/exceptions`);
}

// ── Summary Metrics ──
export async function getSummary(runId) {
  return request(`/runs/${runId}/summary`);
}

// ── Audit Trail ──
export async function getAuditLog(runId) {
  return request(`/runs/${runId}/audit`);
}

// ── AI Explain ──
export async function aiExplain(runId, matchId) {
  return request(`/runs/${runId}/matches/${matchId}/ai-explain`, { method: 'POST' });
}

// ── Evaluation ──
export async function getEvaluation(runId) {
  return request(`/runs/${runId}/evaluation`);
}

// ── Benchmark ──
export async function runBenchmark() {
  return request('/evaluation/run-benchmark', { method: 'POST' });
}
