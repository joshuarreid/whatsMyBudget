package service;

import model.BudgetRow;
import model.BudgetTransaction;
import model.ProjectedTransaction;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import util.AppLogger;
import util.ProjectedRowConverter;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * CSVStateService: Central orchestrator for statement-based budgeting.
 * Handles loading/saving of current transactions, projections, statement period/file management, and archiving.
 * Integrates with LocalCacheService for config and future statement-file index, and supports robust, future-proof workflows.
 */
@Service
public class CSVStateService {
    private static final Logger logger = AppLogger.getLogger(CSVStateService.class);

    @Autowired
    private BudgetFileService budgetFileService; // For current statement's transactions

    @Autowired
    private ProjectedFileService projectedFileService; // For projected/future transactions

    @Autowired
    private LocalCacheService localCacheService; // For config, statement period, last-open files

    // ========================
    // Projected Transaction API (refactored for robust delegation)
    // ========================

    /**
     * Loads all projected/future transactions using ProjectedFileService.
     * @return list of ProjectedTransaction
     */
    public List<ProjectedTransaction> getAllProjectedTransactions() {
        logger.info("Entering getAllProjectedTransactions()");
        List<BudgetRow> rows = projectedFileService.readAll();
        List<ProjectedTransaction> projections = rows.stream()
                .map(this::convertToProjectedTransaction)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        logger.info("Loaded {} projected transactions.", projections.size());
        return projections;
    }

    /**
     * Loads all projected transactions for a specific statement period.
     * @param period statement period string (must match exactly)
     * @return list of ProjectedTransaction for the period
     */
    public List<ProjectedTransaction> getProjectedTransactionsForPeriod(String period) {
        logger.info("Entering getProjectedTransactionsForPeriod('{}')", period);
        if (period == null || period.isBlank()) {
            logger.warn("Blank/null period provided.");
            return Collections.emptyList();
        }
        List<ProjectedTransaction> all = getAllProjectedTransactions();
        List<ProjectedTransaction> filtered = all.stream()
                .filter(tx -> period.equals(tx.getStatementPeriod()))
                .collect(Collectors.toList());
        logger.info("Returning {} projected transactions for period '{}'.", filtered.size(), period);
        return filtered;
    }

    /**
     * Adds a projected/future transaction.
     * @param projectedTx ProjectedTransaction to add
     * @return true if successful
     */
    public boolean addProjectedTransaction(ProjectedTransaction projectedTx) {
        logger.info("Entering addProjectedTransaction(): {}", projectedTx);
        if (projectedTx == null) {
            logger.error("Cannot add null projected transaction.");
            return false;
        }
        try {
            projectedFileService.add(projectedTx);
            logger.info("Added projected transaction successfully.");
            return true;
        } catch (Exception e) {
            logger.error("Failed to add projected transaction: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Updates a projected transaction in the projections file.
     * Matches by all fields (robust for no unique key; if you add UUID, update this).
     * @param original original ProjectedTransaction to match
     * @param updated updated ProjectedTransaction to replace it
     * @return true if update succeeded
     */
    public boolean updateProjectedTransaction(ProjectedTransaction original, ProjectedTransaction updated) {
        logger.info("Entering updateProjectedTransaction(): original={}, updated={}", original, updated);
        if (original == null || updated == null) {
            logger.error("Null argument given to updateProjectedTransaction.");
            return false;
        }
        // Find and update the first row matching all fields of 'original'
        List<BudgetRow> all = projectedFileService.readAll();
        Optional<BudgetRow> rowToUpdate = all.stream()
                .filter(row -> projectedTransactionEquals(row, original))
                .findFirst();
        if (rowToUpdate.isEmpty()) {
            logger.warn("Original projected transaction not found for update: {}", original);
            return false;
        }
        boolean updatedFlag = projectedFileService.update("UniqueMatch", buildRowUniqueKey(rowToUpdate.get()), updated);
        logger.info("Projected transaction update result: {}", updatedFlag);
        return updatedFlag;
    }

    /**
     * Deletes a projected transaction from the projections file.
     * Matches by all fields (robust for no unique key; if you add UUID, update this).
     * @param tx ProjectedTransaction to delete
     * @return true if deleted
     */
    public boolean deleteProjectedTransaction(ProjectedTransaction tx) {
        logger.info("Entering deleteProjectedTransaction(): {}", tx);
        if (tx == null) {
            logger.error("Null argument to deleteProjectedTransaction.");
            return false;
        }
        // Find and delete the first row matching all fields of 'tx'
        List<BudgetRow> all = projectedFileService.readAll();
        Optional<BudgetRow> rowToDelete = all.stream()
                .filter(row -> projectedTransactionEquals(row, tx))
                .findFirst();
        if (rowToDelete.isEmpty()) {
            logger.warn("Projected transaction not found for deletion: {}", tx);
            return false;
        }
        boolean deleted = projectedFileService.delete("UniqueMatch", buildRowUniqueKey(rowToDelete.get()));
        logger.info("Projected transaction delete result: {}", deleted);
        return deleted;
    }

    // ========================
    // Helpers for matching/conversion (robust, reusable)
    // ========================

    /**
     * Converts a BudgetRow to a ProjectedTransaction.
     * @param row BudgetRow to convert
     * @return ProjectedTransaction or null if conversion fails
     */
    private ProjectedTransaction convertToProjectedTransaction(BudgetRow row) {
        logger.debug("Converting BudgetRow to ProjectedTransaction: {}", row);
        try {
            Map<String, String> map = new HashMap<>();
            map.put("Name", row.getName());
            map.put("Amount", row.getAmount());
            map.put("Category", row.getCategory());
            map.put("Criticality", row.getCriticality());
            map.put("Transaction Date", row.getTransactionDate());
            map.put("Account", row.getAccount());
            map.put("status", row.getStatus());
            map.put("Created time", row.getCreatedTime());
            String statementPeriod = null;
            if (row instanceof ProjectedTransaction) {
                statementPeriod = ((ProjectedTransaction) row).getStatementPeriod();
            } else if (row instanceof BudgetTransaction) {
                statementPeriod = ((BudgetTransaction) row).getStatementPeriod();
            } else {
                statementPeriod = getCurrentStatementPeriod();
            }
            map.put("Statement Period", statementPeriod);
            ProjectedTransaction tx = ProjectedRowConverter.mapToProjectedTransaction(map);
            logger.debug("Converted row: {}", tx);
            return tx;
        } catch (Exception e) {
            logger.error("Failed to convert BudgetRow to ProjectedTransaction: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Checks whether a BudgetRow matches all fields of a ProjectedTransaction.
     * Used for robust matching when no UUID is available.
     */
    private boolean projectedTransactionEquals(BudgetRow row, ProjectedTransaction tx) {
        if (row == null || tx == null) return false;
        return Objects.equals(row.getName(), tx.getName())
                && Objects.equals(row.getAmount(), tx.getAmount())
                && Objects.equals(row.getCategory(), tx.getCategory())
                && Objects.equals(row.getCriticality(), tx.getCriticality())
                && Objects.equals(row.getTransactionDate(), tx.getTransactionDate())
                && Objects.equals(row.getAccount(), tx.getAccount())
                && Objects.equals(row.getStatus(), tx.getStatus())
                && Objects.equals(row.getCreatedTime(), tx.getCreatedTime())
                && Objects.equals(getStatementPeriodForRow(row), tx.getStatementPeriod());
    }

    /**
     * Builds a unique key for a BudgetRow for robust update/delete operations.
     * For now, uses all significant fields concatenated (should move to UUID if available).
     */
    private String buildRowUniqueKey(BudgetRow row) {
        return String.join("|",
                Objects.toString(row.getName(), ""),
                Objects.toString(row.getAmount(), ""),
                Objects.toString(row.getCategory(), ""),
                Objects.toString(row.getCriticality(), ""),
                Objects.toString(row.getTransactionDate(), ""),
                Objects.toString(row.getAccount(), ""),
                Objects.toString(row.getStatus(), ""),
                Objects.toString(row.getCreatedTime(), ""),
                Objects.toString(getStatementPeriodForRow(row), "")
        );
    }

    /**
     * Retrieves the statement period for a BudgetRow, falling back to current period if not present.
     */
    private String getStatementPeriodForRow(BudgetRow row) {
        if (row instanceof ProjectedTransaction) {
            return ((ProjectedTransaction) row).getStatementPeriod();
        } else if (row instanceof BudgetTransaction) {
            return ((BudgetTransaction) row).getStatementPeriod();
        } else {
            return getCurrentStatementPeriod();
        }
    }

    // ========================
    // Existing (working file) methods remain unchanged below...
    // ========================

    public List<BudgetTransaction> getCurrentTransactions() {
        logger.info("Entering getCurrentTransactions()");
        String statementFile = getCurrentStatementFilePath();
        if (statementFile == null || statementFile.isEmpty()) {
            logger.error("No current statement file set.");
            return Collections.emptyList();
        }
        List<BudgetRow> rows = budgetFileService.readAll();
        List<BudgetTransaction> txs = rows.stream()
                .map(row -> (row instanceof BudgetTransaction)
                        ? (BudgetTransaction) row
                        : convertToTransaction(row))
                .collect(Collectors.toList());
        logger.info("Loaded {} transactions from '{}'", txs.size(), statementFile);
        return txs;
    }

    public boolean addTransaction(BudgetTransaction tx) {
        logger.info("Entering addTransaction(): {}", tx);
        if (tx == null) {
            logger.error("Cannot add null transaction.");
            return false;
        }
        try {
            budgetFileService.add(tx);
            logger.info("Added transaction successfully.");
            return true;
        } catch (Exception e) {
            logger.error("Failed to add transaction: {}", e.getMessage());
            return false;
        }
    }

    public boolean updateTransaction(String key, String value, BudgetTransaction updatedTx) {
        logger.info("Entering updateTransaction(): key={}, value={}, updatedTx={}", key, value, updatedTx);
        if (key == null || value == null || updatedTx == null) {
            logger.error("Null argument given to updateTransaction.");
            return false;
        }
        boolean updated = budgetFileService.update(key, value, updatedTx);
        logger.info("Transaction update result: {}", updated);
        return updated;
    }

    public boolean deleteTransaction(String key, String value) {
        logger.info("Entering deleteTransaction(): key={}, value={}", key, value);
        if (key == null || value == null) {
            logger.error("Null argument to deleteTransaction.");
            return false;
        }
        boolean deleted = budgetFileService.delete(key, value);
        logger.info("Transaction delete result: {}", deleted);
        return deleted;
    }

    public boolean saveImportedTransactions(List<BudgetTransaction> transactions) {
        logger.info("Entering saveImportedTransactions() with {} transactions.", transactions == null ? 0 : transactions.size());
        if (transactions == null || transactions.isEmpty()) {
            logger.warn("No transactions provided to saveImportedTransactions.");
            return false;
        }

        String statementFile = getCurrentStatementFilePath();
        if (statementFile == null || statementFile.isEmpty()) {
            logger.error("No current statement file set. Cannot save imported transactions.");
            return false;
        }

        int successCount = 0;
        for (BudgetTransaction tx : transactions) {
            try {
                budgetFileService.add(tx);
                logger.debug("Appended imported transaction: {}", tx);
                successCount++;
            } catch (Exception e) {
                logger.error("Failed to save imported transaction: {}. Error: {}", tx, e.getMessage(), e);
            }
        }
        logger.info("saveImportedTransactions complete: {}/{} transactions saved to '{}'.", successCount, transactions.size(), statementFile);
        return successCount == transactions.size();
    }

    public String getCurrentStatementPeriod() {
        logger.info("Entering getCurrentStatementPeriod()");
        String period = localCacheService.getCurrentStatement();
        logger.info("Current statement period: {}", period);
        return period;
    }

    public void setCurrentStatementPeriod(String period) {
        logger.info("Entering setCurrentStatementPeriod(): {}", period);
        if (period == null || period.isEmpty()) {
            logger.error("Attempted to set empty/null statement period.");
            return;
        }
        localCacheService.setCurrentStatement(period);
        logger.info("Set current statement period to '{}'", period);
    }

    public String getCurrentStatementFilePath() {
        logger.info("Entering getCurrentStatementFilePath()");
        String path = localCacheService.getBudgetCsvPath();
        logger.info("Current statement file path: {}", path);
        return path;
    }

    public void setCurrentStatementFilePath(String filePath) {
        logger.info("Entering setCurrentStatementFilePath(): {}", filePath);
        if (filePath == null || filePath.isEmpty()) {
            logger.error("Attempted to set empty/null statement file path.");
            return;
        }
        localCacheService.setBudgetCsvPath(filePath);
        logger.info("Set current statement file path to '{}'", filePath);
    }

    public boolean archiveAndRolloverStatement(String newStatementPeriod) {
        logger.info("Entering archiveAndRolloverStatement() with new period '{}'", newStatementPeriod);
        String currentFile = getCurrentStatementFilePath();
        String currentPeriod = getCurrentStatementPeriod();
        if (currentFile == null || currentPeriod == null || currentFile.isEmpty() || currentPeriod.isEmpty()) {
            logger.error("Current statement file or period not set, cannot archive.");
            return false;
        }
        Path source = Paths.get(currentFile);
        String archiveName = "budget_" + currentPeriod + ".csv";
        Path archive = source.resolveSibling(archiveName);
        try {
            Files.copy(source, archive, StandardCopyOption.REPLACE_EXISTING);
            logger.info("Archived statement to '{}'", archiveName);
            Files.write(source, Collections.singletonList(String.join(",", budgetFileService.getHeaders())));
            logger.info("Cleared current statement file for new period.");
            setCurrentStatementPeriod(newStatementPeriod);
            logger.info("Set new current statement period in cache: {}", newStatementPeriod);

            // TODO: Update per-statement file index in local cache for fast lookup/averaging (future feature)
            // localCacheService.set("statementFileIndex_" + currentPeriod, archive.toString());

            return true;
        } catch (IOException e) {
            logger.error("Failed to archive and rollover: {}", e.getMessage());
            return false;
        }
    }

    private BudgetTransaction convertToTransaction(BudgetRow row) {
        logger.info("Converting BudgetRow to BudgetTransaction: {}", row);
        BudgetTransaction tx = new BudgetTransaction(
                row.getName(),
                row.getAmount(),
                row.getCategory(),
                row.getCriticality(),
                row.getTransactionDate(),
                row.getAccount(),
                row.getStatus(),
                row.getCreatedTime(),
                getCurrentStatementPeriod()
        );
        logger.info("Converted row: {}", tx);
        return tx;
    }
}