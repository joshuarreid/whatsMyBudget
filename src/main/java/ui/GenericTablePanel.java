package ui;

import model.BudgetTransaction;
import model.BudgetTransactionList;
import org.slf4j.Logger;
import util.AppLogger;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;
import java.util.function.Function;

/**
 * A generic, reusable table panel for displaying BudgetTransaction data.
 * Columns and row data mapping are configurable for scalability.
 */
public class GenericTablePanel extends JPanel {
    private final Logger logger = AppLogger.getLogger(getClass());
    private final JTable table;
    private final DefaultTableModel tableModel;
    private final String[] columnNames;
    private final Function<BudgetTransaction, Object[]> rowMapper;

    /**
     * Constructs a generic table panel with specified columns and row mapping.
     * @param columnNames Table column headers
     * @param rowMapper   Function mapping BudgetTransaction to row objects
     */
    public GenericTablePanel(String[] columnNames, Function<BudgetTransaction, Object[]> rowMapper) {
        super(new BorderLayout());
        logger.info("Creating GenericTablePanel with columns: {}", (Object) columnNames);
        this.columnNames = columnNames;
        this.rowMapper = rowMapper;
        this.tableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };
        this.table = new JTable(tableModel);
        this.add(new JScrollPane(table), BorderLayout.CENTER);
    }

    /**
     * Sets the transactions to display in the table.
     * @param transactions List of BudgetTransaction (not null)
     */
    public void setTransactions(List<BudgetTransaction> transactions) {
        logger.info("setTransactions called with {} transaction(s)", transactions == null ? 0 : transactions.size());
        tableModel.setRowCount(0);
        if (transactions == null || transactions.isEmpty()) {
            logger.info("No transactions to display");
            return;
        }
        for (BudgetTransaction tx : transactions) {
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
     * Returns the underlying JTable for further customization or adding listeners.
     */
    public JTable getTable() {
        logger.info("getTable called");
        return table;
    }
}