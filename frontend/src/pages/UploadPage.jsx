import React, { useState, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { Upload, FileSpreadsheet, CheckCircle2, Loader2, Zap, FileText, Sparkles, ArrowRight, Database, Brain, Cpu, AlertTriangle } from 'lucide-react';
import { uploadFiles, triggerReconciliation } from '../api/client';
import { CLEAN_DATASET, EXCEPTION_DATASET } from '../data/sampleDatasets';

function UploadZone({ label, hint, file, onFile, accept = ".csv" }) {
  const inputRef = useRef();
  const [dragOver, setDragOver] = useState(false);

  const handleDrop = (e) => {
    e.preventDefault();
    setDragOver(false);
    const f = e.dataTransfer.files[0];
    if (f) onFile(f);
  };

  return (
    <div
      className={`surface-card hoverable ${file ? 'uploaded' : ''}`}
      style={{
        borderStyle: file ? 'solid' : 'dashed',
        borderColor: file ? 'var(--semantic-success)' : dragOver ? 'var(--accent-primary)' : 'var(--border-subtle)',
        background: file ? 'var(--semantic-success-subtle)' : 'var(--bg-surface)',
        textAlign: 'center',
        padding: 32,
        cursor: 'pointer',
        transition: 'all 150ms ease'
      }}
      onClick={() => inputRef.current?.click()}
      onDragOver={(e) => { e.preventDefault(); setDragOver(true); }}
      onDragLeave={() => setDragOver(false)}
      onDrop={handleDrop}
    >
      <input
        ref={inputRef}
        type="file"
        accept={accept}
        onChange={(e) => onFile(e.target.files[0])}
        style={{ display: 'none' }}
      />
      {file ? (
        <CheckCircle2 size={36} style={{ color: 'var(--semantic-success)', margin: '0 auto 12px' }} />
      ) : (
        <FileSpreadsheet size={36} style={{ color: 'var(--text-muted)', margin: '0 auto 12px' }} />
      )}
      <div style={{ fontSize: 13, fontWeight: 600, color: 'var(--text-primary)', marginBottom: 4 }}>
        {file ? file.name : label}
      </div>
      <div style={{ fontSize: 11, color: 'var(--text-muted)' }}>
        {file ? `${(file.size / 1024).toFixed(1)} KB · Validated` : hint}
      </div>
    </div>
  );
}

/* Animated Pipeline Step Indicator */
function PipelineProgress({ step }) {
  const steps = [
    { label: 'Files Ingested', icon: <Upload size={12} />, stepNum: 1 },
    { label: 'Normalized', icon: <Database size={12} />, stepNum: 2 },
    { label: 'Rules Engine', icon: <Cpu size={12} />, stepNum: 3 },
    { label: 'AI Reasoning', icon: <Brain size={12} />, stepNum: 5 },
  ];

  return (
    <div className="surface-card shimmer" style={{ marginBottom: 24, padding: 20 }}>
      <div style={{ fontSize: 11, fontWeight: 600, color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: '0.04em', marginBottom: 16 }}>
        Pipeline Execution Sequence
      </div>
      <div className="pipeline-steps">
        {steps.map((s, i) => {
          const isComplete = step >= s.stepNum;
          const isActive = !isComplete && (
            (s.stepNum === 1 && step >= 0) ||
            (s.stepNum === 2 && step >= 1) ||
            (s.stepNum === 3 && step >= 2) ||
            (s.stepNum === 5 && step >= 3)
          );

          return (
            <React.Fragment key={i}>
              <div className={`pipeline-step ${isComplete ? 'complete' : isActive ? 'active' : ''}`}>
                <div className="pipeline-step-icon">
                  {isComplete ? <CheckCircle2 size={14} /> : s.icon}
                </div>
                <span style={{ fontSize: 12, fontWeight: isComplete || isActive ? 600 : 400, whiteSpace: 'nowrap' }}>
                  {s.label}
                </span>
              </div>
              {i < steps.length - 1 && (
                <div className={`pipeline-connector ${isComplete ? 'complete' : isActive ? 'active' : ''}`} />
              )}
            </React.Fragment>
          );
        })}
      </div>
    </div>
  );
}

export default function UploadPage({ onRunCreated }) {
  const navigate = useNavigate();
  const [tab, setTab] = useState('CSV'); // 'CSV' | 'BILL'
  const [gatewayFile, setGatewayFile] = useState(null);
  const [bankFile, setBankFile] = useState(null);
  const [ledgerFile, setLedgerFile] = useState(null);
  const [billFile, setBillFile] = useState(null);

  const [uploading, setUploading] = useState(false);
  const [reconciling, setReconciling] = useState(false);
  const [uploadResult, setUploadResult] = useState(null);
  const [reconResult, setReconResult] = useState(null);
  const [error, setError] = useState(null);
  const [step, setStep] = useState(0); // 0: idle, 1: files, 2: normalized, 3: matching, 4: ai, 5: done

  const allSelected = tab === 'CSV' ? (gatewayFile && bankFile && ledgerFile) : (gatewayFile && bankFile && (ledgerFile || billFile));

  const handleUpload = async () => {
    setError(null);
    setUploading(true);
    setStep(1);
    try {
      const targetLedger = tab === 'BILL' && billFile ? billFile : ledgerFile;
      const result = await uploadFiles(gatewayFile, bankFile, targetLedger);
      setUploadResult(result);
      setStep(2);
      onRunCreated?.(result.runId);

      // Auto-trigger reconciliation
      setReconciling(true);
      setStep(3);
      const reconRes = await triggerReconciliation(result.runId);
      setReconResult(reconRes);
      setStep(5);
    } catch (e) {
      setError(e.message);
      setStep(0);
    } finally {
      setUploading(false);
      setReconciling(false);
    }
  };

  const loadDataset = (type) => {
    const ds = type === 'CLEAN' ? CLEAN_DATASET : EXCEPTION_DATASET;
    setTab('CSV');
    setGatewayFile(new File([ds.gateway], "razorpay_gateway_export.csv", { type: "text/csv" }));
    setBankFile(new File([ds.bank], "hdfc_bank_settlement.csv", { type: "text/csv" }));
    setLedgerFile(new File([ds.ledger], "tally_erp_ledger.csv", { type: "text/csv" }));
    setBillFile(null);
    setError(null);
  };

  return (
    <div className="fade-in">
      <div className="page-header" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', flexWrap: 'wrap', gap: 12 }}>
        <div>
          <h2>Upload & Reconcile</h2>
          <p>Upload multi-source CSV files or documents to execute 3-way automated reconciliation</p>
        </div>
        <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
          <button 
            className="btn btn-secondary btn-sm" 
            onClick={() => loadDataset('CLEAN')}
            title="Load 10 records with 100% clean auto-match"
          >
            <Sparkles size={14} style={{ color: 'var(--semantic-success)' }} /> Load Clean Dataset (100% Match)
          </button>
          <button 
            className="btn btn-secondary btn-sm" 
            onClick={() => loadDataset('EXCEPTION')}
            style={{ borderColor: 'var(--semantic-warning)', background: 'rgba(255, 143, 0, 0.08)' }}
            title="Load 50 enterprise records with missing bank/ledger, fee drift, and amount mismatches to showcase AI investigation"
          >
            <AlertTriangle size={14} style={{ color: 'var(--semantic-warning)' }} /> 
            <strong>Demo: Exceptions & AI Cases (50 Rows)</strong>
          </button>
        </div>
      </div>

      <div className="filter-bar" style={{ marginBottom: 20 }}>
        <button className={`filter-chip ${tab === 'CSV' ? 'active' : ''}`} onClick={() => setTab('CSV')}>
          <FileSpreadsheet size={13} style={{ verticalAlign: 'middle', marginRight: 6 }} /> Standard 3-Way CSV Files
        </button>
        <button className={`filter-chip ${tab === 'BILL' ? 'active' : ''}`} onClick={() => setTab('BILL')}>
          <FileText size={13} style={{ verticalAlign: 'middle', marginRight: 6 }} /> Invoice & Vendor Bill Ingestion
        </button>
      </div>

      {tab === 'CSV' ? (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 16, marginBottom: 24 }}>
          <UploadZone label="Gateway Export" hint="Razorpay / Stripe CSV" file={gatewayFile} onFile={setGatewayFile} />
          <UploadZone label="Bank Settlement" hint="HDFC / ICICI Bank CSV" file={bankFile} onFile={setBankFile} />
          <UploadZone label="Internal Ledger" hint="Tally / NetSuite ERP CSV" file={ledgerFile} onFile={setLedgerFile} />
        </div>
      ) : (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 16, marginBottom: 24 }}>
          <UploadZone label="Gateway Export" hint="Razorpay Export CSV" file={gatewayFile} onFile={setGatewayFile} />
          <UploadZone label="Bank Settlement" hint="Bank Settlement CSV" file={bankFile} onFile={setBankFile} />
          <UploadZone label="Custom Bill / Document" hint="Upload Slip (CSV / Text / Receipt)" file={billFile} onFile={setBillFile} accept=".csv,.txt,.pdf" />
        </div>
      )}

      {/* Animated Pipeline Progress */}
      {step > 0 && <PipelineProgress step={step} />}

      {error && (
        <div className="surface-card" style={{ marginBottom: 20, borderColor: 'var(--semantic-danger)', color: 'var(--semantic-danger)', fontSize: 13 }}>
          ⚠ {error}
        </div>
      )}

      <div style={{ display: 'flex', gap: 12, alignItems: 'center' }}>
        <button
          className="btn btn-primary"
          disabled={!allSelected || uploading || reconciling}
          onClick={handleUpload}
        >
          {uploading || reconciling ? (
            <><div className="spinner" style={{ width: 16, height: 16, borderWidth: 2 }} /> Reconciling Pipeline...</>
          ) : (
            <><Zap size={16} /> Execute Reconciliation</>
          )}
        </button>

        {reconResult && (
          <button className="btn btn-secondary" onClick={() => navigate('/dashboard')}>
            View Overview Dashboard <ArrowRight size={14} />
          </button>
        )}
      </div>

      {uploadResult && (
        <div className="surface-card slide-up" style={{ marginTop: 24, opacity: 0 }}>
          <div style={{ fontSize: 11, fontWeight: 600, textTransform: 'uppercase', color: 'var(--text-muted)', marginBottom: 12 }}>
            Execution Results Summary
          </div>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 16 }}>
            <div>
              <div style={{ fontSize: 11, color: 'var(--text-muted)' }}>Run ID</div>
              <div className="mono" style={{ fontSize: 16, fontWeight: 700 }}>#{uploadResult.runId}</div>
            </div>
            <div>
              <div style={{ fontSize: 11, color: 'var(--text-muted)' }}>Gateway Rows</div>
              <div className="mono" style={{ fontSize: 16, fontWeight: 700 }}>{uploadResult.gatewayRowCount}</div>
            </div>
            <div>
              <div style={{ fontSize: 11, color: 'var(--text-muted)' }}>Bank Rows</div>
              <div className="mono" style={{ fontSize: 16, fontWeight: 700 }}>{uploadResult.bankRowCount}</div>
            </div>
            <div>
              <div style={{ fontSize: 11, color: 'var(--text-muted)' }}>Ledger Rows</div>
              <div className="mono" style={{ fontSize: 16, fontWeight: 700 }}>{uploadResult.ledgerRowCount}</div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
