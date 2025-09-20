package model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.*;

/**
 * Stores all workspace-local cache/config data for backup and sync.
 * Includes current working file, latest statement period, and any period-file mapping.
 * Serializable and robust to schema evolution.
 */
@Getter
@Setter
public class LocalCacheState implements Serializable {
    private static final Logger logger = LoggerFactory.getLogger(LocalCacheState.class);

    /** Path to the current working budget CSV file */
    private String budgetCsvPath;

    /** Last selected UI view/tab (e.g., "Josh", "Anna", "Joint") */
    private String lastView;

    /** Current active statement period (e.g., "SEPTEMBER2025") */
    private String currentStatementPeriod;

    /** List of recent budget files for quick access */
    private List<String> recentBudgetFiles = new ArrayList<>();

    /** List of recent projections files */
    private List<String> recentProjectedFiles = new ArrayList<>();

    /** List of all known statement periods (used for dropdowns, navigation, etc.) */
    private List<String> statementPeriods = new ArrayList<>();

    /** Mapping of statement period -> archived file name (for fast lookup) */
    private Map<String, String> statementPeriodToFileMap = new HashMap<>();

    /** Other app config as a generic map (future extensibility) */
    private Map<String, String> appConfig = new HashMap<>();

    /** For migration/versioning */
    private String version;

    /**
     * Default constructor ensures all collections are non-null.
     */
    public LocalCacheState() {
        logger.info("Constructing LocalCacheState and initializing collections if null.");
        if (recentBudgetFiles == null) {
            logger.warn("recentBudgetFiles was null in constructor. Initializing as empty list.");
            recentBudgetFiles = new ArrayList<>();
        }
        if (recentProjectedFiles == null) {
            logger.warn("recentProjectedFiles was null in constructor. Initializing as empty list.");
            recentProjectedFiles = new ArrayList<>();
        }
        if (statementPeriods == null) {
            logger.warn("statementPeriods was null in constructor. Initializing as empty list.");
            statementPeriods = new ArrayList<>();
        }
        if (statementPeriodToFileMap == null) {
            logger.warn("statementPeriodToFileMap was null in constructor. Initializing as empty map.");
            statementPeriodToFileMap = new HashMap<>();
        }
        if (appConfig == null) {
            logger.warn("appConfig was null in constructor. Initializing as empty map.");
            appConfig = new HashMap<>();
        }
        logger.info("LocalCacheState constructed with non-null collections.");
    }

    /**
     * Validates this LocalCacheState instance for general app use.
     * Checks presence of required fields and logs the result.
     * @return true if all required fields are present; false otherwise
     */
    public boolean validate() {
        logger.info("Validating LocalCacheState...");
        boolean valid = true;

        if (budgetCsvPath == null || budgetCsvPath.trim().isEmpty()) {
            logger.warn("Validation failed: budgetCsvPath is null or empty.");
            valid = false;
        }
        if (currentStatementPeriod == null || currentStatementPeriod.trim().isEmpty()) {
            logger.warn("Validation failed: currentStatementPeriod is null or empty.");
            valid = false;
        }
        if (recentBudgetFiles == null) {
            logger.warn("Validation failed: recentBudgetFiles is null.");
            valid = false;
        }
        if (recentProjectedFiles == null) {
            logger.warn("Validation failed: recentProjectedFiles is null.");
            valid = false;
        }
        if (statementPeriods == null) {
            logger.warn("Validation failed: statementPeriods is null.");
            valid = false;
        }
        if (statementPeriodToFileMap == null) {
            logger.warn("Validation failed: statementPeriodToFileMap is null.");
            valid = false;
        }
        if (version == null || version.trim().isEmpty()) {
            logger.warn("Validation failed: version is null or empty.");
            valid = false;
        }

        if (valid) {
            logger.info("LocalCacheState validation PASSED.");
        } else {
            logger.error("LocalCacheState validation FAILED.");
        }
        return valid;
    }

    /**
     * Validates this LocalCacheState instance for backup/sync operations.
     * Ensures all fields required for cloud sync are present and non-empty.
     * @return true if valid for backup/sync; false otherwise
     */
    public boolean validateForBackup() {
        logger.info("Validating LocalCacheState for backup/sync...");
        boolean valid = true;

        if (budgetCsvPath == null || budgetCsvPath.trim().isEmpty()) {
            logger.error("Backup validation failed: budgetCsvPath is required.");
            valid = false;
        }
        if (currentStatementPeriod == null || currentStatementPeriod.trim().isEmpty()) {
            logger.error("Backup validation failed: currentStatementPeriod is required.");
            valid = false;
        }
        if (recentBudgetFiles == null || recentBudgetFiles.isEmpty()) {
            logger.error("Backup validation failed: recentBudgetFiles is missing or empty.");
            valid = false;
        }
        if (statementPeriods == null || statementPeriods.isEmpty()) {
            logger.error("Backup validation failed: statementPeriods is missing or empty.");
            valid = false;
        }
        if (version == null || version.trim().isEmpty()) {
            logger.error("Backup validation failed: version is missing or empty.");
            valid = false;
        }

        if (valid) {
            logger.info("LocalCacheState BACKUP validation PASSED.");
        } else {
            logger.error("LocalCacheState BACKUP validation FAILED.");
        }
        return valid;
    }

    @Override
    public String toString() {
        return "LocalCacheState{" +
                "budgetCsvPath='" + budgetCsvPath + '\'' +
                ", lastView='" + lastView + '\'' +
                ", currentStatementPeriod='" + currentStatementPeriod + '\'' +
                ", recentBudgetFiles=" + recentBudgetFiles +
                ", recentProjectedFiles=" + recentProjectedFiles +
                ", statementPeriods=" + statementPeriods +
                ", statementPeriodToFileMap=" + statementPeriodToFileMap +
                ", appConfig=" + appConfig +
                ", version='" + version + '\'' +
                '}';
    }
}