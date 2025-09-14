package ui;

import model.BudgetTransaction;
import model.BudgetTransactionList;
import model.ProjectedTransaction;
import org.slf4j.Logger;
import util.AppLogger;
import service.CSVStateService;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Panel that displays total spending by category for a given account and criticality,
 * including projected expenses as a highlighted "Projected" category (blue row).
 * Projections shown are those passed in via setProjectedTransactions, which
 * must be pre-filtered for the currently selected statement period.
 * Robust to null/empty input, includes full logging, and is ready for future drilldown.
 */
public class CategorySummaryPanel extends JPanel {
    private final Logger logger = AppLogger.getLogger(getClass());
    private String account;
    private String criticality;
    private final JTable table;
    private final DefaultTableModel tableModel;
    private Consumer<String> categoryRowClickListener;
    /**
     * The list of projected transactions to display. Must already be filtered by statement period.
     */
    private List<ProjectedTransaction> projectedTransactions = Collections.emptyList();

    /**
     * Constructs a summary panel for a specific account and criticality.
     * @param account     Account to filter on (e.g., "Josh", "Anna", "Joint")
     * @param criticality Criticality to filter on ("Essential" or "NonEssential")
     */
    public CategorySummaryPanel(String account, String criticality) {
        super(new BorderLayout());
        logger.info("CategorySummaryPanel created for account='{}', criticality='{}'", account, criticality);
        this.account = account;
        this.criticality = criticality;
        this.tableModel = new DefaultTableModel(new String[]{"Category", "Total Amount"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };
        this.table = new JTable(tableModel);
        this.table.setDefaultRenderer(Object.class, new ProjectedCategoryCellRenderer());
        this.add(new JScrollPane(table), BorderLayout.CENTER);
        setupTableRowClickListener();
    }

    /**
     * Loads all data for this summary panel from the CSVStateService, using the current statement period.
     * This will refresh both real and projected transactions for this account/criticality.
     * @param stateService CSVStateService to pull statement period and data from
     */
    public void loadDataFromStateService(CSVStateService stateService) {
        logger.info("loadDataFromStateService called for account='{}', criticality='{}'", account, criticality);
        if (stateService == null) {
            logger.error("CSVStateService is null, cannot load data.");
            setTransactions((List<BudgetTransaction>) null);
            setProjectedTransactions(null);
            return;
        }
        String currentPeriod = stateService.getCurrentStatementPeriod();
        logger.info("Retrieved current statement period: '{}'", currentPeriod);

        List<BudgetTransaction> allTransactions = stateService.getCurrentTransactions();
        logger.info("Retrieved {} current transactions from state service.", allTransactions.size());
        // Filter for account and criticality
        List<BudgetTransaction> filteredTransactions = allTransactions.stream()
                .filter(tx -> account == null || account.equalsIgnoreCase(tx.getAccount()))
                .filter(tx -> criticality == null || criticality.equalsIgnoreCase(tx.getCriticality()))
                .collect(Collectors.toList());
        logger.info("Filtered to {} transactions for account='{}', criticality='{}'.", filteredTransactions.size(), account, criticality);
        setTransactions(filteredTransactions);

        List<ProjectedTransaction> projections = (currentPeriod == null)
                ? Collections.emptyList()
                : stateService.getProjectedTransactionsForPeriod(currentPeriod).stream()
                .filter(tx -> account == null || account.equalsIgnoreCase(tx.getAccount()))
                .filter(tx -> criticality == null || criticality.equalsIgnoreCase(tx.getCriticality()))
                .collect(Collectors.toList());
        logger.info("Filtered to {} projected transactions for account='{}', criticality='{}'.", projections.size(), account, criticality);
        setProjectedTransactions(projections);
    }

    /**
     * Registers a listener that is called when a category row is clicked.
     * @param listener Consumer receiving the clicked category name (String)
     */
    public void setCategoryRowClickListener(Consumer<String> listener) {
        logger.info("setCategoryRowClickListener called.");
        this.categoryRowClickListener = listener;
    }

    /**
     * Sets the projected transactions list to be displayed as the "Projected" category.
     * This list MUST already be filtered for the current statement period.
     * Triggers a table refresh.
     * @param projected List of ProjectedTransaction (must be for the current statement period)
     */
    public void setProjectedTransactions(List<ProjectedTransaction> projected) {
        logger.info("setProjectedTransactions called with {} projected(s) for account '{}', criticality '{}'. "
                        + "Input must be filtered for the current statement period.",
                projected == null ? 0 : projected.size(), account, criticality);
        this.projectedTransactions = (projected == null) ? Collections.emptyList() : projected;
        logger.info("Set {} projected transactions for display.", this.projectedTransactions.size());
        refreshTable();
    }

    /**
     * Sets transactions to summarize and display by category, including a totals row at the bottom.
     * Also adds a "Projected" row if projected transactions are present.
     * Triggers a table refresh.
     * @param transactions List of BudgetTransaction (may be null or empty)
     */
    public void setTransactions(List<BudgetTransaction> transactions) {
        logger.info("setTransactions called with {} transaction(s) for account '{}', criticality '{}'",
                transactions == null ? 0 : transactions.size(), account, criticality);
        this.lastTransactions = transactions;
        refreshTable();
    }

    /**
     * Convenient overload that takes a BudgetTransactionList and uses its filtering.
     * @param transactionList The BudgetTransactionList to summarize
     */
    public void setTransactions(BudgetTransactionList transactionList) {
        logger.info("setTransactions(BudgetTransactionList) called for account='{}', criticality='{}'", account, criticality);
        if (transactionList == null) {
            logger.warn("Null BudgetTransactionList provided to setTransactions; clearing table.");
            setTransactions((List<BudgetTransaction>) null);
            return;
        }
        setTransactions(transactionList.getByAccountAndCriticality(account, criticality));
    }

    // Store the last base transactions to enable projection refresh
    private List<BudgetTransaction> lastTransactions = null;

    /**
     * Recomputes and repopulates the table rows using the last provided transactions and projections.
     * This ensures the table updates when either setTransactions or setProjectedTransactions is called.
     */
    private void refreshTable() {
        logger.info("refreshTable called for account '{}', criticality '{}'", account, criticality);
        tableModel.setRowCount(0);
        // Filter real transactions
        List<BudgetTransaction> filtered = (lastTransactions == null) ? Collections.emptyList() :
                lastTransactions.stream()
                        .filter(tx -> (account == null || account.equalsIgnoreCase(tx.getAccount())))
                        .filter(tx -> (criticality == null || criticality.equalsIgnoreCase(tx.getCriticality())))
                        .collect(Collectors.toList());
        logger.info("Filtered to {} base transactions for summary.", filtered.size());
        Map<String, Double> totalsByCategory = filtered.stream()
                .collect(Collectors.groupingBy(
                        tx -> tx.getCategory() == null ? "(Uncategorized)" : tx.getCategory(),
                        Collectors.summingDouble(BudgetTransaction::getAmountValue)
                ));
        logger.info("Aggregated spending into {} categories.", totalsByCategory.size());

        double grandTotal = 0.0;
        for (Map.Entry<String, Double> entry : totalsByCategory.entrySet()) {
            tableModel.addRow(new Object[]{entry.getKey(), String.format("$%.2f", entry.getValue())});
            grandTotal += entry.getValue();
        }

        // Add Projected row if needed, in blue
        if (!projectedTransactions.isEmpty()) {
            double projectedTotal = projectedTransactions.stream()
                    .mapToDouble(ProjectedTransaction::getAmountValue)
                    .sum();
            tableModel.addRow(new Object[]{"Projected", String.format("$%.2f", projectedTotal)});
            logger.info("Added Projected row: Projected = ${}", String.format("%.2f", projectedTotal));
            grandTotal += projectedTotal;
        }

        logger.info("Category summary table populated with {} rows.", tableModel.getRowCount());

        // Add totals row if there was at least one category or projected row
        if (!totalsByCategory.isEmpty() || !projectedTransactions.isEmpty()) {
            tableModel.addRow(new Object[]{"TOTAL", String.format("$%.2f", grandTotal)});
            logger.info("Totals row added: TOTAL = ${}", String.format("%.2f", grandTotal));
        }
    }

    /**
     * Table cell renderer to highlight the "Projected" category row in blue.
     */
    private static class ProjectedCategoryCellRenderer extends DefaultTableCellRenderer {
        private static final Color PROJECTION_BLUE = new Color(180, 210, 255);
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus, int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            DefaultTableModel model = (DefaultTableModel) table.getModel();
            Object catObj = model.getValueAt(row, 0);
            if (catObj != null && "Projected".equalsIgnoreCase(catObj.toString())) {
                c.setBackground(PROJECTION_BLUE);
            } else {
                c.setBackground(isSelected ? table.getSelectionBackground() : Color.WHITE);
            }
            return c;
        }
    }

    /**
     * Internal: Adds a mouse listener to the table for row click events.
     * If the Projected row is clicked, shows a dialog of projected transactions.
     * Otherwise, delegates to the row click listener if set.
     */
    private void setupTableRowClickListener() {
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                logger.info("Table row clicked. e={}", e);
                int row = table.rowAtPoint(e.getPoint());
                // Exclude the totals row from click events
                if (row >= 0 && row < tableModel.getRowCount() - 1) {
                    Object catObj = tableModel.getValueAt(row, 0);
                    if (catObj != null && "Projected".equalsIgnoreCase(catObj.toString())) {
                        logger.info("Projected row clicked - showing projected transactions dialog.");
                        showProjectedTransactionsDialog();
                    } else if (categoryRowClickListener != null && catObj != null) {
                        String category = catObj.toString();
                        logger.info("Category row clicked: '{}'", category);
                        categoryRowClickListener.accept(category);
                    } else {
                        logger.warn("No categoryRowClickListener set or category object was null.");
                    }
                } else if (row == tableModel.getRowCount() - 1) {
                    logger.info("Totals row clicked (ignored).");
                }
            }
        });
    }

    /**
     * Opens a dialog displaying the projected transactions in a table.
     */
    private void showProjectedTransactionsDialog() {
        logger.info("showProjectedTransactionsDialog called with {} projected transactions.", projectedTransactions.size());
        if (projectedTransactions.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No projected transactions found for this view.", "No Projected Expenses", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        // Map ProjectedTransaction to BudgetTransaction so we can reuse GenericTablePanel
        List<BudgetTransaction> asBudgetTransactions = projectedTransactions.stream()
                .map(ProjectedTransaction::asBudgetTransaction) // You must implement this conversion in your model
                .collect(Collectors.toList());

        String[] columns = {"Name", "Amount", "Category", "Criticality", "Transaction Date", "Account", "Statement Period"};
        GenericTablePanel panel = new GenericTablePanel(columns, tx -> new Object[] {
                tx.getName(), tx.getAmount(), tx.getCategory(),
                tx.getCriticality(), tx.getTransactionDate(), tx.getAccount(),
                tx.getStatementPeriod()
        });
        panel.setTransactions(asBudgetTransactions);

        JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(this), "Projected Transactions", Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.getContentPane().add(panel, BorderLayout.CENTER);
        dialog.setSize(800, 400);
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
        logger.info("Projected transactions dialog displayed.");
    }
}