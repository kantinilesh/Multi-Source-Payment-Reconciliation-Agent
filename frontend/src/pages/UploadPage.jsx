import React, { useState, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { Upload, FileSpreadsheet, CheckCircle, Loader, Zap, FileText, Sparkles } from 'lucide-react';
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
      className={`file-upload-zone ${file ? 'uploaded' : ''} ${dragOver ? 'drag-over' : ''}`}
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
        <CheckCircle className="upload-icon" style={{ color: 'var(--accent-green)' }} />
      ) : (
        <FileSpreadsheet className="upload-icon" />
      )}
      <div className="upload-label">{file ? file.name : label}</div>
      <div className="upload-hint">
        {file ? `${(file.size / 1024).toFixed(1)} KB` : hint}
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
  const [progress, setProgress] = useState(0);

  const allSelected = tab === 'CSV' ? (gatewayFile && bankFile && ledgerFile) : (gatewayFile && bankFile && (ledgerFile || billFile));

  const handleUpload = async () => {
    setError(null);
    setUploading(true);
    setProgress(20);
    try {
      const targetLedger = tab === 'BILL' && billFile ? billFile : ledgerFile;
      const result = await uploadFiles(gatewayFile, bankFile, targetLedger);
      setUploadResult(result);
      setProgress(50);
      onRunCreated?.(result.runId);

      // Auto-trigger reconciliation
      setReconciling(true);
      setProgress(70);
      const reconRes = await triggerReconciliation(result.runId);
      setReconResult(reconRes);
      setProgress(100);
    } catch (e) {
      setError(e.message);
    } finally {
      setUploading(false);
      setReconciling(false);
    }
  };

  const handleLoadEnterpriseSample = () => {
    const gwCsv = `payment_id,order_id,amount,fee,tax,status,timestamp,payment_method,card_network
pay_N1A001,order_RZP_001,1500.00,35.40,5.40,SUCCESS,2024-02-10T09:15:00Z,card,Visa
pay_N1A002,order_RZP_002,4500.00,106.20,16.20,SUCCESS,2024-02-10T09:22:00Z,upi,UPI
pay_N1A003,order_RZP_003,890.00,20.98,3.20,SUCCESS,2024-02-10T09:40:00Z,netbanking,HDFC
pay_N1A004,order_RZP_004,12500.00,295.00,45.00,SUCCESS,2024-02-10T10:05:00Z,card,MasterCard
pay_N1A005,order_RZP_005,3200.00,75.52,11.52,SUCCESS,2024-02-10T10:30:00Z,upi,UPI
pay_N1A006,order_RZP_006,6700.00,158.12,24.12,SUCCESS,2024-02-10T11:00:00Z,card,Amex
pay_N1A007,order_RZP_007,2100.00,49.56,7.56,SUCCESS,2024-02-10T11:45:00Z,upi,UPI
pay_N1A008,order_RZP_008,9400.00,221.84,33.84,SUCCESS,2024-02-10T12:15:00Z,netbanking,ICICI
pay_N1A009,order_RZP_009,1800.00,42.48,6.48,REFUNDED,2024-02-10T13:00:00Z,upi,UPI
pay_N1A010,order_RZP_010,5300.00,125.08,19.08,SUCCESS,2024-02-10T14:10:00Z,card,Visa`;

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
    <div>
      <div className="page-header" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
        <div>
          <h2>Upload & Reconcile</h2>
          <p>Upload source files or invoice documents to trigger reconciliation</p>
        </div>
        <button className="btn btn-secondary btn-sm" onClick={handleLoadEnterpriseSample}>
          <Sparkles size={14} style={{ color: 'var(--accent-amber)' }} /> Load Enterprise Razorpay/HDFC Datasets
        </button>
      </div>

      <div className="filter-bar" style={{ marginBottom: 20 }}>
        <button className={`filter-chip ${tab === 'CSV' ? 'active' : ''}`} onClick={() => setTab('CSV')}>
          <FileSpreadsheet size={14} style={{ verticalAlign: 'middle', marginRight: 6 }} /> Standard 3-Way CSV Files
        </button>
        <button className={`filter-chip ${tab === 'BILL' ? 'active' : ''}`} onClick={() => setTab('BILL')}>
          <FileText size={14} style={{ verticalAlign: 'middle', marginRight: 6 }} /> Invoice & Vendor Bill Ingestion
        </button>
      </div>

      {tab === 'CSV' ? (
        <div className="upload-grid">
          <UploadZone label="Gateway Export" hint="Razorpay / Stripe CSV" file={gatewayFile} onFile={setGatewayFile} />
          <UploadZone label="Bank Settlement" hint="HDFC / ICICI Bank CSV" file={bankFile} onFile={setBankFile} />
          <UploadZone label="Internal Ledger" hint="Tally / NetSuite ERP CSV" file={ledgerFile} onFile={setLedgerFile} />
        </div>
      ) : (
        <div className="upload-grid">
          <UploadZone label="Gateway Export" hint="Razorpay Export CSV" file={gatewayFile} onFile={setGatewayFile} />
          <UploadZone label="Bank Settlement" hint="Bank Settlement CSV" file={bankFile} onFile={setBankFile} />
          <UploadZone label="Custom Bill / Invoice Document" hint="Upload Bill (CSV / Text / Invoice)" file={billFile} onFile={setBillFile} accept=".csv,.txt,.pdf" />
        </div>
      )}

      {error && (
        <div className="glass-card no-hover" style={{ marginBottom: 16, borderColor: 'var(--accent-rose)', color: 'var(--accent-rose)', fontSize: 13 }}>
          ⚠ {error}
        </div>
      )}

      {(uploading || reconciling) && (
        <div className="progress-bar" style={{ marginBottom: 16 }}>
          <div className="progress-fill" style={{ width: `${progress}%` }} />
        </div>
      )}

      <div style={{ display: 'flex', gap: 12, alignItems: 'center' }}>
        <button
          className="btn btn-primary"
          disabled={!allSelected || uploading || reconciling}
          onClick={handleUpload}
        >
          {uploading ? (
            <><Loader size={16} className="spinner" style={{ border: 'none', borderTopColor: 'transparent' }} /> Uploading...</>
          ) : reconciling ? (
            <><Zap size={16} /> Reconciling...</>
          ) : (
            <><Upload size={16} /> Upload & Reconcile</>
          )}
        </button>

        {reconResult && (
          <button
            className="btn btn-secondary"
            onClick={() => navigate('/dashboard')}
          >
            View Dashboard →
          </button>
        )}
      </div>

      {uploadResult && (
        <div className="glass-card no-hover" style={{ marginTop: 24 }}>
          <div className="section-block">
            <div className="section-title">Upload Results</div>
            <div className="info-grid">
              <div className="info-item">
                <span className="info-label">Run ID</span>
                <span className="info-value mono">#{uploadResult.runId}</span>
              </div>
              <div className="info-item">
                <span className="info-label">Status</span>
                <span className="info-value">{reconResult ? reconResult.status : uploadResult.status}</span>
              </div>
              <div className="info-item">
                <span className="info-label">Gateway Rows</span>
                <span className="info-value mono">{uploadResult.gatewayRowCount}</span>
              </div>
              <div className="info-item">
                <span className="info-label">Bank Rows</span>
                <span className="info-value mono">{uploadResult.bankRowCount}</span>
              </div>
              <div className="info-item">
                <span className="info-label">Ledger Rows</span>
                <span className="info-value mono">{uploadResult.ledgerRowCount}</span>
              </div>
              {reconResult && (
                <>
                  <div className="info-item">
                    <span className="info-label">Reconciled</span>
                    <span className="info-value mono" style={{ color: 'var(--accent-green)' }}>{reconResult.matchedCount || 0}</span>
                  </div>
                  <div className="info-item">
                    <span className="info-label">Exceptions</span>
                    <span className="info-value mono" style={{ color: 'var(--accent-rose)' }}>{reconResult.exceptionCount || 0}</span>
                  </div>
                  <div className="info-item">
                    <span className="info-label">Processing Time</span>
                    <span className="info-value mono">{reconResult.processingTimeMs || 0}ms</span>
                  </div>
                </>
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
