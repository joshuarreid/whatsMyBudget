import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import service.BudgetFileService;
import service.CSVStateService;
import service.ImportService;
import service.LocalCacheService;
import service.ProjectedFileService;
import ui.FileChooserUtil;
import ui.MainWindow;

import javax.swing.*;

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

            // Always check if the file exists, not just config presence
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

            // Ensure the budget CSV file is present and valid (header, etc.)
            BudgetFileService budgetFileService = context.getBean(BudgetFileService.class);
            try {
                budgetFileService.ensureCsvFileReady();
            } catch (Exception ex) {
                logger.error("Failed to ensure budget CSV file is ready: {}", ex.getMessage(), ex);
                JOptionPane.showMessageDialog(null, "Failed to initialize budget CSV: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                System.exit(1);
            }

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

    @Configuration
    public static class BudgetAppConfig {
        @Bean
        public LocalCacheService localCacheService() {
            return new LocalCacheService();
        }

        @Bean
        public BudgetFileService budgetFileService() {
            logger.info("Creating BudgetFileService bean with path: {}", Main.runtimeCsvPath);
            return new BudgetFileService(Main.runtimeCsvPath);
        }

        @Bean
        public ProjectedFileService projectedFileService() {
            logger.info("Creating ProjectedFileService bean with path: {}", Main.runtimeProjectedCsvPath);
            return new ProjectedFileService(Main.runtimeProjectedCsvPath);
        }

        @Bean
        public ImportService importService() {
            return new ImportService();
        }

        @Bean
        public CSVStateService csvStateService() {
            return new CSVStateService();
        }
    }
}