package ui;

import model.BudgetTransaction;
import model.BudgetTransactionList;
import model.ProjectedTransaction;
import org.slf4j.Logger;
import util.AppLogger;

import javax.swing.*;
import java.awt.*;
import java.time.LocalDate;
import java.util.List;
import java.util.Collections;
import java.util.stream.Collectors;

/**
 * Panel for Joint view: shows Essential and NonEssential spending by category for the Joint account,
 * and allows weekly breakdown drilldown from category tables.
 */
public class JointViewPanel extends JPanel {
    private static final Logger logger = AppLogger.getLogger(JointViewPanel.class);
    private final CategorySummaryPanel essentialPanel;
    private final CategorySummaryPanel nonEssentialPanel;
    private BudgetTransactionList allTransactionList;
    private List<ProjectedTransaction> projectedTransactions = Collections.emptyList();

    /**
     * Constructs the JointViewPanel with category summary panels.
     */
    public JointViewPanel() {
        super(new GridLayout(2, 1));
        logger.info("Initializing JointViewPanel");
        essentialPanel = new CategorySummaryPanel("Joint", "Essential");
        nonEssentialPanel = new CategorySummaryPanel("Joint", "NonEssential");
        this.add(essentialPanel);
        this.add(nonEssentialPanel);

        // Set up click listeners for both summary tables
        essentialPanel.setCategoryRowClickListener(category -> handleCategoryRowClick(category, true));
        nonEssentialPanel.setCategoryRowClickListener(category -> handleCategoryRowClick(category, false));
    }

    /**
     * Sets the transaction list for this view using a raw list.
     * @param transactions List of BudgetTransaction (may be null or empty)
     */
    public void setTransactions(List<BudgetTransaction> transactions) {
        logger.info("setTransactions(List) called on JointViewPanel with {} transaction(s)", transactions == null ? 0 : transactions.size());
        if (transactions != null) {
            this.allTransactionList = new BudgetTransactionList(transactions, "JointViewPanel Transactions");
        } else {
            this.allTransactionList = new BudgetTransactionList(null, "JointViewPanel Transactions");
        }
        essentialPanel.setTransactions(this.allTransactionList);
        nonEssentialPanel.setTransactions(this.allTransactionList);
    }

    /**
     * Sets the transaction list for this view using a BudgetTransactionList model.
     * @param transactionList BudgetTransactionList (may be null)
     */
    public void setTransactions(BudgetTransactionList transactionList) {
        logger.info("setTransactions(BudgetTransactionList) called on JointViewPanel");
        if (transactionList == null) {
            this.allTransactionList = new BudgetTransactionList(null, "JointViewPanel Transactions");
            essentialPanel.setTransactions((List<BudgetTransaction>) null);
            nonEssentialPanel.setTransactions((List<BudgetTransaction>) null);
        } else {
            this.allTransactionList = transactionList;
            essentialPanel.setTransactions(transactionList);
            nonEssentialPanel.setTransactions(transactionList);
        }
    }

    /**
     * Sets projected transactions for this view and updates child panels.
     * @param projected List of ProjectedTransaction (may be null or empty)
     */
    public void setProjectedTransactions(List<ProjectedTransaction> projected) {
        logger.info("setProjectedTransactions called on JointViewPanel with {} projected(s)", projected == null ? 0 : projected.size());
        if (projected == null) {
            this.projectedTransactions = Collections.emptyList();
        } else {
            this.projectedTransactions = projected;
        }
        essentialPanel.setProjectedTransactions(projected);
        nonEssentialPanel.setProjectedTransactions(projected);
    }

    /**
     * Handles clicking on a category row in the summary table and displays the weekly breakdown dialog,
     * or the projected transactions dialog if "Projected" is selected.
     * @param category The clicked category name
     * @param isEssential True if from essentials table, false if from non-essentials
     */
    private void handleCategoryRowClick(String category, boolean isEssential) {
        logger.info("handleCategoryRowClick called for category '{}', isEssential={}", category, isEssential);

        if ("Projected".equalsIgnoreCase(category)) {
            logger.info("Projected category clicked in JointViewPanel - dialog handled by CategorySummaryPanel.");
            // Dialog is already handled at the panel level.
            return;
        }

        if (allTransactionList == null) {
            logger.error("No transaction list available in JointViewPanel for breakdown.");
            JOptionPane.showMessageDialog(this, "No transactions available for breakdown.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        String criticality = isEssential ? "Essential" : "NonEssential";
        // Only use Joint account transactions, do NOT split or personalize
        List<BudgetTransaction> filtered = allTransactionList.getByAccountAndCriticality("Joint", criticality)
                .stream()
                .filter(tx -> category.equals(tx.getCategory()))
                .collect(Collectors.toList());
        logger.info("Filtered {} Joint transactions for category '{}' (isEssential={})", filtered.size(), category, isEssential);

        if (filtered.isEmpty()) {
            logger.warn("No transactions found for category '{}' (isEssential={})", category, isEssential);
            JOptionPane.showMessageDialog(this, "No transactions found for this category.", "No Data", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        LocalDate statementStartDate = findStatementStartDate(filtered);
        LocalDate statementEndDate = findStatementEndDate(filtered);

        if (statementStartDate == null || statementEndDate == null) {
            logger.error("Could not determine statement period for breakdown dialog.");
            JOptionPane.showMessageDialog(this, "Could not determine statement period for breakdown.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        WeeklyBreakdownPanel breakdownPanel = new WeeklyBreakdownPanel(
                category,
                filtered,
                statementStartDate,
                statementEndDate
        );

        // Show the breakdown panel in a modal dialog
        JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(this), category + " - Weekly Breakdown", Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.getContentPane().add(breakdownPanel, BorderLayout.CENTER);
        dialog.setSize(500, 400);
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);

        logger.info("Displayed WeeklyBreakdownPanel for category '{}'", category);
    }

    /**
     * Finds the earliest transaction date in the list.
     * @param txs BudgetTransaction list
     * @return LocalDate or null if not found
     */
    private LocalDate findStatementStartDate(List<BudgetTransaction> txs) {
        logger.info("findStatementStartDate called.");
        return txs.stream()
                .map(BudgetTransaction::getDate)
                .filter(d -> d != null)
                .min(LocalDate::compareTo)
                .orElse(null);
    }

    /**
     * Finds the latest transaction date in the list.
     * @param txs BudgetTransaction list
     * @return LocalDate or null if not found
     */
    private LocalDate findStatementEndDate(List<BudgetTransaction> txs) {
        logger.info("findStatementEndDate called.");
        return txs.stream()
                .map(BudgetTransaction::getDate)
                .filter(d -> d != null)
                .max(LocalDate::compareTo)
                .orElse(null);
    }
}