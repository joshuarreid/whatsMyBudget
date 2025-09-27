package service;

import model.LocalCacheState;
import org.slf4j.Logger;
import org.springframework.stereotype.Service;
import util.AppLogger;

import java.io.*;
import java.util.*;

/**
 * LocalCacheService manages a simple configuration property file in the user's home directory
 * for local caching of app settings.
 *
 * Hybrid approach:
 * - Supports arbitrary key/value pairs for forward extensibility (cloud tokens, custom views, statement mapping, etc.).
 * - Explicit methods for core use cases (working budget file, last view/tab, current statement, recents, statement periods).
 * - Robust logging and error handling.
 * - Ready for future expansion (cross-device sync, per-statement file index, etc.).
 */
@Service
public class LocalCacheService {
    private static final Logger logger = AppLogger.getLogger(LocalCacheService.class);
    private static final String APP_DIR = ".whatsmybudget";
    private static final String CONFIG_FILE = "appconfig.properties";

    private final File configFile;
    private final Properties props;

    // Core keys (extend as needed)
    public static final String KEY_BUDGET_CSV_PATH = "budgetCsvPath";
    public static final String KEY_LAST_VIEW = "lastView";
    public static final String KEY_CURRENT_STATEMENT = "currentStatement";
    public static final String KEY_LAST_OPEN_PROJECTED_FILE = "lastOpenProjectedFile";
    public static final String KEY_RECENT_BUDGET_FILES = "recentBudgetFiles";
    public static final String KEY_RECENT_PROJECTED_FILES = "recentProjectedFiles";
    public static final String KEY_STATEMENT_PERIODS = "statementPeriods";

    /**
     * Initializes the local cache, creating config directory and loading properties.
     */
    public LocalCacheService() {
        logger.info("Initializing LocalCacheService...");
        File configDir = new File(System.getProperty("user.home"), APP_DIR);
        if (!configDir.exists()) {
            boolean created = configDir.mkdirs();
            if (created) {
                logger.info("Created config directory: {}", configDir.getAbsolutePath());
            } else {
                logger.error("Failed to create config directory: {}", configDir.getAbsolutePath());
            }
        }
        configFile = new File(configDir, CONFIG_FILE);
        props = new Properties();
        try {
            if (configFile.exists()) {
                try (FileInputStream in = new FileInputStream(configFile)) {
                    props.load(in);
                    logger.info("Loaded app config from: {}", configFile.getAbsolutePath());
                }
            } else {
                logger.info("Config file does not exist, will create on save: {}", configFile.getAbsolutePath());
            }
        } catch (IOException e) {
            logger.error("Error loading local cache file: {}", e.getMessage(), e);
        }
    }

    // --- Generic Key/Value API for future extensibility ---
    /**
     * Gets a property value by key.
     */
    public String get(String key) {
        String value = props.getProperty(key);
        logger.info("get('{}') -> '{}'", key, value);
        return value;
    }

    /**
     * Sets a property value by key and saves.
     */
    public void set(String key, String value) {
        logger.info("set('{}', '{}')", key, value);
        if (key == null) {
            logger.error("Attempted to set null key in LocalCacheService");
            return;
        }
        if (value == null) value = "";
        props.setProperty(key, value);
        save();
    }

    // --- Explicit API for core settings ---

    /** @return Path to the main budget CSV file (working file) */
    public String getBudgetCsvPath() {
        String path = props.getProperty(KEY_BUDGET_CSV_PATH, "");
        logger.info("getBudgetCsvPath -> '{}'", path);
        return path;
    }

    /** Set path to the main budget CSV file */
    public void setBudgetCsvPath(String path) {
        logger.info("setBudgetCsvPath('{}')", path);
        if (path == null) path = "";
        props.setProperty(KEY_BUDGET_CSV_PATH, path);
        save();
    }

    /** @return Last selected view/tab (e.g., "Josh", "Anna", "Joint") */
    public String getLastView() {
        String view = props.getProperty(KEY_LAST_VIEW, "");
        logger.info("getLastView -> '{}'", view);
        return view;
    }

    /** Store last selected view/tab */
    public void setLastView(String view) {
        logger.info("setLastView('{}')", view);
        if (view == null) view = "";
        props.setProperty(KEY_LAST_VIEW, view);
        save();
    }

    /** @return Current active statement period (may be empty) */
    public String getCurrentStatement() {
        String statement = props.getProperty(KEY_CURRENT_STATEMENT, "");
        logger.info("getCurrentStatement -> '{}'", statement);
        return statement;
    }

    /** Set current statement period */
    public void setCurrentStatement(String statement) {
        logger.info("setCurrentStatement('{}')", statement);
        if (statement == null) statement = "";
        props.setProperty(KEY_CURRENT_STATEMENT, statement);
        save();
    }

    /**
     * Sets the current statement period for the application, logs and validates input.
     * @param period The statement period string to set as current (e.g., "SEPTEMBER2025")
     */
    public void setCurrentStatementPeriod(String period) {
        logger.info("setCurrentStatementPeriod('{}') called.", period);
        if (period == null || period.trim().isEmpty()) {
            logger.warn("Attempted to set blank/null current statement period.");
            props.remove(KEY_CURRENT_STATEMENT);
        } else {
            props.setProperty(KEY_CURRENT_STATEMENT, period.trim());
            logger.info("Set current statement period to '{}'.", period.trim());
        }
        save();
    }

    /**
     * Gets the current statement period from config, logs the result.
     *
     */
    public String getCurrentStatementPeriod() {
        String period = props.getProperty(KEY_CURRENT_STATEMENT, "");
        logger.info("getCurrentStatementPeriod -> '{}'", period);
        return period;
    }


    /** @return List of recent budget files */
    public List<String> getRecentBudgetFiles() {
        String val = props.getProperty(KEY_RECENT_BUDGET_FILES, "");
        logger.info("getRecentBudgetFiles -> '{}'", val);
        if (val.isEmpty()) return new ArrayList<>();
        return new ArrayList<>(Arrays.asList(val.split("\\|")));
    }


    /** @return List of recent projected files */
    public List<String> getRecentProjectedFiles() {
        String val = props.getProperty(KEY_RECENT_PROJECTED_FILES, "");
        logger.info("getRecentProjectedFiles -> '{}'", val);
        if (val.isEmpty()) return new ArrayList<>();
        return new ArrayList<>(Arrays.asList(val.split("\\|")));
    }


    // --- Statement period persistence API ---

    /**
     * Gets all known statement periods, as stored in local config.
     * @return List of statement period strings.
     */
    public List<String> getAllStatementPeriods() {
        String val = props.getProperty(KEY_STATEMENT_PERIODS, "");
        logger.info("getAllStatementPeriods -> '{}'", val);
        if (val.isEmpty()) return new ArrayList<>();
        return new ArrayList<>(Arrays.asList(val.split("\\|")));
    }

    /**
     * Add a statement period to persistent config.
     * Duplicates are not added.
     * @param period Statement period to persist.
     */
    public void addStatementPeriod(String period) {
        logger.info("addStatementPeriod('{}')", period);
        if (period == null || period.isEmpty()) return;
        List<String> periods = getAllStatementPeriods();
        if (!periods.contains(period)) {
            periods.add(period);
            props.setProperty(KEY_STATEMENT_PERIODS, String.join("|", periods));
            save();
            logger.info("Statement period '{}' added to persistent config.", period);
        } else {
            logger.info("Statement period '{}' already present in config.", period);
        }
    }

    /**
     * Saves the properties to the config file.
     */
    private void save() {
        logger.info("Saving config to '{}'", configFile.getAbsolutePath());
        try (FileOutputStream out = new FileOutputStream(configFile)) {
            props.store(out, "WhatsMyBudget Local Cache Config");
            logger.debug("Saved local cache config to: {}", configFile.getAbsolutePath());
        } catch (IOException e) {
            logger.error("Failed to save local cache config: {}", e.getMessage(), e);
        }
    }

    /** @return File object for the config file. */
    public File getConfigFile() {
        logger.info("getConfigFile -> '{}'", configFile.getAbsolutePath());
        return configFile;
    }

    /**
     * Overwrites the local cache config with the values from the given LocalCacheState.
     * All core properties are replaced; unknown properties are preserved.
     * @param state LocalCacheState to apply
     */
    public void setLocalCacheState(LocalCacheState state) {
        logger.info("Entering setLocalCacheState(LocalCacheState) with input: {}", state);
        if (state == null) {
            logger.warn("setLocalCacheState called with null state. Aborting.");
            return;
        }
        props.setProperty(KEY_BUDGET_CSV_PATH, state.getBudgetCsvPath() != null ? state.getBudgetCsvPath() : "");
        props.setProperty(KEY_LAST_VIEW, state.getLastView() != null ? state.getLastView() : "");
        props.setProperty(KEY_CURRENT_STATEMENT, state.getCurrentStatementPeriod() != null ? state.getCurrentStatementPeriod() : "");
        props.setProperty(KEY_RECENT_BUDGET_FILES, state.getRecentBudgetFiles() != null ? String.join("|", state.getRecentBudgetFiles()) : "");
        props.setProperty(KEY_RECENT_PROJECTED_FILES, state.getRecentProjectedFiles() != null ? String.join("|", state.getRecentProjectedFiles()) : "");
        props.setProperty(KEY_STATEMENT_PERIODS, state.getStatementPeriods() != null ? String.join("|", state.getStatementPeriods()) : "");
        // Optionally: handle statementPeriodToFileMap and appConfig
        logger.info("LocalCacheState properties set. Saving to config file.");
        save();
    }

    /**
     * Loads and returns the current LocalCacheState from properties.
     * All fields are loaded from the current config; missing keys default to empty/null as appropriate.
     * @return LocalCacheState representing the current local cache/config state
     */
    public LocalCacheState getLocalCacheState() {
        logger.info("Entering getLocalCacheState()");
        LocalCacheState state = new LocalCacheState();
        try {
            state.setBudgetCsvPath(props.getProperty(KEY_BUDGET_CSV_PATH, ""));
            state.setLastView(props.getProperty(KEY_LAST_VIEW, ""));
            state.setCurrentStatementPeriod(props.getProperty(KEY_CURRENT_STATEMENT, ""));
            String recentBudgetFiles = props.getProperty(KEY_RECENT_BUDGET_FILES, "");
            state.setRecentBudgetFiles(recentBudgetFiles.isEmpty() ? new ArrayList<>() : Arrays.asList(recentBudgetFiles.split("\\|")));
            String recentProjectedFiles = props.getProperty(KEY_RECENT_PROJECTED_FILES, "");
            state.setRecentProjectedFiles(recentProjectedFiles.isEmpty() ? new ArrayList<>() : Arrays.asList(recentProjectedFiles.split("\\|")));
            String statementPeriods = props.getProperty(KEY_STATEMENT_PERIODS, "");
            state.setStatementPeriods(statementPeriods.isEmpty() ? new ArrayList<>() : Arrays.asList(statementPeriods.split("\\|")));
            // Optionally: load statementPeriodToFileMap and appConfig if needed in future
            state.setVersion(props.getProperty("version", "1"));
            logger.info("Loaded LocalCacheState: {}", state);
        } catch (Exception e) {
            logger.error("Error constructing LocalCacheState: {}", e.getMessage(), e);
        }
        return state;
    }


}