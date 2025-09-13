package ui;

import model.BudgetTransaction;
import model.BudgetTransactionList;
import org.slf4j.Logger;
import util.AppLogger;

import javax.swing.*;
import java.awt.*;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Panel for Anna's view: shows Essential and NonEssential spending by category,
 * and allows weekly breakdown drilldown from category tables.
 * Inherits standard summary panel structure from IndividualViewPanel.
 */
public class AnnaViewPanel extends IndividualViewPanel {
    private static final Logger logger = AppLogger.getLogger(AnnaViewPanel.class);

    private BudgetTransactionList allTransactionList;

    /**
     * Constructs the AnnaViewPanel with category summary panels.
     */
    public AnnaViewPanel() {
        super("Anna");
        logger.info("Initializing AnnaViewPanel");
        setLayout(new GridLayout(2, 1));
        this.add(getEssentialPanel());
        this.add(getNonEssentialPanel());

        // Set up click listeners for both summary tables
        getEssentialPanel().setCategoryRowClickListener(category -> handleCategoryRowClick(category, true));
        getNonEssentialPanel().setCategoryRowClickListener(category -> handleCategoryRowClick(category, false));
    }

    /**
     * Sets the transaction list for this view using a raw list.
     * @param transactions List of BudgetTransaction (may be null or empty)
     */
    public void setTransactions(List<BudgetTransaction> transactions) {
        logger.info("setTransactions(List) called on AnnaViewPanel with {} transaction(s)", transactions == null ? 0 : transactions.size());
        if (transactions != null) {
            this.allTransactionList = new BudgetTransactionList(transactions, "AnnaViewPanel Transactions");
        } else {
            this.allTransactionList = new BudgetTransactionList(null, "AnnaViewPanel Transactions");
        }
        getEssentialPanel().setTransactions(this.allTransactionList);
        getNonEssentialPanel().setTransactions(this.allTransactionList);
    }

    /**
     * Sets the transaction list for this view using a BudgetTransactionList model.
     * @param transactionList BudgetTransactionList (may be null)
     */
    public void setTransactions(BudgetTransactionList transactionList) {
        logger.info("setTransactions(BudgetTransactionList) called on AnnaViewPanel");
        if (transactionList == null) {
            this.allTransactionList = new BudgetTransactionList(null, "AnnaViewPanel Transactions");
            getEssentialPanel().setTransactions((List<BudgetTransaction>) null);
            getNonEssentialPanel().setTransactions((List<BudgetTransaction>) null);
        } else {
            this.allTransactionList = transactionList;
            getEssentialPanel().setTransactions(transactionList);
            getNonEssentialPanel().setTransactions(transactionList);
        }
    }

    /**
     * Handles clicking on a category row in the summary table and displays the weekly breakdown dialog.
     * @param category The clicked category name
     * @param isEssential True if from essentials table, false if from non-essentials
     */
    private void handleCategoryRowClick(String category, boolean isEssential) {
        logger.info("handleCategoryRowClick called for category '{}', isEssential={}", category, isEssential);

        if (allTransactionList == null) {
            logger.error("No transaction list available in AnnaViewPanel for breakdown.");
            JOptionPane.showMessageDialog(this, "No transactions available for breakdown.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        String criticality = isEssential ? "Essential" : "NonEssential";
        List<BudgetTransaction> personalized = allTransactionList.getPersonalizedTransactions("Anna", criticality);
        logger.info("Personalized transaction list for Anna with criticality '{}': {} transactions", criticality, personalized.size());

        // Filter for the selected category
        List<BudgetTransaction> filtered = personalized.stream()
                .filter(tx -> category.equals(tx.getCategory()))
                .collect(Collectors.toList());
        logger.info("Filtered {} personalized transactions for category '{}' (isEssential={})", filtered.size(), category, isEssential);

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