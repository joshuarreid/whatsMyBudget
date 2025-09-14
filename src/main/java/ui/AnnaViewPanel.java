package ui;

import model.BudgetTransaction;
import model.BudgetTransactionList;
import model.ProjectedTransaction;
import org.slf4j.Logger;
import util.AppLogger;
import util.ProjectedTransactionUtil;
import service.CSVStateService;

import javax.swing.*;
import java.awt.*;
import java.time.LocalDate;
import java.util.List;
import java.util.Collections;
import java.util.stream.Collectors;

/**
 * Panel for Anna's view: shows Essential and NonEssential spending by category,
 * and allows weekly breakdown drilldown from category tables.
 * Inherits standard summary panel structure from IndividualViewPanel.
 */
public class AnnaViewPanel extends IndividualViewPanel {
    private static final Logger logger = AppLogger.getLogger(AnnaViewPanel.class);

    private BudgetTransactionList allTransactionList;
    private List<ProjectedTransaction> projectedTransactions = Collections.emptyList();

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
     * Loads all data for this view from the CSVStateService, using the current statement period.
     * This will refresh both real and projected transactions for this account.
     * @param stateService CSVStateService to pull statement period and data from
     */
    public void loadDataFromStateService(CSVStateService stateService) {
        logger.info("loadDataFromStateService called for AnnaViewPanel.");
        if (stateService == null) {
            logger.error("CSVStateService is null, cannot load data.");
            setTransactions((BudgetTransactionList) null);
            setProjectedTransactions(null);
            return;
        }
        String currentPeriod = stateService.getCurrentStatementPeriod();
        logger.info("Retrieved current statement period for Anna: '{}'", currentPeriod);

        List<BudgetTransaction> allTransactions = stateService.getCurrentTransactions();
        logger.info("Retrieved {} current transactions from state service.", allTransactions.size());
        // Filter for Anna's account
        List<BudgetTransaction> filteredTransactions = allTransactions.stream()
                .filter(tx -> "Anna".equalsIgnoreCase(tx.getAccount()))
                .collect(Collectors.toList());
        logger.info("Filtered to {} transactions for account='Anna'.", filteredTransactions.size());
        setTransactions(filteredTransactions);

        // Get all projections for this period
        List<ProjectedTransaction> allProjectionsForPeriod = (currentPeriod == null)
                ? Collections.emptyList()
                : stateService.getProjectedTransactionsForPeriod(currentPeriod);
        logger.info("Retrieved {} projected transactions for period '{}'.", allProjectionsForPeriod.size(), currentPeriod);

        // Use utility to split "Joint" projections for Anna
        List<ProjectedTransaction> personalizedProjections = ProjectedTransactionUtil.splitJointProjectedTransactionsForAccount(allProjectionsForPeriod, "Anna");
        logger.info("After splitting, {} projected transactions for Anna.", personalizedProjections.size());
        setProjectedTransactions(personalizedProjections);
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
     * Sets the projected transactions for this view and updates child panels.
     * @param projected List of ProjectedTransaction (may be null or empty)
     */
    public void setProjectedTransactions(List<ProjectedTransaction> projected) {
        logger.info("setProjectedTransactions called on AnnaViewPanel with {} projected(s)", projected == null ? 0 : projected.size());
        if (projected == null) {
            this.projectedTransactions = Collections.emptyList();
        } else {
            this.projectedTransactions = projected;
        }
        getEssentialPanel().setProjectedTransactions(projected);
        getNonEssentialPanel().setProjectedTransactions(projected);
    }

    /**
     * Handles clicking on a category row in the summary table and displays the weekly breakdown dialog.
     * If "Projected" is clicked, the dialog is handled by the panel.
     * @param category The clicked category name
     * @param isEssential True if from essentials table, false if from non-essentials
     */
    private void handleCategoryRowClick(String category, boolean isEssential) {
        logger.info("handleCategoryRowClick called for category '{}', isEssential={}", category, isEssential);

        if ("Projected".equalsIgnoreCase(category)) {
            logger.info("Projected category clicked in AnnaViewPanel - dialog handled by CategorySummaryPanel.");
            // Dialog is already handled at the panel level.
            return;
        }

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