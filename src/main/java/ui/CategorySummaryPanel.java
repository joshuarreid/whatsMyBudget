package ui;

import model.BudgetTransaction;
import model.BudgetTransactionList;
import org.slf4j.Logger;
import util.AppLogger;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Panel that displays total spending by category for a given account and criticality.
 * Robust to null/empty input, includes full logging, and is ready for future drilldown.
 */
public class CategorySummaryPanel extends JPanel {
    private final Logger logger = AppLogger.getLogger(getClass());
    private String account;
    private String criticality;
    private final JTable table;
    private final DefaultTableModel tableModel;

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
        this.add(new JScrollPane(table), BorderLayout.CENTER);
    }

    /**
     * Sets transactions to summarize and display by category.
     * @param transactions List of BudgetTransaction (may be null or empty)
     */
    public void setTransactions(List<BudgetTransaction> transactions) {
        logger.info("setTransactions called with {} transaction(s) for account '{}', criticality '{}'",
                transactions == null ? 0 : transactions.size(), account, criticality);
        tableModel.setRowCount(0);
        if (transactions == null || transactions.isEmpty()) {
            logger.warn("No transactions to summarize.");
            return;
        }
        List<BudgetTransaction> filtered = transactions.stream()
                .filter(tx -> (account == null || account.equalsIgnoreCase(tx.getAccount())))
                .filter(tx -> (criticality == null || criticality.equalsIgnoreCase(tx.getCriticality())))
                .collect(Collectors.toList());
        logger.info("Filtered to {} transactions for summary.", filtered.size());
        Map<String, Double> totalsByCategory = filtered.stream()
                .collect(Collectors.groupingBy(
                        tx -> tx.getCategory() == null ? "(Uncategorized)" : tx.getCategory(),
                        Collectors.summingDouble(tx -> parseAmount(tx.getAmount()))
                ));
        logger.info("Aggregated spending into {} categories.", totalsByCategory.size());
        for (Map.Entry<String, Double> entry : totalsByCategory.entrySet()) {
            tableModel.addRow(new Object[]{entry.getKey(), String.format("$%.2f", entry.getValue())});
        }
        logger.info("Category summary table populated with {} rows.", tableModel.getRowCount());
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

    /**
     * Parses an amount string (e.g., "$10.00") into a double.
     * Returns 0.0 for invalid/empty input.
     */
    private static double parseAmount(String amount) {
        if (amount == null) return 0.0;
        String clean = amount.replace("$", "").replace(",", "").trim();
        if (clean.isEmpty()) return 0.0;
        try {
            return Double.parseDouble(clean);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
}