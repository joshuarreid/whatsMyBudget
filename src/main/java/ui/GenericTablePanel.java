package ui;

import model.BudgetTransaction;
import model.ProjectedTransaction;
import model.BudgetTransactionList;
import org.slf4j.Logger;
import util.AppLogger;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.util.List;
import java.util.function.Function;

/**
 * Generic panel for displaying budget or projected transactions in a table.
 * Supports both BudgetTransaction and ProjectedTransaction types.
 */
public class GenericTablePanel extends JPanel {
    private static final Logger logger = AppLogger.getLogger(GenericTablePanel.class);

    private final DefaultTableModel tableModel;
    private final JTable table;
    private final Function<BudgetTransaction, Object[]> rowMapper;

    /**
     * Constructs the GenericTablePanel with the given columns and row-mapping function.
     * @param columns Array of column names
     * @param rowMapper Function mapping a BudgetTransaction to an Object[] row
     */
    public GenericTablePanel(String[] columns, Function<BudgetTransaction, Object[]> rowMapper) {
        logger.info("Initializing GenericTablePanel with columns: {}", (Object) columns);
        this.rowMapper = rowMapper;
        this.tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        this.table = new JTable(tableModel);
        setLayout(new java.awt.BorderLayout());
        add(new JScrollPane(table), java.awt.BorderLayout.CENTER);
        logger.info("GenericTablePanel initialized.");
    }

    /**
     * Sets the transactions to display in the table. Accepts BudgetTransaction or subclasses (e.g., ProjectedTransaction).
     * @param transactions List of BudgetTransaction (may include ProjectedTransaction)
     */
    public void setTransactions(List<? extends BudgetTransaction> transactions) {
        logger.info("setTransactions called with {} transaction(s)", transactions == null ? 0 : transactions.size());
        tableModel.setRowCount(0);
        if (transactions == null || transactions.isEmpty()) {
            logger.info("No transactions to display");
            return;
        }
        for (BudgetTransaction tx : transactions) {
            if (tx instanceof ProjectedTransaction) {
                logger.debug("Adding ProjectedTransaction: {}", tx);
            } else {
                logger.debug("Adding BudgetTransaction: {}", tx);
            }
            tableModel.addRow(rowMapper.apply(tx));
        }
        logger.info("Table populated with {} row(s)", tableModel.getRowCount());
    }

    /**
     * Overload for convenience: sets transactions from BudgetTransactionList.
     * @param transactionList BudgetTransactionList (may be null)
     */
    public void setTransactions(BudgetTransactionList transactionList) {
        logger.info("setTransactions(BudgetTransactionList) called");
        if (transactionList == null) {
            setTransactions((List<BudgetTransaction>) null);
            return;
        }
        setTransactions(transactionList.getTransactions());
    }

    /**
     * Convenience overload for projected transactions.
     * @param projectedTransactions List of ProjectedTransaction
     */
    public void setProjectedTransactions(List<ProjectedTransaction> projectedTransactions) {
        logger.info("setProjectedTransactions called with {} projected transaction(s)", projectedTransactions == null ? 0 : projectedTransactions.size());
        setTransactions(projectedTransactions);
    }

    /**
     * Returns the underlying JTable for selection/interaction.
     */
    public JTable getTable() {
        return table;
    }
}