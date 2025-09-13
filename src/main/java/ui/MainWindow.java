package ui;

import model.BudgetTransaction;
import model.BudgetTransactionList;
import org.slf4j.Logger;
import util.AppLogger;
import service.LocalCacheService;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Main window for the Statement-Based Budgeting app.
 * Provides tabs for Josh, Joint, and Anna views.
 */
public class MainWindow extends JFrame {
    private static final Logger logger = AppLogger.getLogger(MainWindow.class);

    private final JoshViewPanel joshViewPanel;
    private final JointViewPanel jointViewPanel;
    private final AnnaViewPanel annaViewPanel;
    private final JTabbedPane tabbedPane;
    private final LocalCacheService cache;

    public MainWindow(String csvPath, String lastView, LocalCacheService cache, boolean firstLaunch) {
        super("Statement-Based Budgeting");
        logger.info("Initializing MainWindow...");
        this.cache = cache;
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(900, 600);
        setLocationRelativeTo(null);

        tabbedPane = new JTabbedPane();

        joshViewPanel = new JoshViewPanel();
        jointViewPanel = new JointViewPanel();
        annaViewPanel = new AnnaViewPanel();

        tabbedPane.addTab("Josh", joshViewPanel);
        tabbedPane.addTab("Joint", jointViewPanel);
        tabbedPane.addTab("Anna", annaViewPanel);

        // Set default or last view
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

        // When tab changes, save last view
        tabbedPane.addChangeListener(e -> {
            String selected = tabbedPane.getTitleAt(tabbedPane.getSelectedIndex());
            cache.setLastView(selected);
            logger.info("Tab changed: now showing '{}'", selected);
        });

        getContentPane().add(tabbedPane, BorderLayout.CENTER);

        // Load transactions
        BudgetTransactionList allTransactions = loadBudgetTransactions(csvPath);

        // Prompt import if not first launch
        if (!firstLaunch) {
            int result = JOptionPane.showConfirmDialog(this,
                    "Do you want to import new transactions?",
                    "Import Transactions", JOptionPane.YES_NO_OPTION);
            if (result == JOptionPane.YES_OPTION) {
                // Implement import logic here (left as TODO)
                logger.info("User chose to import transactions (TODO).");
            }
        }

        joshViewPanel.setTransactions(allTransactions);
        jointViewPanel.setTransactions(allTransactions);
        annaViewPanel.setTransactions(allTransactions);

        logger.info("MainWindow setup complete.");
    }

    /**
     * Loads transactions from the given CSV file.
     */
    private BudgetTransactionList loadBudgetTransactions(String csvPath) {
        logger.info("Loading transactions from file: {}", csvPath);
        List<BudgetTransaction> list = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(csvPath))) {
            String line;
            boolean first = true;
            while ((line = br.readLine()) != null) {
                if (first) { first = false; continue; } // Skip header
                String[] fields = line.split(",", -1);
                if (fields.length < 9) {
                    logger.warn("Skipping malformed CSV line: {}", line);
                    continue;
                }
                String name = fields[0].trim();
                String amount = fields[1].trim();
                String category = fields[2].trim();
                String criticality = fields[3].trim();
                String transactionDate = fields[4].trim();
                String account = fields[5].trim();
                String status = fields[6].trim();
                String createdTime = fields[7].trim();
                String paymentMethod = fields[8].trim();
                String statementPeriod = transactionDate; // For now, use transactionDate as statementPeriod

                list.add(new BudgetTransaction(
                        name, amount, category, criticality, transactionDate,
                        account, status, createdTime, statementPeriod
                ));
            }
            logger.info("Loaded {} transactions from CSV.", list.size());
        } catch (FileNotFoundException e) {
            logger.warn("CSV file not found. Using empty transaction list.");
        } catch (Exception e) {
            logger.error("Error loading transactions: {}", e.getMessage(), e);
            JOptionPane.showMessageDialog(this, "Error loading transactions: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
        return new BudgetTransactionList(list, "Main Budget Transactions");
    }
}