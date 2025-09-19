package ui;

import org.slf4j.Logger;
import util.AppLogger;

import javax.swing.*;
import java.awt.*;
import java.io.File;

/**
 * Utility for robust cross-platform file selection (including iCloud on macOS).
 */
public class FileChooserUtil {
    private static final Logger logger = AppLogger.getLogger(FileChooserUtil.class);

    /**
     * Prompts the user to select a file location for the main budget CSV, with iCloud Drive support on macOS.
     * @param parentComponent the parent component for dialog
     * @return The selected file path, or null if cancelled.
     */
    public static String promptForBudgetCsvFile(Component parentComponent) {
        logger.info("Prompting user to select location for main budget CSV.");
        String osName = System.getProperty("os.name").toLowerCase();
        String selectedPath = null;
        try {
            if (osName.contains("mac")) {
                logger.info("Detected macOS, using AWT native FileDialog for iCloud support.");
                FileDialog dialog = new FileDialog((Frame) null, "Select or Create Budget CSV", FileDialog.SAVE);
                dialog.setFilenameFilter((dir, name) -> name.endsWith(".csv") || !name.contains("."));
                dialog.setVisible(true);
                String dir = dialog.getDirectory();
                String file = dialog.getFile();
                if (dir != null && file != null) {
                    selectedPath = new File(dir, file).getAbsolutePath();
                    logger.info("User selected file: {}", selectedPath);
                } else {
                    logger.info("User cancelled file selection.");
                }
            } else {
                logger.info("Using JFileChooser for non-macOS platform.");
                JFileChooser chooser = new JFileChooser();
                chooser.setDialogTitle("Select location for main budget CSV");
                chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
                chooser.setSelectedFile(new File("budget.csv"));
                int userSelection = chooser.showSaveDialog(parentComponent);
                if (userSelection == JFileChooser.APPROVE_OPTION) {
                    selectedPath = chooser.getSelectedFile().getAbsolutePath();
                    logger.info("User selected file: {}", selectedPath);
                } else {
                    logger.info("User cancelled file selection.");
                }
            }
        } catch (Exception e) {
            logger.error("Error during file selection: {}", e.getMessage(), e);
            JOptionPane.showMessageDialog(parentComponent,
                    "Error selecting file: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
        return selectedPath;
    }
}