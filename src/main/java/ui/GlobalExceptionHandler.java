package ui;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;

/**
 * GlobalExceptionHandler catches all uncaught exceptions and displays an error dialog using ErrorDialogUtil.
 * It also logs all exceptions robustly.
 */
public class GlobalExceptionHandler implements Thread.UncaughtExceptionHandler {
    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private final Component parentComponent;

    /**
     * Constructs a GlobalExceptionHandler.
     * @param parentComponent the parent component for error dialogs (may be null)
     */
    public GlobalExceptionHandler(Component parentComponent) {
        this.parentComponent = parentComponent;
    }

    /**
     * Handles any uncaught exception by logging and showing an error dialog.
     */
    @Override
    public void uncaughtException(Thread t, Throwable e) {
        logger.error("Uncaught exception in thread '{}': {}", t.getName(), e.getMessage(), e);
        String message = "An unexpected error occurred:\n" + e.toString();
        ErrorDialogUtil.showErrorDialog(parentComponent, message, e);
    }

    /**
     * Registers this handler as the global exception handler for all threads.
     * Also installs a handler for the Swing Event Dispatch Thread (EDT).
     */
    public void register() {
        Thread.setDefaultUncaughtExceptionHandler(this);

        // For Swing: Install handler for the EDT
        System.setProperty("sun.awt.exception.handler", GlobalEDTExceptionCatcher.class.getName());

        // Alternative for modern Java:
        try {
            // Use a proxy for EDT exceptions
            Runnable install = () -> {
                Thread.currentThread().setUncaughtExceptionHandler(this);
            };
            SwingUtilities.invokeLater(install);
            logger.info("Registered GlobalExceptionHandler for all threads and EDT.");
        } catch (Exception ex) {
            logger.error("Failed to register GlobalExceptionHandler for EDT: {}", ex.getMessage(), ex);
        }
    }

    /**
     * Helper for legacy Java to catch EDT exceptions.
     */
    public static class GlobalEDTExceptionCatcher {
        public void handle(Throwable thrown) {
            new GlobalExceptionHandler(null).uncaughtException(Thread.currentThread(), thrown);
        }
    }
}