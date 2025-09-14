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

import static ui.GenericTablePanel.formatAmount;

/**
 * Panel that displays total spending by category for a given account and criticality,
 * including projected transactions for the current statement period, highlighted in blue.
 * Both real and projected transactions are included in the table and grand total.
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
     * The list of projected transactions to display. Must already be filtered for the current statement period.
     */
    private List<ProjectedTransaction> projectedTransactions = Collections.emptyList();

    public CategorySummaryPanel(String account, String criticality) {
        super(new BorderLayout());
        logger.info("CategorySummaryPanel created for account='{}', criticality='{}'", account, criticality);
        this.account = account;
        this.criticality = criticality;
        this.tableModel = new DefaultTableModel(new String[]{"Category", "Total Amount", "Type"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };
        this.table = new JTable(tableModel);
        this.table.setDefaultRenderer(Object.class, new ProjectedCategoryCellRenderer());
        this.add(new JScrollPane(table), BorderLayout.CENTER);
        setupTableRowClickListener();
    }

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

    public void setCategoryRowClickListener(Consumer<String> listener) {
        logger.info("setCategoryRowClickListener called.");
        this.categoryRowClickListener = listener;
    }

    public void setProjectedTransactions(List<ProjectedTransaction> projected) {
        logger.info("setProjectedTransactions called with {} projected(s) for account '{}', criticality '{}'. "
                        + "Input must be filtered for the current statement period.",
                projected == null ? 0 : projected.size(), account, criticality);
        this.projectedTransactions = (projected == null) ? Collections.emptyList() : projected;
        logger.info("Set {} projected transactions for display.", this.projectedTransactions.size());
        refreshTable();
    }

    public void setTransactions(List<BudgetTransaction> transactions) {
        logger.info("setTransactions called with {} transaction(s) for account '{}', criticality '{}'",
                transactions == null ? 0 : transactions.size(), account, criticality);
        this.lastTransactions = transactions;
        refreshTable();
    }

    public void setTransactions(BudgetTransactionList transactionList) {
        logger.info("setTransactions(BudgetTransactionList) called for account='{}', criticality='{}'", account, criticality);
        if (transactionList == null) {
            logger.warn("Null BudgetTransactionList provided to setTransactions; clearing table.");
            setTransactions((List<BudgetTransaction>) null);
            return;
        }
        setTransactions(transactionList.getByAccountAndCriticality(account, criticality));
    }

    private List<BudgetTransaction> lastTransactions = null;

    /**
     * Repopulates the table showing both real and projected category totals,
     * with projected rows highlighted blue and included in the grand total.
     * Always displays both actual and projected rows for the same category if both exist.
     */
    private void refreshTable() {
        logger.info("refreshTable called for account '{}', criticality '{}'", account, criticality);
        tableModel.setRowCount(0);
        List<BudgetTransaction> filtered = (lastTransactions == null) ? Collections.emptyList() :
                lastTransactions.stream()
                        .filter(tx -> (account == null || account.equalsIgnoreCase(tx.getAccount())))
                        .filter(tx -> (criticality == null || criticality.equalsIgnoreCase(tx.getCriticality())))
                        .collect(Collectors.toList());
        logger.info("Filtered to {} base transactions for summary.", filtered.size());

        // Aggregate real transactions
        Map<String, Double> realTotals = filtered.stream()
                .collect(Collectors.groupingBy(
                        tx -> tx.getCategory() == null ? "(Uncategorized)" : tx.getCategory(),
                        Collectors.summingDouble(BudgetTransaction::getAmountValue)
                ));

        // Aggregate projected transactions
        Map<String, Double> projectedTotals = projectedTransactions.stream()
                .collect(Collectors.groupingBy(
                        tx -> tx.getCategory() == null ? "(Uncategorized)" : tx.getCategory(),
                        Collectors.summingDouble(ProjectedTransaction::getAmountValue)
                ));

        logger.info("Aggregated {} real categories, {} projected categories.", realTotals.size(), projectedTotals.size());

        // Get union of all categories for consistent display order
        Set<String> allCategories = new TreeSet<>();
        allCategories.addAll(realTotals.keySet());
        allCategories.addAll(projectedTotals.keySet());

        double grandTotal = 0.0;

        for (String category : allCategories) {
            boolean hasReal = realTotals.containsKey(category);
            boolean hasProj = projectedTotals.containsKey(category);

            // Always add both rows if both exist for a category
            if (hasReal) {
                double val = realTotals.get(category);
                tableModel.addRow(new Object[]{category, String.format("$%.2f", val), "Actual"});
                logger.debug("Added Actual row: Category='{}', Amount={}", category, val);
                grandTotal += val;
            }
            if (hasProj) {
                double val = projectedTotals.get(category);
                tableModel.addRow(new Object[]{category, String.format("$%.2f", val), "Projected"});
                logger.debug("Added Projected row: Category='{}', Amount={}", category, val);
                grandTotal += val;
            }
        }

        logger.info("Category summary table populated with {} rows ({} real, {} projected, {} unique categories).",
                tableModel.getRowCount(), realTotals.size(), projectedTotals.size(), allCategories.size());

        // Add totals row if there was at least one category row
        if (!allCategories.isEmpty()) {
            tableModel.addRow(new Object[]{"TOTAL", String.format("$%.2f", grandTotal), ""});
            logger.info("Totals row added: TOTAL = ${}", String.format("%.2f", grandTotal));
        }
    }

    /**
     * Table cell renderer to highlight projected rows in blue.
     */
    private static class ProjectedCategoryCellRenderer extends DefaultTableCellRenderer {
        private static final Color PROJECTION_BLUE = new Color(180, 210, 255);
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus, int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            DefaultTableModel model = (DefaultTableModel) table.getModel();
            Object typeObj = model.getValueAt(row, 2);
            if (typeObj != null && "Projected".equalsIgnoreCase(typeObj.toString())) {
                c.setBackground(PROJECTION_BLUE);
            } else {
                c.setBackground(isSelected ? table.getSelectionBackground() : Color.WHITE);
            }
            return c;
        }
    }

    private void setupTableRowClickListener() {
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                logger.info("Table row clicked. e={}", e);
                int row = table.rowAtPoint(e.getPoint());
                // Exclude the totals row from click events
                if (row >= 0 && row < tableModel.getRowCount() - 1) {
                    Object catObj = tableModel.getValueAt(row, 0);
                    Object typeObj = tableModel.getValueAt(row, 2);
                    if (typeObj != null && "Projected".equalsIgnoreCase(typeObj.toString())) {
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

    private void showProjectedTransactionsDialog() {
        logger.info("showProjectedTransactionsDialog called with {} projected transactions.", projectedTransactions.size());
        if (projectedTransactions.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No projected transactions found for this view.", "No Projected Expenses", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        List<BudgetTransaction> asBudgetTransactions = projectedTransactions.stream()
                .map(ProjectedTransaction::asBudgetTransaction)
                .collect(Collectors.toList());

        String[] columns = {"Name", "Amount", "Category", "Criticality", "Transaction Date", "Account", "Statement Period"};
        GenericTablePanel panel = new GenericTablePanel(columns, tx -> new Object[] {
                tx.getName(), formatAmount(tx.getAmount()), tx.getCategory(),
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