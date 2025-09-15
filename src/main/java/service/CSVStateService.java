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
 *
 * IMPORTANT: BudgetTransaction objects may have a statementPeriod field (for import/export/historical reasons),
 * but the application logic for all current transaction operations should NEVER use or filter on that field.
 * Only ProjectedTransaction logic uses statementPeriod for filtering and grouping.
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
                // BudgetTransaction may have statementPeriod, but we never use it for logic
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
     * Only compares visible/editable fields: Name, Amount, Category, Criticality, Account, Created Time, Statement Period.
     */
    private boolean projectedTransactionEquals(BudgetRow row, ProjectedTransaction tx) {
        logger.debug("Entering projectedTransactionEquals: row={}, tx={}", row, tx);
        if (row == null || tx == null) {
            logger.warn("projectedTransactionEquals: row or tx is null (row={}, tx={})", row, tx);
            return false;
        }
        boolean match =
                Objects.equals(row.getName(), tx.getName()) &&
                        Objects.equals(row.getAmount(), tx.getAmount()) &&
                        Objects.equals(row.getCategory(), tx.getCategory()) &&
                        Objects.equals(row.getCriticality(), tx.getCriticality()) &&
                        Objects.equals(row.getAccount(), tx.getAccount()) &&
                        Objects.equals(row.getCreatedTime(), tx.getCreatedTime()) &&
                        Objects.equals(getStatementPeriodForRow(row), tx.getStatementPeriod());
        if (!match) {
            logger.debug("projectedTransactionEquals: No match for row={} vs tx={}", row, tx);
        } else {
            logger.debug("projectedTransactionEquals: Match found for row={} and tx={}", row, tx);
        }
        return match;
    }

    /**
     * Builds a unique key for a BudgetRow for robust update/delete operations.
     * Uses only fields shown in the current UI/table: Name, Amount, Category, Criticality, Account, Created Time, Statement Period.
     */
    private String buildRowUniqueKey(BudgetRow row) {
        logger.debug("Entering buildRowUniqueKey for row={}", row);
        String key = String.join("|",
                Objects.toString(row.getName(), ""),
                Objects.toString(row.getAmount(), ""),
                Objects.toString(row.getCategory(), ""),
                Objects.toString(row.getCriticality(), ""),
                Objects.toString(row.getAccount(), ""),
                Objects.toString(row.getCreatedTime(), ""),
                Objects.toString(getStatementPeriodForRow(row), "")
        );
        logger.debug("buildRowUniqueKey: generated key={}", key);
        return key;
    }

    /**
     * Retrieves the statement period for a BudgetRow, falling back to current period if not present.
     * For BudgetTransaction, this is only used for projection operations (never for transaction logic).
     */
    private String getStatementPeriodForRow(BudgetRow row) {
        if (row instanceof ProjectedTransaction) {
            return ((ProjectedTransaction) row).getStatementPeriod();
        } else if (row instanceof BudgetTransaction) {
            // For BudgetTransaction, statementPeriod is present but not used for transaction logic.
            return ((BudgetTransaction) row).getStatementPeriod();
        } else {
            return getCurrentStatementPeriod();
        }
    }

    // ========================
    // Statement period & file management
    // ========================

    /**
     * Gets the currently active statement period from LocalCacheService.
     * @return The current statement period, or null if not set.
     */
    public String getCurrentStatementPeriod() {
        logger.info("Entering getCurrentStatementPeriod()");
        String period = localCacheService.getCurrentStatement();
        if (period == null || period.isBlank()) {
            logger.warn("No current statement period is set in LocalCacheService.");
            return null;
        }
        logger.info("Current statement period: {}", period);
        return period;
    }

    /**
     * Sets the current statement period in LocalCacheService.
     * @param period The statement period to set.
     */
    public void setCurrentStatementPeriod(String period) {
        logger.info("Entering setCurrentStatementPeriod(): {}", period);
        if (period == null || period.isEmpty()) {
            logger.error("Attempted to set empty/null statement period.");
            return;
        }
        localCacheService.setCurrentStatement(period);
        logger.info("Set current statement period to '{}'", period);
    }

    /**
     * Gets all budget transactions in the current working file.
     * No statement period filtering is applied; all transactions in the working file are considered current.
     * The statementPeriod field in BudgetTransaction is ignored for all transaction logic.
     * @return List of BudgetTransaction for the working file, or empty list if none.
     */
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
        logger.info("Loaded {} transactions from '{}'. No statement period filtering performed.", txs.size(), statementFile);
        return txs;
    }

    /**
     * Saves a list of imported BudgetTransaction objects to the current working statement file.
     * Logs all actions, validates input, and returns true if all transactions were saved successfully.
     *
     * @param transactions List of BudgetTransaction objects to save (must not be null or empty).
     * @return true if all transactions were written to the working file, false otherwise.
     */
    public boolean saveImportedTransactions(List<BudgetTransaction> transactions) {
        logger.info("Entering saveImportedTransactions() with {} transaction(s).", transactions == null ? 0 : transactions.size());
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

    // ========================
    // Statement file path and archive/rollover logic unchanged
    // ========================

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

    /**
     * Converts a BudgetRow to a BudgetTransaction.
     * Ensures statementPeriod is never null to avoid IllegalArgumentException.
     * For transactions from files without a statementPeriod column, uses an empty string.
     * Logs all conversions and any fallback behavior.
     * @param row BudgetRow to convert
     * @return BudgetTransaction
     */
    /**
     * Converts a BudgetRow to a BudgetTransaction.
     * Ensures statementPeriod is never null to avoid IllegalArgumentException.
     * For transactions from files without a statementPeriod column, uses an empty string.
     * Logs all conversions and any fallback behavior.
     * @param row BudgetRow to convert
     * @return BudgetTransaction
     */
    private BudgetTransaction convertToTransaction(BudgetRow row) {
        logger.info("Converting BudgetRow to BudgetTransaction: {}", row);
        String statementPeriod = null;
        if (row instanceof BudgetTransaction) {
            statementPeriod = ((BudgetTransaction) row).getStatementPeriod();
        }
        // Defensive: If null, set to empty string (never pass null to constructor)
        if (statementPeriod == null) {
            statementPeriod = "";
        }
        // Always pass paymentMethod from BudgetRow
        BudgetTransaction tx = new BudgetTransaction(
                row.getName(),
                row.getAmount(),
                row.getCategory(),
                row.getCriticality(),
                row.getTransactionDate(),
                row.getAccount(),
                row.getStatus(),
                row.getCreatedTime(),
                row.getPaymentMethod(),
                statementPeriod
        );
        logger.info("Converted row: {}", tx);
        return tx;
    }
}