package ui;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;

/**
 * Utility for displaying error dialogs with recursion protection.
 */
public class ErrorDialogUtil {
    private static final Logger logger = LoggerFactory.getLogger(ErrorDialogUtil.class);
    private static final ThreadLocal<Boolean> showingErrorDialog = ThreadLocal.withInitial(() -> false);

    /**
     * Displays an error dialog with the given message and logs the error.
     * If already showing an error dialog, logs the recursion and does not show another.
     * @param parent the parent component for the dialog (can be null)
     * @param message the error message to display
     * @param error the exception (can be null)
     */
    public static void showErrorDialog(Component parent, String message, Throwable error) {
        if (Boolean.TRUE.equals(showingErrorDialog.get())) {
            logger.error("Recursive error dialog suppressed: {}", message, error);
            return;
        }
        showingErrorDialog.set(true);
        try {
            logger.error("Error occurred: {}", message, error);
            JOptionPane.showMessageDialog(
                    parent,
                    message + (error != null ? "\n\n" + error.getMessage() : ""),
                    "Error",
                    JOptionPane.ERROR_MESSAGE
            );
        } catch (Exception e) {
            logger.error("Exception while showing error dialog: {}", e.getMessage(), e);
        } finally {
            showingErrorDialog.set(false);
        }
    }
}