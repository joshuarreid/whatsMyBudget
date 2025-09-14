package service;

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
 * - Explicit methods for core use cases (working budget file, last view/tab, current statement, recents).
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
    // For future: cloud sync tokens, statement file index, etc.

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

    /** @return Path to last open projected file */
    public String getLastOpenProjectedFile() {
        String path = props.getProperty(KEY_LAST_OPEN_PROJECTED_FILE, "");
        logger.info("getLastOpenProjectedFile -> '{}'", path);
        return path;
    }

    /** Store path to last open projected file */
    public void setLastOpenProjectedFile(String path) {
        logger.info("setLastOpenProjectedFile('{}')", path);
        if (path == null) path = "";
        props.setProperty(KEY_LAST_OPEN_PROJECTED_FILE, path);
        save();
    }

    /** @return List of recent budget files */
    public List<String> getRecentBudgetFiles() {
        String val = props.getProperty(KEY_RECENT_BUDGET_FILES, "");
        logger.info("getRecentBudgetFiles -> '{}'", val);
        if (val.isEmpty()) return new ArrayList<>();
        return new ArrayList<>(Arrays.asList(val.split("\\|")));
    }

    /** Add a file to recent budget files, keeping max 5 */
    public void addRecentBudgetFile(String path) {
        logger.info("addRecentBudgetFile('{}')", path);
        if (path == null || path.isEmpty()) return;
        List<String> recents = getRecentBudgetFiles();
        recents.remove(path);
        recents.add(0, path);
        while (recents.size() > 5) recents.remove(5);
        props.setProperty(KEY_RECENT_BUDGET_FILES, String.join("|", recents));
        save();
    }

    /** @return List of recent projected files */
    public List<String> getRecentProjectedFiles() {
        String val = props.getProperty(KEY_RECENT_PROJECTED_FILES, "");
        logger.info("getRecentProjectedFiles -> '{}'", val);
        if (val.isEmpty()) return new ArrayList<>();
        return new ArrayList<>(Arrays.asList(val.split("\\|")));
    }

    /** Add a file to recent projected files, keeping max 5 */
    public void addRecentProjectedFile(String path) {
        logger.info("addRecentProjectedFile('{}')", path);
        if (path == null || path.isEmpty()) return;
        List<String> recents = getRecentProjectedFiles();
        recents.remove(path);
        recents.add(0, path);
        while (recents.size() > 5) recents.remove(5);
        props.setProperty(KEY_RECENT_PROJECTED_FILES, String.join("|", recents));
        save();
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
}