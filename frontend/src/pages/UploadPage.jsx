import React, { useState, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { Upload, FileSpreadsheet, CheckCircle, Loader, Zap } from 'lucide-react';
import { uploadFiles, triggerReconciliation } from '../api/client';

function UploadZone({ label, hint, file, onFile }) {
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
        accept=".csv"
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
  const [gatewayFile, setGatewayFile] = useState(null);
  const [bankFile, setBankFile] = useState(null);
  const [ledgerFile, setLedgerFile] = useState(null);
  const [uploading, setUploading] = useState(false);
  const [reconciling, setReconciling] = useState(false);
  const [uploadResult, setUploadResult] = useState(null);
  const [reconResult, setReconResult] = useState(null);
  const [error, setError] = useState(null);
  const [progress, setProgress] = useState(0);

  const allSelected = gatewayFile && bankFile && ledgerFile;

  const handleUpload = async () => {
    setError(null);
    setUploading(true);
    setProgress(20);
    try {
      const result = await uploadFiles(gatewayFile, bankFile, ledgerFile);
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

  return (
    <div>
      <div className="page-header">
        <h2>Upload & Reconcile</h2>
        <p>Upload your financial source files to start the reconciliation pipeline</p>
      </div>

      <div className="upload-grid">
        <UploadZone
          label="Gateway Export"
          hint="Payment gateway transactions CSV"
          file={gatewayFile}
          onFile={setGatewayFile}
        />
        <UploadZone
          label="Bank Settlement"
          hint="Bank settlement records CSV"
          file={bankFile}
          onFile={setBankFile}
        />
        <UploadZone
          label="Internal Ledger"
          hint="ERP internal ledger CSV"
          file={ledgerFile}
          onFile={setLedgerFile}
        />
      </div>

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
