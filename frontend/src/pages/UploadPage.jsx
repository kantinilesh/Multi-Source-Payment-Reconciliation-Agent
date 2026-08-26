import React, { useState, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { Upload, FileSpreadsheet, CheckCircle2, Loader2, Zap, FileText, Sparkles, ArrowRight } from 'lucide-react';
import { uploadFiles, triggerReconciliation } from '../api/client';

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

  const handleLoadEnterpriseSample = () => {
    const gwCsv = `payment_id,order_id,amount,fee,tax,status,timestamp,payment_method,card_network
pay_RZP_001,order_RZP_001,1500.00,35.40,5.40,SUCCESS,2024-02-10T09:15:00Z,card,Visa
pay_RZP_002,order_RZP_002,4500.00,106.20,16.20,SUCCESS,2024-02-10T09:22:00Z,upi,UPI
pay_RZP_003,order_RZP_003,890.00,20.98,3.20,SUCCESS,2024-02-10T09:40:00Z,netbanking,HDFC
pay_RZP_004,order_RZP_004,12500.00,295.00,45.00,SUCCESS,2024-02-10T10:05:00Z,card,MasterCard
pay_RZP_005,order_RZP_005,3200.00,75.52,11.52,SUCCESS,2024-02-10T10:30:00Z,upi,UPI
pay_RZP_006,order_RZP_006,6700.00,158.12,24.12,SUCCESS,2024-02-10T11:00:00Z,card,Amex
pay_RZP_007,order_RZP_007,2100.00,49.56,7.56,SUCCESS,2024-02-10T11:45:00Z,upi,UPI
pay_RZP_008,order_RZP_008,9400.00,221.84,33.84,SUCCESS,2024-02-10T12:15:00Z,netbanking,ICICI
pay_RZP_009,order_RZP_009,1800.00,42.48,6.48,REFUNDED,2024-02-10T13:00:00Z,upi,UPI
pay_RZP_010,order_RZP_010,5300.00,125.08,19.08,SUCCESS,2024-02-10T14:10:00Z,card,Visa`;

    const bkCsv = `utr_number,reference_note,settled_amount,settlement_date,bank_name
UTR9821001,SET-RZP-001,1464.60,2024-02-12T00:00:00Z,HDFC Bank
UTR9821002,SET-RZP-002,4393.80,2024-02-12T00:00:00Z,HDFC Bank
UTR9821003,SET-RZP-003,869.02,2024-02-12T00:00:00Z,HDFC Bank
UTR9821004,SET-RZP-004,12205.00,2024-02-12T00:00:00Z,HDFC Bank
UTR9821005,SET-RZP-005,3124.48,2024-02-12T00:00:00Z,HDFC Bank
UTR9821006,SET-RZP-006,6541.88,2024-02-12T00:00:00Z,HDFC Bank
UTR9821007,SET-RZP-007,2050.44,2024-02-12T00:00:00Z,HDFC Bank
UTR9821008,SET-RZP-008,9178.16,2024-02-12T00:00:00Z,HDFC Bank
UTR9821009,SET-RZP-009,1757.52,2024-02-12T00:00:00Z,HDFC Bank
UTR9821010,SET-RZP-010,5174.92,2024-02-12T00:00:00Z,HDFC Bank`;

    const lgCsv = `voucher_no,order_ref,amount,status,date,account_head
VCH-2024-001,PAY-RZP-001,1500.00,PAID,2024-02-10,Sales Income
VCH-2024-002,PAY-RZP-002,4500.00,PAID,2024-02-10,Sales Income
VCH-2024-003,PAY-RZP-003,890.00,PAID,2024-02-10,Sales Income
VCH-2024-004,PAY-RZP-004,12500.00,PAID,2024-02-10,Sales Income
VCH-2024-005,PAY-RZP-005,3200.00,PAID,2024-02-10,Sales Income
VCH-2024-006,PAY-RZP-006,6700.00,PAID,2024-02-10,Sales Income
VCH-2024-007,PAY-RZP-007,2100.00,PAID,2024-02-10,Sales Income
VCH-2024-008,PAY-RZP-008,9400.00,PAID,2024-02-10,Sales Income
VCH-2024-009,PAY-RZP-009,1800.00,REFUNDED,2024-02-10,Sales Returns
VCH-2024-010,PAY-RZP-010,5300.00,PAID,2024-02-10,Sales Income`;

    setGatewayFile(new File([gwCsv], "razorpay_gateway_export.csv", { type: "text/csv" }));
    setBankFile(new File([bkCsv], "hdfc_bank_settlement.csv", { type: "text/csv" }));
    setLedgerFile(new File([lgCsv], "tally_erp_ledger.csv", { type: "text/csv" }));
  };

  return (
    <div className="fade-in">
      <div className="page-header" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
        <div>
          <h2>Upload & Reconcile</h2>
          <p>Upload source CSV files or receipt documents to execute multi-source reconciliation</p>
        </div>
        <button className="btn btn-secondary btn-sm" onClick={handleLoadEnterpriseSample}>
          <Sparkles size={14} style={{ color: 'var(--semantic-warning)' }} /> Load Enterprise Datasets
        </button>
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

      {/* Progress Sequence Animation */}
      {step > 0 && (
        <div className="surface-card" style={{ marginBottom: 24, padding: 20 }}>
          <div style={{ fontSize: 12, fontWeight: 600, color: 'var(--text-muted)', textTransform: 'uppercase', marginBottom: 12 }}>
            Pipeline Execution Sequence
          </div>
          <div style={{ display: 'flex', gap: 16, alignItems: 'center', fontSize: 13 }}>
            <span style={{ color: step >= 1 ? 'var(--semantic-success)' : 'var(--text-muted)' }}>
              {step >= 1 ? '✓' : '○'} Files Ingested
            </span>
            <span>→</span>
            <span style={{ color: step >= 2 ? 'var(--semantic-success)' : 'var(--text-muted)' }}>
              {step >= 2 ? '✓' : '○'} Transactions Normalized
            </span>
            <span>→</span>
            <span style={{ color: step >= 3 ? 'var(--semantic-success)' : 'var(--text-muted)' }}>
              {step >= 3 ? '✓' : '○'} Rules Matching Engine
            </span>
            <span>→</span>
            <span style={{ color: step >= 5 ? 'var(--semantic-success)' : 'var(--text-muted)' }}>
              {step >= 5 ? '✓' : '○'} AI Exception Reasoning
            </span>
          </div>
        </div>
      )}

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
            <><Loader2 size={16} className="spinner" /> Reconciling Pipeline...</>
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
        <div className="surface-card" style={{ marginTop: 24 }}>
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
