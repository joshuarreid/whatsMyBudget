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

    /**
     * Loads all transactions for the current statement period.
     * @return List of BudgetTransaction for current statement
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
        logger.info("Loaded {} transactions from '{}'", txs.size(), statementFile);
        return txs;
    }

    /**
     * Adds a new transaction to the current statement.
     * @param tx BudgetTransaction to add
     * @return true if successful
     */
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

    /**
     * Updates a transaction in the current statement file by key-value lookup.
     * @param key   field for matching (e.g., "Name")
     * @param value value to match
     * @param updatedTx updated transaction
     * @return true if update succeeded
     */
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

    /**
     * Deletes a transaction from the current statement file by key-value lookup.
     * @param key   field for matching
     * @param value value to match
     * @return true if deleted
     */
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

    /**
     * Loads all projected/future transactions.
     * @return list of ProjectedTransaction
     */
    public List<ProjectedTransaction> getProjectedTransactions() {
        logger.info("Entering getProjectedTransactions()");
        List<BudgetRow> rows = projectedFileService.readAll();
        List<ProjectedTransaction> projections = rows.stream()
                .map(row -> row instanceof ProjectedTransaction
                        ? (ProjectedTransaction) row
                        : convertToProjectedTransaction(row))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        logger.info("Loaded {} projected transactions.", projections.size());
        return projections;
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
            logger.error("Failed to add projected transaction: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Updates a projected transaction in the projections file by key-value lookup.
     * @param key   field for matching (e.g., "Name")
     * @param value value to match
     * @param updatedTx updated projected transaction
     * @return true if update succeeded
     */
    public boolean updateProjectedTransaction(String key, String value, ProjectedTransaction updatedTx) {
        logger.info("Entering updateProjectedTransaction(): key={}, value={}, updatedTx={}", key, value, updatedTx);
        if (key == null || value == null || updatedTx == null) {
            logger.error("Null argument given to updateProjectedTransaction.");
            return false;
        }
        boolean updated = projectedFileService.update(key, value, updatedTx);
        logger.info("Projected transaction update result: {}", updated);
        return updated;
    }

    /**
     * Deletes a projected transaction from the projections file by key-value lookup.
     * @param key   field for matching
     * @param value value to match
     * @return true if deleted
     */
    public boolean deleteProjectedTransaction(String key, String value) {
        logger.info("Entering deleteProjectedTransaction(): key={}, value={}", key, value);
        if (key == null || value == null) {
            logger.error("Null argument to deleteProjectedTransaction.");
            return false;
        }
        boolean deleted = projectedFileService.delete(key, value);
        logger.info("Projected transaction delete result: {}", deleted);
        return deleted;
    }

    /**
     * Archives the current working statement file and clears it for the next period.
     * The archive is named by statement period.
     * Updates local cache and (in future) statement-period file index.
     * @param newStatementPeriod the new/current period (e.g., "2025-09-13_to_2025-10-12")
     * @return true if archive/rollover succeeded
     */
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

    /**
     * Gets the current statement period string from LocalCacheService.
     * @return current statement period
     */
    public String getCurrentStatementPeriod() {
        logger.info("Entering getCurrentStatementPeriod()");
        String period = localCacheService.getCurrentStatement();
        logger.info("Current statement period: {}", period);
        return period;
    }

    /**
     * Sets the current statement period in LocalCacheService.
     * @param period statement period string
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
     * Gets the file path of the current statement file (main budget CSV) from LocalCacheService.
     * @return file path or null
     */
    public String getCurrentStatementFilePath() {
        logger.info("Entering getCurrentStatementFilePath()");
        String path = localCacheService.getBudgetCsvPath();
        logger.info("Current statement file path: {}", path);
        return path;
    }

    /**
     * Sets the file path for the current statement file (main budget CSV) in LocalCacheService.
     * @param filePath path to file
     */
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
     * Utility: Convert from BudgetRow to BudgetTransaction if needed.
     * Ensures full compatibility with data model for robust analysis.
     */
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

    /**
     * Utility: Convert from BudgetRow to ProjectedTransaction if needed.
     * Ensures full compatibility with data model for projections.
     */
    private ProjectedTransaction convertToProjectedTransaction(BudgetRow row) {
        logger.info("Converting BudgetRow to ProjectedTransaction: {}", row);
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
            // For projections, Statement Period is required
            String statementPeriod = row instanceof ProjectedTransaction
                    ? ((ProjectedTransaction) row).getStatementPeriod()
                    : getCurrentStatementPeriod();
            map.put("Statement Period", statementPeriod);
            ProjectedTransaction tx = ProjectedRowConverter.mapToProjectedTransaction(map);
            logger.info("Converted row: {}", tx);
            return tx;
        } catch (Exception e) {
            logger.error("Failed to convert BudgetRow to ProjectedTransaction: {}", e.getMessage(), e);
            return null;
        }
    }
}