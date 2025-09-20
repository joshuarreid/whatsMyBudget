package app;

import config.BudgetAppConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import service.*;
import ui.FileChooserUtil;
import ui.MainWindow;

import javax.swing.*;

/**
 * app.Main entry point for the Statement-Based Budgeting application.
 * Handles bootstrapping, service initialization, and main UI lifecycle.
 */
public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static String runtimeCsvPath;
    public static String runtimeProjectedCsvPath;

    public static void main(String[] args) {
        logger.info("Application starting...");
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                logger.info("System look and feel set successfully.");
            } catch (Exception e) {
                logger.warn("Failed to set look and feel: {}", e.getMessage());
            }

            LocalCacheService cache = new LocalCacheService();
            logger.info("LocalCacheService initialized.");
            String csvPath = cache.getBudgetCsvPath();

            if (csvPath == null || csvPath.trim().isEmpty()) {
                logger.info("No budget CSV file found; prompting for budget CSV location.");
                String selectedPath = FileChooserUtil.promptForBudgetCsvFile(null);
                if (selectedPath != null && !selectedPath.trim().isEmpty()) {
                    csvPath = selectedPath;
                    cache.setBudgetCsvPath(csvPath);
                } else {
                    logger.warn("User cancelled budget CSV selection. Exiting.");
                    System.exit(0);
                }
            }

            runtimeCsvPath = csvPath;
            runtimeProjectedCsvPath = deriveProjectedCsvPath(csvPath);

            logger.info("Runtime CSV path set for Spring beans: {}", runtimeCsvPath);
            logger.info("Runtime Projected CSV path set for Spring beans: {}", runtimeProjectedCsvPath);

            String lastView = cache.getLastView();
            logger.info("Last view loaded from cache: {}", lastView);

            ApplicationContext context = new AnnotationConfigApplicationContext(BudgetAppConfig.class);
            logger.info("Spring ApplicationContext initialized.");

            BudgetFileService budgetFileService = context.getBean(BudgetFileService.class);
            try {
                budgetFileService.ensureCsvFileReady();
            } catch (Exception ex) {
                logger.error("Failed to ensure budget CSV file is ready: {}", ex.getMessage(), ex);
                JOptionPane.showMessageDialog(null, "Failed to initialize budget CSV: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                System.exit(1);
            }

            // --- CLOUD VALIDATION SECTION ---
            CSVStateService csvStateService = context.getBean(CSVStateService.class);
            logger.info("Validating DigitalOcean Cloud Restore service before launching UI.");
            try {
                if (!csvStateService.validateCloudConnection()) {
                    logger.error("DigitalOcean Cloud Restore service failed validation. Cloud features will be unavailable.");
                    JOptionPane.showMessageDialog(null,
                            "Cloud Restore is not configured correctly or cannot connect to DigitalOcean.\n" +
                                    "Please check your Cloud Restore settings before using backup/sync features.",
                            "Cloud Restore Not Configured", JOptionPane.ERROR_MESSAGE);
                    // Optionally: System.exit(2);
                } else {
                    logger.info("DigitalOcean Cloud Restore service validated successfully.");
                }
            } catch (Exception e) {
                logger.error("Exception during DigitalOcean Cloud Restore validation: {}", e.getMessage(), e);
                JOptionPane.showMessageDialog(null,
                        "An error occurred while validating Cloud Restore:\n" + e.getMessage(),
                        "Cloud Restore Error", JOptionPane.ERROR_MESSAGE);
                // Optionally: System.exit(3);
            }
            // --- END CLOUD VALIDATION SECTION ---

            MainWindow mainWindow = new MainWindow(lastView, false);

            injectServices(mainWindow, context);

            mainWindow.reloadAndRefreshAllPanels();

            mainWindow.setVisible(true);
            logger.info("MainWindow initialized and visible.");
        });
    }

    private static String deriveProjectedCsvPath(String csvPath) {
        if (csvPath == null) return null;
        java.io.File file = new java.io.File(csvPath);
        String parent = file.getParent();
        String projectedName = "projections.csv";
        if (parent != null) {
            return new java.io.File(parent, projectedName).getAbsolutePath();
        } else {
            return projectedName;
        }
    }

    private static void injectServices(MainWindow mainWindow, ApplicationContext context) {
        try {
            CSVStateService csvStateService = context.getBean(CSVStateService.class);
            ImportService importService = context.getBean(ImportService.class);
            LocalCacheService cacheService = context.getBean(LocalCacheService.class);

            mainWindow.setCsvStateService(csvStateService);
            mainWindow.setImportService(importService);
            mainWindow.setCache(cacheService);

            logger.info("Injected CSVStateService, ImportService, and LocalCacheService into MainWindow.");
        } catch (Exception ex) {
            logger.error("Failed to inject Spring beans into MainWindow: {}", ex.getMessage(), ex);
            JOptionPane.showMessageDialog(null, "App startup failed: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            System.exit(2);
        }
    }
}