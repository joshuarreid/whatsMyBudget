import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.LocalCacheService;
import ui.FileChooserUtil;
import ui.MainWindow;

import javax.swing.*;
import java.awt.*;
import java.io.File;

/**
 * Main application entry point for Statement-Based Budgeting app.
 * Handles first-launch experience, config persistence, and robust file/dialog handling.
 */
public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        logger.info("Application starting...");
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                logger.warn("Failed to set look and feel: {}", e.getMessage());
            }

            LocalCacheService cache = new LocalCacheService();
            String csvPath = cache.getBudgetCsvPath();
            boolean firstLaunch = (csvPath == null || csvPath.trim().isEmpty());

            if (firstLaunch) {
                logger.info("First launch detected; prompting for budget CSV location.");
                String selectedPath = FileChooserUtil.promptForBudgetCsvFile(null);
                if (selectedPath != null && !selectedPath.trim().isEmpty()) {
                    csvPath = selectedPath;
                    cache.setBudgetCsvPath(csvPath);
                    // Create the file if it doesn't exist
                    try {
                        File csvFile = new File(csvPath);
                        if (!csvFile.exists()) {
                            boolean created = csvFile.createNewFile();
                            if (created) {
                                logger.info("Created new budget CSV at {}", csvPath);
                            } else {
                                logger.warn("Budget CSV file was not created (may already exist): {}", csvPath);
                            }
                        }
                    } catch (Exception ex) {
                        logger.error("Failed to create budget CSV: {}", ex.getMessage(), ex);
                        JOptionPane.showMessageDialog(null, "Failed to create budget CSV: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                        System.exit(1);
                    }
                } else {
                    logger.warn("User cancelled budget CSV selection. Exiting.");
                    System.exit(0);
                }
            }

            String lastView = cache.getLastView();
            MainWindow mainWindow = new MainWindow(csvPath, lastView, cache, firstLaunch);
            mainWindow.setVisible(true);
            logger.info("MainWindow initialized and visible.");
        });
    }
}