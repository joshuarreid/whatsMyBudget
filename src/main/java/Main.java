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
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static String runtimeCsvPath;
    public static String runtimeProjectedCsvPath;

    /**
     * Standard CSV header for a new budget file.
     */
    private static final String BUDGET_CSV_HEADER = "Name,Amount,Category,Criticality,Transaction Date,Account,status,Created time,Payment Method";

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
            File csvFile = (csvPath != null) ? new File(csvPath) : null;

            // Always check if the file exists, not just config presence
            if (csvPath == null || csvPath.trim().isEmpty() || csvFile == null || !csvFile.exists()) {
                logger.info("No budget CSV file found; prompting for budget CSV location.");
                String selectedPath = FileChooserUtil.promptForBudgetCsvFile(null);
                if (selectedPath != null && !selectedPath.trim().isEmpty()) {
                    csvPath = selectedPath;
                    csvFile = new File(csvPath);
                    cache.setBudgetCsvPath(csvPath);
                    try {
                        if (!csvFile.exists()) {
                            boolean created = csvFile.createNewFile();
                            if (created) {
                                logger.info("Created new budget CSV at {}", csvPath);
                                writeCsvHeader(csvFile);
                            } else {
                                logger.warn("Budget CSV file was not created (may already exist): {}", csvPath);
                            }
                        }
                        // Write header if missing or file is empty
                        ensureCsvHeader(csvFile);
                    } catch (Exception ex) {
                        logger.error("Failed to create or initialize budget CSV: {}", ex.getMessage(), ex);
                        JOptionPane.showMessageDialog(null, "Failed to create budget CSV: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                        System.exit(1);
                    }
                } else {
                    logger.warn("User cancelled budget CSV selection. Exiting.");
                    System.exit(0);
                }
            } else {
                // File exists, check for empty or missing header
                ensureCsvHeader(csvFile);
            }

            runtimeCsvPath = csvPath;
            runtimeProjectedCsvPath = deriveProjectedCsvPath(csvPath);

            logger.info("Runtime CSV path set for Spring beans: {}", runtimeCsvPath);
            logger.info("Runtime Projected CSV path set for Spring beans: {}", runtimeProjectedCsvPath);

            String lastView = cache.getLastView();
            logger.info("Last view loaded from cache: {}", lastView);

            ApplicationContext context = new AnnotationConfigApplicationContext(BudgetAppConfig.class);
            logger.info("Spring ApplicationContext initialized.");

            MainWindow mainWindow = new MainWindow(lastView, false);

            injectServices(mainWindow, context);

            mainWindow.reloadAndRefreshAllPanels();

            mainWindow.setVisible(true);
            logger.info("MainWindow initialized and visible.");
        });
    }

    /**
     * Writes the standard CSV header row to the provided file.
     * Logs success and any IO errors encountered.
     *
     * @param file The file to write the header to.
     */
    private static void writeCsvHeader(File file) {
        try (FileWriter writer = new FileWriter(file, false)) {
            writer.write(BUDGET_CSV_HEADER);
            writer.write(System.lineSeparator());
            logger.info("Wrote header row to CSV file: {}", file.getAbsolutePath());
        } catch (IOException e) {
            logger.error("Failed to write header row to CSV file: {}", e.getMessage(), e);
            JOptionPane.showMessageDialog(null,
                    "Failed to write header to new budget CSV: " + e.getMessage(),
                    "File Error", JOptionPane.ERROR_MESSAGE);
            throw new RuntimeException("Failed to write header to new budget CSV", e);
        }
    }

    /**
     * Ensures the CSV file has the correct header row as the first line.
     * If the file is empty or header is missing/invalid, writes the correct header.
     *
     * @param file The CSV file to check and fix.
     */
    private static void ensureCsvHeader(File file) {
        try {
            List<String> lines = Files.readAllLines(file.toPath());
            if (lines.isEmpty()) {
                logger.info("CSV file {} is empty, writing header.", file.getAbsolutePath());
                writeCsvHeader(file);
            } else {
                String firstLine = lines.get(0).trim();
                if (!firstLine.equals(BUDGET_CSV_HEADER)) {
                    logger.warn("CSV file {} missing or invalid header. Rewriting header.", file.getAbsolutePath());
                    // Rewrite: header + rest of file (except old header if present)
                    try (FileWriter writer = new FileWriter(file, false)) {
                        writer.write(BUDGET_CSV_HEADER);
                        writer.write(System.lineSeparator());
                        // Only write body if it wasn't a mistaken header
                        if (!firstLine.isEmpty() && !firstLine.contains(",Amount,")) {
                            for (String line : lines) {
                                writer.write(line);
                                writer.write(System.lineSeparator());
                            }
                        } else if (lines.size() > 1) {
                            for (int i = 1; i < lines.size(); i++) {
                                writer.write(lines.get(i));
                                writer.write(System.lineSeparator());
                            }
                        }
                    }
                    logger.info("CSV file {} header corrected.", file.getAbsolutePath());
                }
            }
        } catch (IOException e) {
            logger.error("Failed to check or write header to CSV file: {}", e.getMessage(), e);
            JOptionPane.showMessageDialog(null,
                    "Failed to check or write header in budget CSV: " + e.getMessage(),
                    "File Error", JOptionPane.ERROR_MESSAGE);
            throw new RuntimeException("Failed to ensure header in budget CSV", e);
        }
    }

    private static String deriveProjectedCsvPath(String csvPath) {
        if (csvPath == null) return null;
        File file = new File(csvPath);
        String parent = file.getParent();
        String projectedName = "projections.csv";
        if (parent != null) {
            return new File(parent, projectedName).getAbsolutePath();
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