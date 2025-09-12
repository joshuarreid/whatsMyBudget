package service;

import org.slf4j.Logger;
import org.springframework.stereotype.Service;
import util.AppLogger;

import java.io.*;
import java.util.*;

/**
 * LocalCacheService manages a simple configuration property file in the user's home directory
 * for local caching of app settings.
 */
@Service
public class LocalCacheService {
    private static final Logger logger = AppLogger.getLogger(LocalCacheService.class);
    private static final String APP_DIR = ".whatsmybudget";
    private static final String CONFIG_FILE = "appconfig.properties";

    private final File configFile;
    private final Properties props;

    // Example keys (add more as needed)
    public static final String KEY_LAST_OPEN_BUDGET_FILE = "lastOpenBudgetFile";
    public static final String KEY_LAST_OPEN_PROJECTED_FILE = "lastOpenProjectedFile";
    public static final String KEY_CURRENT_STATEMENT = "currentStatement";
    public static final String KEY_RECENT_BUDGET_FILES = "recentBudgetFiles";
    public static final String KEY_RECENT_PROJECTED_FILES = "recentProjectedFiles";

    public LocalCacheService() {
        File configDir = new File(System.getProperty("user.home"), APP_DIR);
        if (!configDir.exists()) {
            boolean created = configDir.mkdirs();
            if (created) logger.info("Created config directory: {}", configDir.getAbsolutePath());
        }
        configFile = new File(configDir, CONFIG_FILE);
        props = new Properties();
        try {
            if (configFile.exists()) {
                try (FileInputStream in = new FileInputStream(configFile)) {
                    props.load(in);
                    logger.info("Loaded app config from: {}", configFile.getAbsolutePath());
                }
            }
        } catch (IOException e) {
            logger.error("Error loading local cache file: {}", e.getMessage());
        }
    }

    public String get(String key) {
        return props.getProperty(key);
    }

    public void set(String key, String value) {
        props.setProperty(key, value);
        save();
    }

    public String getCurrentStatement() {
        return props.getProperty(KEY_CURRENT_STATEMENT, "");
    }

    public void setCurrentStatement(String statement) {
        props.setProperty(KEY_CURRENT_STATEMENT, statement);
        save();
    }

    public String getLastOpenBudgetFile() {
        return props.getProperty(KEY_LAST_OPEN_BUDGET_FILE, "");
    }

    public void setLastOpenBudgetFile(String path) {
        props.setProperty(KEY_LAST_OPEN_BUDGET_FILE, path);
        save();
    }

    public String getLastOpenProjectedFile() {
        return props.getProperty(KEY_LAST_OPEN_PROJECTED_FILE, "");
    }

    public void setLastOpenProjectedFile(String path) {
        props.setProperty(KEY_LAST_OPEN_PROJECTED_FILE, path);
        save();
    }

    public List<String> getRecentBudgetFiles() {
        String val = props.getProperty(KEY_RECENT_BUDGET_FILES, "");
        if (val.isEmpty()) return new ArrayList<>();
        return new ArrayList<>(Arrays.asList(val.split("\\|")));
    }

    public void addRecentBudgetFile(String path) {
        List<String> recents = getRecentBudgetFiles();
        recents.remove(path);
        recents.add(0, path);
        while (recents.size() > 5) recents.remove(5);
        props.setProperty(KEY_RECENT_BUDGET_FILES, String.join("|", recents));
        save();
    }

    public List<String> getRecentProjectedFiles() {
        String val = props.getProperty(KEY_RECENT_PROJECTED_FILES, "");
        if (val.isEmpty()) return new ArrayList<>();
        return new ArrayList<>(Arrays.asList(val.split("\\|")));
    }

    public void addRecentProjectedFile(String path) {
        List<String> recents = getRecentProjectedFiles();
        recents.remove(path);
        recents.add(0, path);
        while (recents.size() > 5) recents.remove(5);
        props.setProperty(KEY_RECENT_PROJECTED_FILES, String.join("|", recents));
        save();
    }

    private void save() {
        try (FileOutputStream out = new FileOutputStream(configFile)) {
            props.store(out, "WhatsMyBudget Local Cache Config");
            logger.debug("Saved local cache config to: {}", configFile.getAbsolutePath());
        } catch (IOException e) {
            logger.error("Failed to save local cache config: {}", e.getMessage());
        }
    }

    public File getConfigFile() {
        return configFile;
    }
}