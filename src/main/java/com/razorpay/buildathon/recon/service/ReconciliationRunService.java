package com.razorpay.buildathon.recon.service;

import com.razorpay.buildathon.recon.dto.RunUploadResponse;
import com.razorpay.buildathon.recon.model.NormalizedTransaction;
import com.razorpay.buildathon.recon.model.ReconciliationRun;
import com.razorpay.buildathon.recon.model.RunStatus;
import com.razorpay.buildathon.recon.repository.NormalizedTransactionRepository;
import com.razorpay.buildathon.recon.repository.ReconciliationRunRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Orchestrates Phase 2's scope: create a run, parse the 3 uploaded files into
 * normalized transactions, persist everything, mark the run ready for matching.
 */
@Service
public class ReconciliationRunService {

    private final ReconciliationRunRepository runRepository;
    private final NormalizedTransactionRepository transactionRepository;
    private final TransactionNormalizationService normalizationService;

    public ReconciliationRunService(ReconciliationRunRepository runRepository,
                                     NormalizedTransactionRepository transactionRepository,
                                     TransactionNormalizationService normalizationService) {
        this.runRepository = runRepository;
        this.transactionRepository = transactionRepository;
        this.normalizationService = normalizationService;
    }

    @Transactional
    public RunUploadResponse createRunFromUpload(MultipartFile gatewayFile,
                                                   MultipartFile bankFile,
                                                   MultipartFile ledgerFile) {
        ReconciliationRun run = new ReconciliationRun();
        run.setStatus(RunStatus.NORMALIZING);
        run.setGatewayFileName(gatewayFile.getOriginalFilename());
        run.setBankFileName(bankFile.getOriginalFilename());
        run.setLedgerFileName(ledgerFile.getOriginalFilename());
        run = runRepository.save(run);

        try {
            List<NormalizedTransaction> gatewayTxns =
                    normalizationService.parseGatewayExport(gatewayFile, run);
            List<NormalizedTransaction> bankTxns =
                    normalizationService.parseBankSettlement(bankFile, run);
            List<NormalizedTransaction> ledgerTxns =
                    normalizationService.parseInternalLedger(ledgerFile, run);

            transactionRepository.saveAll(gatewayTxns);
            transactionRepository.saveAll(bankTxns);
            transactionRepository.saveAll(ledgerTxns);

            run.setStatus(RunStatus.NORMALIZING);
            runRepository.save(run);

            return new RunUploadResponse(
                    run.getId(),
                    run.getStatus(),
                    gatewayTxns.size(),
                    bankTxns.size(),
                    ledgerTxns.size(),
                    "Files parsed and normalized successfully. Ready for matching (Phase 3)."
            );
        } catch (Exception e) {
            run.setStatus(RunStatus.FAILED);
            runRepository.save(run);
            throw new RuntimeException("Failed to process upload for run " + run.getId() + ": "
                    + e.getMessage(), e);
        }
    }

    public ReconciliationRun getRun(Long runId) {
        return runRepository.findById(runId)
                .orElseThrow(() -> new IllegalArgumentException("No such run: " + runId));
    }

    public List<NormalizedTransaction> getTransactionsForRun(Long runId) {
        return transactionRepository.findByRunId(runId);
    }
}
