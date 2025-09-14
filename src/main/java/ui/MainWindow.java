package ui;

import model.BudgetTransaction;
import model.BudgetTransactionList;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import util.AppLogger;
import service.CSVStateService;
import service.ImportService;
import service.LocalCacheService;
import util.BudgetRowHashUtil;

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
     * Creates a menu bar with File > Import Transactions, Manage Projected Expenses, Exit.
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

        JMenuItem manageProjectedItem = new JMenuItem("Manage Projected Expenses");
        manageProjectedItem.addActionListener(e -> {
            logger.info("User selected Manage Projected Expenses from menu.");
            handleManageProjectedExpenses();
        });

        JMenuItem exitItem = new JMenuItem("Exit");
        exitItem.addActionListener(e -> {
            logger.info("User selected Exit from menu. Exiting application.");
            System.exit(0);
        });

        fileMenu.add(importItem);
        fileMenu.add(manageProjectedItem);
        fileMenu.addSeparator();
        fileMenu.add(exitItem);
        menuBar.add(fileMenu);

        logger.info("Menu bar created.");
        return menuBar;
    }

    /**
     * Handles the Import Transactions workflow.
     * Prompts user to pick a Notion-exported CSV, parses transactions, checks for duplicates,
     * previews, and imports new transactions and refreshes UI.
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

        // Step 1: Parse CSV to list of BudgetTransaction (do not save yet)
        List<BudgetTransaction> importedTxs;
        try {
            importedTxs = importService.parseFileToBudgetTransactions(importFile);
            logger.info("Parsed {} transactions from file: {}",
                    importedTxs != null ? importedTxs.size() : 0, importFile.getName());
            if (importedTxs == null || importedTxs.isEmpty()) {
                logger.warn("No transactions detected in the selected file: {}", importFile.getName());
                JOptionPane.showMessageDialog(this, "No transactions detected in the selected file.", "Import Warning", JOptionPane.WARNING_MESSAGE);
                return;
            }
        } catch (Exception e) {
            logger.error("Failed to parse CSV: {}", e.getMessage(), e);
            JOptionPane.showMessageDialog(this, "Failed to parse file: " + e.getMessage(), "Import Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // Step 1.5: Check for duplicates against current working file
        try {
            List<BudgetTransaction> existingTxs = csvStateService.getCurrentTransactions();
            Set<String> existingHashes = new HashSet<>();
            if (existingTxs != null) {
                for (BudgetTransaction tx : existingTxs) {
                    String hash = BudgetRowHashUtil.computeTransactionHash(
                            tx.getName(),
                            tx.getAmount(),
                            tx.getCategory(),
                            tx.getCriticality(),
                            tx.getTransactionDate(),
                            tx.getAccount(),
                            tx.getStatus(),
                            tx.getCreatedTime(),
                            tx.getPaymentMethod()
                    );
                    existingHashes.add(hash);
                }
            }
            int duplicateCount = 0;
            for (BudgetTransaction tx : importedTxs) {
                String hash = BudgetRowHashUtil.computeTransactionHash(
                        tx.getName(),
                        tx.getAmount(),
                        tx.getCategory(),
                        tx.getCriticality(),
                        tx.getTransactionDate(),
                        tx.getAccount(),
                        tx.getStatus(),
                        tx.getCreatedTime(),
                        tx.getPaymentMethod()
                );
                boolean isDuplicate = existingHashes.contains(hash);
                tx.setDuplicate(isDuplicate);
                if (isDuplicate) {
                    duplicateCount++;
                }
            }
            logger.info("Duplicate detection complete: {} duplicate(s) found in CSV import.", duplicateCount);
        } catch (Exception e) {
            logger.error("Error during duplicate detection: {}", e.getMessage(), e);
            JOptionPane.showMessageDialog(this, "Error during duplicate detection: " + e.getMessage(), "Import Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // Step 2: Show ImportDialog for user review and confirmation (no statement period dialog)
        ImportDialog importDialog = new ImportDialog(this, importedTxs, (List<BudgetTransaction> confirmedTxs) -> {
            try {
                // Only import non-duplicate transactions
                List<BudgetTransaction> nonDuplicateTxs = confirmedTxs.stream()
                        .filter(tx -> !tx.isDuplicate())
                        .toList();
                csvStateService.saveImportedTransactions(nonDuplicateTxs);
                logger.info("Imported {} transactions to working file (skipped duplicates)", nonDuplicateTxs.size());
                JOptionPane.showMessageDialog(this,
                        "Successfully imported " + nonDuplicateTxs.size() + " new transactions. Duplicates were skipped.",
                        "Import Success", JOptionPane.INFORMATION_MESSAGE);
                showEmptyWarning = true;
                reloadAndRefreshAllPanels();
            } catch (Exception ex) {
                logger.error("Failed to import transactions: {}", ex.getMessage(), ex);
                JOptionPane.showMessageDialog(this, "Failed to import transactions: " + ex.getMessage(), "Import Error", JOptionPane.ERROR_MESSAGE);
            }
        });
        importDialog.setVisible(true);
    }

    /**
     * Handles the Manage Projected Expenses workflow.
     * Opens a dialog that allows the user to view and manage projected expenses by statement period.
     */
    private void handleManageProjectedExpenses() {
        logger.info("Opening Manage Projected Expenses dialog.");
        if (csvStateService == null) {
            logger.error("csvStateService is null in handleManageProjectedExpenses.");
            JOptionPane.showMessageDialog(this, "Application error: Service unavailable.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (cache == null) {
            logger.error("localCacheService is null in handleManageProjectedExpenses.");
            JOptionPane.showMessageDialog(this, "Application error: Local cache service unavailable.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        ManageProjectedExpensesDialog dialog = new ManageProjectedExpensesDialog(
                this,
                csvStateService,
                cache,
                this::reloadAndRefreshAllPanels
        );
        dialog.setVisible(true);
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

            // Use the new data load method to ensure both actuals and projections are loaded per view
            joshViewPanel.loadDataFromStateService(csvStateService);
            jointViewPanel.loadDataFromStateService(csvStateService);
            annaViewPanel.loadDataFromStateService(csvStateService);

            logger.info("All panels refreshed with latest transaction and projections data ({} transactions).", (txs == null ? 0 : txs.size()));
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
    }}