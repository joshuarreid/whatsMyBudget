package ui;

import model.BudgetTransaction;
import model.BudgetTransactionList;
import org.slf4j.Logger;
import util.AppLogger;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
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
    private Consumer<String> categoryRowClickListener; // Added for drilldown support

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
        setupTableRowClickListener();
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
     * Internal: Adds a mouse listener to the table for row click events.
     */
    private void setupTableRowClickListener() {
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                logger.info("Table row clicked. e={}", e);
                int row = table.rowAtPoint(e.getPoint());
                if (row >= 0 && row < tableModel.getRowCount()) {
                    Object catObj = tableModel.getValueAt(row, 0);
                    if (categoryRowClickListener != null && catObj != null) {
                        String category = catObj.toString();
                        logger.info("Category row clicked: '{}'", category);
                        categoryRowClickListener.accept(category);
                    } else {
                        logger.warn("No categoryRowClickListener set or category object was null.");
                    }
                }
            }
        });
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
                        Collectors.summingDouble(BudgetTransaction::getAmountValue)
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
}