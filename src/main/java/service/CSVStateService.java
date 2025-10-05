package service;

import model.BudgetRow;
import model.BudgetTransaction;
import model.ProjectedTransaction;
import model.LocalCacheState;
import model.WorkspaceDTO;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import util.AppLogger;
import util.ProjectedRowConverter;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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

    private final BudgetFileService budgetFileService;
    private final ProjectedFileService projectedFileService;
    private final LocalCacheService localCacheService;
    private final DigitalOceanWorkspaceService digitalOceanWorkspaceService;

    public CSVStateService(
            BudgetFileService budgetFileService,
            ProjectedFileService projectedFileService,
            LocalCacheService localCacheService,
            DigitalOceanWorkspaceService digitalOceanWorkspaceService
    ) {
        logger.info("Initializing CSVStateService with provided dependencies.");
        this.budgetFileService = budgetFileService;
        this.projectedFileService = projectedFileService;
        this.localCacheService = localCacheService;
        this.digitalOceanWorkspaceService = digitalOceanWorkspaceService;
        logger.info("CSVStateService initialized.");
    }

    // ========================
    // Cloud Sync Methods
    // ========================

    /**
     * Backs up all app state to the cloud using DigitalOceanWorkspaceService.
     * Gathers current transactions, projections, local cache/config, builds a WorkspaceDTO, and uploads a versioned backup.
     * Logs all actions, errors, and result.
     */
    public void backupToCloud() {
        logger.info("Entering backupToCloud() for cloud sync.");
        try {
            List<BudgetTransaction> budgetTransactions = getCurrentTransactions();
            List<ProjectedTransaction> projectedTransactions = getAllProjectedTransactions();
            LocalCacheState localCacheState = localCacheService.getLocalCacheState();

            WorkspaceDTO workspace = new WorkspaceDTO();
            workspace.setBudgetTransactions(budgetTransactions);
            workspace.setProjectedTransactions(projectedTransactions);
            workspace.setLocalCacheState(localCacheState);
            workspace.setVersion(localCacheState != null ? localCacheState.getVersion() : "1");
            workspace.setLastModified(java.time.Instant.now().toString());
            workspace.updateAllSectionHashes();

            if (digitalOceanWorkspaceService == null) {
                logger.error("DigitalOceanWorkspaceService is not initialized. Cannot perform cloud backup.");
                return;
            }
            digitalOceanWorkspaceService.uploadWorkspaceVersioned(workspace);
            logger.info("Cloud backup completed successfully.");
        } catch (Exception e) {
            logger.error("Cloud backup failed: {}", e.getMessage(), e);
        }
    }

    /**
     * Restores the application state from the latest cloud backup.
     * - Backs up all current local files before applying cloud state.
     * - Validates the downloaded WorkspaceDTO before applying.
     * - Logs every step, user action, and error for traceability.
     * - UI should call lockUI/unlockUI or show/hide progress indicator around this call.
     */
    public void cloudSync() {
        logger.info("User initiated cloudSync() to restore from cloud backup.");

        // Notify UI to lock and show progress (assume UI calls these if available)
        logger.info("Requesting UI to lock with progress indicator for cloud restore.");
        try {
            // 1. Backup local state
            logger.info("Creating local backups before applying cloud restore.");
            String budgetBackupPath = backupFile(budgetFileService.getFilePath(), "budget");
            String projectedBackupPath = backupFile(projectedFileService.getFilePath(), "projections");
            String localCacheBackupPath = backupLocalCache(localCacheService.getLocalCacheState());

            logger.info("Local backups created: budgetBackup='{}', projectionsBackup='{}', localCacheBackup='{}'",
                    budgetBackupPath, projectedBackupPath, localCacheBackupPath);

            // 2. Download from cloud
            logger.info("Downloading WorkspaceDTO from cloud...");
            WorkspaceDTO workspace = digitalOceanWorkspaceService.downloadLatestWorkspaceBackup();

            if (workspace == null) {
                logger.error("No valid WorkspaceDTO found in cloud. Restore aborted. Local files remain unchanged.");
                showErrorDialog("No valid cloud backup found.\nYour local files have NOT been changed.");
                return;
            }

            // 3. Validate cloud data
            logger.info("Validating downloaded WorkspaceDTO...");
            if (!workspace.validate()) {
                logger.error("Downloaded WorkspaceDTO failed validation. Restore aborted. Local files remain unchanged.");
                showErrorDialog("Cloud backup is invalid or corrupt.\nYour local files have NOT been changed.");
                return;
            }

            // 4. Apply to local state
            logger.info("Applying WorkspaceDTO to local state...");
            boolean applied = applyWorkspaceDTO(workspace);
            if (applied) {
                logger.info("Cloud restore applied successfully. UI will be refreshed.");
                showInfoDialog("Workspace successfully restored from cloud backup.");
                // UI should call reloadAndRefreshAllPanels() after this
            } else {
                logger.error("Failed to apply WorkspaceDTO from cloud. Local state unchanged.");
                showErrorDialog("Failed to apply cloud backup.\nYour local files have NOT been changed.");
            }
        } catch (Exception e) {
            logger.error("Exception during cloud restore: {}", e.getMessage(), e);
            showErrorDialog("An error occurred during cloud restore:\n" + e.getMessage() + "\nYour local files have NOT been changed.");
        } finally {
            // Notify UI to unlock/progress done
            logger.info("Cloud restore complete. Requesting UI to unlock and hide progress indicator.");
        }
    }

    /**
     * Backs up the given file to a timestamped copy in the same directory,
     * naming it as originalName_YYYYMMDD_HHmmss.ext (e.g., budget_20250920_021900.csv).
     * Deletes previous backups matching originalName_*.ext before creating a new backup.
     * Returns the backup file path, or null if backup fails.
     */
    private String backupFile(String originalFilePath, String label) {
        logger.info("Backing up file '{}' ({})...", originalFilePath, label);
        if (originalFilePath == null || originalFilePath.trim().isEmpty()) {
            logger.warn("No file path provided for {} backup.", label);
            return null;
        }
        try {
            File original = new File(originalFilePath);
            if (!original.exists()) {
                logger.warn("File '{}' does not exist. Skipping {} backup.", originalFilePath, label);
                return null;
            }
            Path dir = original.toPath().getParent();
            String originalName = original.getName();
            String extension = "";
            int dot = originalName.lastIndexOf('.');
            if (dot != -1) {
                extension = originalName.substring(dot);
                originalName = originalName.substring(0, dot);
            }
            String datePart = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").format(LocalDateTime.now());
            String backupName = originalName + "_" + datePart + extension;
            Path backupPath = dir != null ? dir.resolve(backupName) : Paths.get(backupName);

            // Delete previous backups for this file (pattern: originalName_*.ext)
            final String backupGlob = originalName + "_*" + extension;
            if (dir != null) {
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, backupGlob)) {
                    for (Path path : stream) {
                        try {
                            Files.deleteIfExists(path);
                            logger.info("Deleted previous backup file: '{}'", path.toString());
                        } catch (Exception ex) {
                            logger.warn("Failed to delete previous backup file '{}': {}", path.toString(), ex.getMessage());
                        }
                    }
                } catch (Exception ex) {
                    logger.warn("Failed to list previous backup files for deletion: {}", ex.getMessage());
                }
            }

            Files.copy(original.toPath(), backupPath, StandardCopyOption.REPLACE_EXISTING);
            logger.info("{} file backed up to '{}'.", label, backupPath);
            return backupPath.toString();
        } catch (Exception e) {
            logger.error("Failed to backup {} file '{}': {}", label, originalFilePath, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Serializes and saves the LocalCacheState to a timestamped backup file,
     * naming it as localCacheState_YYYYMMDD_HHmmss.json.
     * Deletes previous backups matching localCacheState_*.json before creating a new backup.
     * Returns the backup file path, or null if backup fails.
     */
    private String backupLocalCache(LocalCacheState cacheState) {
        logger.info("Backing up LocalCacheState...");
        if (cacheState == null) {
            logger.warn("No LocalCacheState provided for backup.");
            return null;
        }
        try {
            String baseName = "localCacheState";
            String extension = ".json";
            String datePart = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").format(LocalDateTime.now());
            String backupFile = baseName + "_" + datePart + extension;
            Path dir = Paths.get(".").toAbsolutePath().normalize();

            // Delete previous local cache backups (pattern: localCacheState_*.json)
            String backupGlob = baseName + "_*" + extension;
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, backupGlob)) {
                for (Path path : stream) {
                    try {
                        Files.deleteIfExists(path);
                        logger.info("Deleted previous LocalCacheState backup: '{}'", path.toString());
                    } catch (Exception ex) {
                        logger.warn("Failed to delete previous LocalCacheState backup '{}': {}", path.toString(), ex.getMessage());
                    }
                }
            } catch (Exception ex) {
                logger.warn("Failed to list previous LocalCacheState backups for deletion: {}", ex.getMessage());
            }

            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            mapper.writeValue(new File(backupFile), cacheState);
            logger.info("LocalCacheState backed up to '{}'.", backupFile);
            return backupFile;
        } catch (Exception e) {
            logger.error("Failed to backup LocalCacheState: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Shows an error dialog to the user (to be implemented by the UI layer).
     */
    private void showErrorDialog(String message) {
        // Example: javax.swing.JOptionPane.showMessageDialog(null, message, "Error", JOptionPane.ERROR_MESSAGE);
        logger.warn("User-facing error dialog: {}", message);
    }

    /**
     * Shows an info dialog to the user (to be implemented by the UI layer).
     */
    private void showInfoDialog(String message) {
        // Example: javax.swing.JOptionPane.showMessageDialog(null, message, "Restore Complete", JOptionPane.INFORMATION_MESSAGE);
        logger.info("User-facing info dialog: {}", message);
    }

    /**
     * Applies a WorkspaceDTO's state to all relevant local services/files.
     * Never overwrites the user's local budgetCsvPath or other file paths with cloud values.
     * Returns true if all sections applied successfully, false otherwise.
     * @param workspace WorkspaceDTO to apply
     * @return true if successful, false otherwise
     */
    private boolean applyWorkspaceDTO(WorkspaceDTO workspace) {
        logger.info("Applying WorkspaceDTO to local state.");
        try {
            if (workspace.getBudgetTransactions() != null) {
                budgetFileService.overwriteAll(workspace.getBudgetTransactions());
                logger.info("Budget transactions applied.");
            }
            if (workspace.getProjectedTransactions() != null) {
                projectedFileService.overwriteAll(workspace.getProjectedTransactions());
                logger.info("Projected transactions applied.");
            }
            if (workspace.getLocalCacheState() != null) {
                LocalCacheState restoredCache = workspace.getLocalCacheState();
                String restoredPath = restoredCache.getBudgetCsvPath();
                String localPath = localCacheService.getLocalCacheState().getBudgetCsvPath();
                logger.info("Restored budgetCsvPath from cloud: '{}'", restoredPath);
                logger.info("Current local budgetCsvPath: '{}'", localPath);

                // Always preserve the local path; never overwrite with cloud value
                if (!Objects.equals(restoredPath, localPath)) {
                    logger.warn("Cloud backup budgetCsvPath ('{}') differs from local ('{}'). Keeping local path.", restoredPath, localPath);
                    restoredCache.setBudgetCsvPath(localPath);
                }
                localCacheService.setLocalCacheState(restoredCache);
                logger.info("Local cache state applied, local file path preserved.");
            }
            return true;
        } catch (Exception e) {
            logger.error("Failed to apply WorkspaceDTO to local state: {}", e.getMessage(), e);
            return false;
        }
    }

    // ========================
    // Projected Transaction API (refactored for robust delegation)
    // ========================

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

    public boolean updateProjectedTransaction(ProjectedTransaction original, ProjectedTransaction updated) {
        logger.info("Entering updateProjectedTransaction(): original={}, updated={}", original, updated);
        if (original == null || updated == null) {
            logger.error("Null argument given to updateProjectedTransaction.");
            return false;
        }
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

    public boolean deleteProjectedTransaction(ProjectedTransaction tx) {
        logger.info("Entering deleteProjectedTransaction(): {}", tx);
        if (tx == null) {
            logger.error("Null argument to deleteProjectedTransaction.");
            return false;
        }
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
    // Statement period & file management
    // ========================

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

    public void setCurrentStatementPeriod(String period) {
        logger.info("Entering setCurrentStatementPeriod(): {}", period);
        if (period == null || period.isEmpty()) {
            logger.error("Attempted to set empty/null statement period.");
            return;
        }
        localCacheService.setCurrentStatement(period);
        logger.info("Set current statement period to '{}'", period);
    }

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

    private BudgetTransaction convertToTransaction(BudgetRow row) {
        logger.info("Converting BudgetRow to BudgetTransaction: {}", row);
        String statementPeriod = null;
        if (row instanceof BudgetTransaction) {
            statementPeriod = ((BudgetTransaction) row).getStatementPeriod();
        }
        if (statementPeriod == null) {
            statementPeriod = "";
        }
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

    /**
     * Validates connectivity to the DigitalOcean Spaces cloud sync service.
     * Calls DigitalOceanWorkspaceService.validateConnection() and logs all results.
     *
     * @return true if the DigitalOcean service is configured and reachable; false otherwise.
     */
    public boolean validateCloudConnection() {
        logger.info("Entering validateCloudConnection() in CSVStateService.");
        if (digitalOceanWorkspaceService == null) {
            logger.error("DigitalOceanWorkspaceService is null in CSVStateService. Cannot validate cloud connection.");
            return false;
        }
        try {
            boolean result = digitalOceanWorkspaceService.validateConnection();
            if (result) {
                logger.info("DigitalOceanWorkspaceService connection validated successfully.");
            } else {
                logger.error("DigitalOceanWorkspaceService failed connection validation.");
            }
            return result;
        } catch (Exception e) {
            logger.error("Exception during cloud connection validation in CSVStateService: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Provides access to the internal DigitalOceanWorkspaceService instance.
     * @return the DigitalOceanWorkspaceService, or null if not initialized.
     */
    public DigitalOceanWorkspaceService getDigitalOceanService() {
        return digitalOceanWorkspaceService;
    }

    /**
     * Provides access to the internal BudgetFileService instance.
     * @return the BudgetFileService, or null if not initialized.
     */
    public BudgetFileService getBudgetFileService() {
        logger.info("getBudgetFileService called.");
        return budgetFileService;
    }

    /**
     * Provides access to the internal ProjectedFileService instance.
     * @return the ProjectedFileService, or null if not initialized.
     */
    public ProjectedFileService getProjectedFileService() {
        logger.info("getProjectedFileService called.");
        return projectedFileService;
    }

    /**
     * Provides access to the internal LocalCacheService instance.
     * @return the LocalCacheService, or null if not initialized.
     */
    public LocalCacheService getLocalCacheService() {
        logger.info("getLocalCacheService called.");
        return localCacheService;
    }

}