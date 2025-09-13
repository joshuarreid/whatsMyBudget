package ui;

import model.BudgetTransaction;
import model.BudgetTransactionList;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import util.AppLogger;
import service.CSVStateService;
import service.ImportService;
import service.LocalCacheService;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Main window for the Statement-Based Budgeting app.
 * Provides tabs for Josh, Joint, and Anna views, with a File menu for importing transactions and exiting.
 *
 * Spring @Autowired is used for all service dependencies.
 * Only runtime parameters (lastView, firstLaunch) are passed in the constructor.
 */
public class MainWindow extends JFrame {
    private static final Logger logger = AppLogger.getLogger(MainWindow.class);

    @Autowired
    private CSVStateService csvStateService;

    @Autowired
    private LocalCacheService cache;

    @Autowired
    private ImportService importService;

    private final JoshViewPanel joshViewPanel;
    private final JointViewPanel jointViewPanel;
    private final AnnaViewPanel annaViewPanel;
    private final JTabbedPane tabbedPane;
    private final String lastView;
    private final boolean firstLaunch;

    /**
     * Flag to control whether to show warning for empty transactions.
     * This should be true only after a user import action.
     */
    private boolean showEmptyWarning = false;

    /**
     * Constructs the main window with all views and the menu bar.
     * Services are injected via Spring @Autowired.
     * Only runtime UI parameters are passed in the constructor.
     *
     * @param lastView Last selected view/tab.
     * @param firstLaunch Whether this is the first launch.
     */
    public MainWindow(String lastView, boolean firstLaunch) {
        super("Statement-Based Budgeting");
        logger.info("Initializing MainWindow. lastView={}, firstLaunch={}", lastView, firstLaunch);
        this.lastView = lastView;
        this.firstLaunch = firstLaunch;

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(900, 600);
        setLocationRelativeTo(null);

        setJMenuBar(createMenuBar());

        tabbedPane = new JTabbedPane();

        joshViewPanel = new JoshViewPanel();
        jointViewPanel = new JointViewPanel();
        annaViewPanel = new AnnaViewPanel();

        tabbedPane.addTab("Josh", joshViewPanel);
        tabbedPane.addTab("Joint", jointViewPanel);
        tabbedPane.addTab("Anna", annaViewPanel);

        int defaultTab = 1; // Joint
        if (lastView != null) {
            switch (lastView) {
                case "Josh": defaultTab = 0; break;
                case "Joint": defaultTab = 1; break;
                case "Anna": defaultTab = 2; break;
                default: break;
            }
        }
        tabbedPane.setSelectedIndex(defaultTab);
        logger.info("Default/last view is tab index {}", defaultTab);

        tabbedPane.addChangeListener(e -> {
            String selected = tabbedPane.getTitleAt(tabbedPane.getSelectedIndex());
            if (cache != null) {
                cache.setLastView(selected);
            } else {
                logger.error("Cache service is null when trying to set last view.");
            }
            logger.info("Tab changed: now showing '{}'", selected);
        });

        getContentPane().add(tabbedPane, BorderLayout.CENTER);

        logger.info("MainWindow setup complete.");
    }

    /**
     * Injects the CSVStateService dependency.
     * @param csvStateService CSVStateService to inject
     */
    public void setCsvStateService(CSVStateService csvStateService) {
        logger.info("Injecting CSVStateService into MainWindow.");
        if (csvStateService == null) {
            logger.error("Injected CSVStateService is null!");
        }
        this.csvStateService = csvStateService;
    }

    /**
     * Injects the ImportService dependency.
     * @param importService ImportService to inject
     */
    public void setImportService(ImportService importService) {
        logger.info("Injecting ImportService into MainWindow.");
        if (importService == null) {
            logger.error("Injected ImportService is null!");
        }
        this.importService = importService;
    }

    /**
     * Injects the LocalCacheService dependency.
     * @param cache LocalCacheService to inject
     */
    public void setCache(LocalCacheService cache) {
        logger.info("Injecting LocalCacheService into MainWindow.");
        if (cache == null) {
            logger.error("Injected LocalCacheService is null!");
        }
        this.cache = cache;
    }

    /**
     * Creates a menu bar with File > Import Transactions, Exit.
     */
    private JMenuBar createMenuBar() {
        logger.info("Creating menu bar.");
        JMenuBar menuBar = new JMenuBar();

        JMenu fileMenu = new JMenu("File");

        JMenuItem importItem = new JMenuItem("Import Transactions");
        importItem.addActionListener(e -> {
            logger.info("User selected Import Transactions from menu.");
            handleImportTransactions();
        });

        JMenuItem exitItem = new JMenuItem("Exit");
        exitItem.addActionListener(e -> {
            logger.info("User selected Exit from menu. Exiting application.");
            System.exit(0);
        });

        fileMenu.add(importItem);
        fileMenu.addSeparator();
        fileMenu.add(exitItem);
        menuBar.add(fileMenu);

        logger.info("Menu bar created.");
        return menuBar;
    }

    /**
     * Handles the Import Transactions workflow.
     * Prompts user to pick a Notion-exported CSV, delegates parsing and import to ImportService, and updates UI.
     */
    private void handleImportTransactions() {
        logger.info("Starting import transactions workflow.");
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select Notion-exported CSV to Import");
        int result = chooser.showOpenDialog(this);
        if (result != JFileChooser.APPROVE_OPTION) {
            logger.info("User cancelled import transaction file chooser.");
            return;
        }
        File importFile = chooser.getSelectedFile();
        if (importFile == null || !importFile.exists()) {
            logger.error("Selected file does not exist: {}", importFile);
            JOptionPane.showMessageDialog(this, "Selected file does not exist.", "Import Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        logger.info("Importing transactions from file: {}", importFile.getAbsolutePath());

        if (csvStateService == null || importService == null) {
            logger.error("csvStateService or importService is null in handleImportTransactions.");
            JOptionPane.showMessageDialog(this, "Application error: Service unavailable.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        String workingCsvPath = csvStateService.getCurrentStatementFilePath();
        ImportService.ImportResult importResult = importService.importTransactions(importFile, workingCsvPath);

        // Updated: Use new import summary for display
        String summary = importResult.getSummary();
        StringBuilder msg = new StringBuilder();
        msg.append("Import complete!\n").append(summary);
        if (importResult.errorCount > 0 && !importResult.errorLines.isEmpty()) {
            msg.append("\nFirst error line: \n").append(importResult.errorLines.get(0));
        }
        JOptionPane.showMessageDialog(this, msg.toString(), "Import Results",
                (importResult.errorCount == 0 && importResult.duplicateCount == 0)
                        ? JOptionPane.INFORMATION_MESSAGE
                        : JOptionPane.WARNING_MESSAGE);

        logger.info("ImportService returned: detectedCount={}, importedCount={}, duplicateCount={}, errorCount={}",
                importResult.detectedCount, importResult.importedCount, importResult.duplicateCount, importResult.errorCount);

        // Set flag so next reload will warn if still empty
        showEmptyWarning = true;

        // After import, reload file and refresh panels
        reloadAndRefreshAllPanels();
    }

    /**
     * Reloads transactions from the current working file (via CSVStateService) and refreshes all view panels.
     * If loading fails, shows a user-facing dialog with the error details and guidance.
     * Also warns if the file is empty or only contains duplicates, but only after an import/user action.
     */
    public void reloadAndRefreshAllPanels() {
        logger.info("Reloading transactions and refreshing all UI panels.");
        if (csvStateService == null) {
            logger.error("csvStateService is null in reloadAndRefreshAllPanels.");
            JOptionPane.showMessageDialog(this, "Application error: Cannot load transactions.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        try {
            List<BudgetTransaction> txs = csvStateService.getCurrentTransactions();
            if ((txs == null || txs.isEmpty()) && showEmptyWarning) {
                logger.warn("No transactions loaded from the CSV; the file may be empty or malformed.");
                String csvPath = null;
                try {
                    csvPath = csvStateService.getCurrentStatementFilePath();
                } catch (Exception inner) {
                    logger.error("Failed to retrieve CSV path: {}", inner.getMessage(), inner);
                }
                StringBuilder msg = new StringBuilder();
                msg.append("No transactions were loaded from your budget CSV file");
                if (csvPath != null) {
                    msg.append(":\n").append(csvPath);
                }
                msg.append("\n\nPossible reasons:\n- The file is empty\n- The file contains only data rows (no header)\n- All rows are malformed or skipped\n\n");
                msg.append("Please check your CSV for proper formatting, including a header row, correct column count, and no extra blank lines.");
                JOptionPane.showMessageDialog(this, msg.toString(), "No Transactions Found", JOptionPane.WARNING_MESSAGE);
            }

            // Warn if there appear to be duplicate rows
            Set<String> seenRows = new HashSet<>();
            boolean hasDuplicates = false;
            if (txs != null) {
                for (BudgetTransaction tx : txs) {
                    String key = tx.toString(); // Consider a more precise key for production
                    if (!seenRows.add(key)) {
                        hasDuplicates = true;
                    }
                }
            }
            if (hasDuplicates) {
                logger.warn("Duplicate transactions detected in CSV.");
                JOptionPane.showMessageDialog(this,
                        "Warning: Duplicate transactions detected in your CSV.\n\n" +
                                "If you are repeatedly importing the same file, consider clearing or deduplicating it to avoid double counting.",
                        "Duplicate Transactions Detected",
                        JOptionPane.WARNING_MESSAGE);
            }

            BudgetTransactionList allTransactions = new BudgetTransactionList(txs, "Main Budget Transactions");
            joshViewPanel.setTransactions(allTransactions);
            jointViewPanel.setTransactions(allTransactions);
            annaViewPanel.setTransactions(allTransactions);

            logger.info("All panels refreshed with latest transaction data ({} transactions).", (txs == null ? 0 : txs.size()));
        } catch (Exception e) {
            logger.error("Failed to reload and refresh panels: {}", e.getMessage(), e);
            String csvPath = null;
            try {
                csvPath = csvStateService.getCurrentStatementFilePath();
            } catch (Exception inner) {
                logger.error("Failed to retrieve CSV path: {}", inner.getMessage(), inner);
            }
            StringBuilder msg = new StringBuilder();
            msg.append("Failed to load your budget CSV file");
            if (csvPath != null) {
                msg.append(":\n").append(csvPath);
            }
            msg.append("\n\nError: ").append(e.getMessage());
            msg.append("\n\nPlease check your CSV for formatting problems (extra/missing columns, header mismatch, blank lines, or invisible characters).");
            msg.append("\n\nIf the problem persists, try opening the CSV in a plain text editor and verify every row matches the header column count.");
            JOptionPane.showMessageDialog(this, msg.toString(), "Budget CSV Load Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}