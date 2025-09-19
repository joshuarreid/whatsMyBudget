package ui;

import model.BudgetTransaction;
import org.slf4j.Logger;
import util.AppLogger;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Panel displaying imported transactions in a table:
 * - Highlights new transactions green.
 * - Grays out duplicates (not editable).
 * - Payment Method is always visible, Status is hidden.
 * - Newest transactions at the top.
 */
public class ImportTablePanel extends JPanel {
    private static final Logger logger = AppLogger.getLogger(ImportTablePanel.class);

    private final JTable table;
    private final ImportTableModel tableModel;

    /**
     * @param transactions List of imported BudgetTransactions, with .isDuplicate() correctly set (from ImportService logic).
     * @param onImportConfirmed Callback for when user presses Import.
     */
    public ImportTablePanel(List<BudgetTransaction> transactions, Runnable onImportConfirmed) {
        logger.info("Initializing ImportTablePanel with {} transactions", transactions.size());
        setLayout(new BorderLayout());

        List<BudgetTransaction> sorted = sortNewestFirst(transactions);
        tableModel = new ImportTableModel(sorted);

        table = new JTable(tableModel);

        // Status column is not present in the model at all.
        // Payment Method is always present and displayed.

        table.setDefaultRenderer(Object.class, new DuplicationHighlightRenderer(tableModel));

        JScrollPane scrollPane = new JScrollPane(table);
        add(scrollPane, BorderLayout.CENTER);

        // Do NOT add Import/Cancel buttons here. They should be managed by the parent dialog.
    }

    /**
     * Returns only the new transactions (not duplicates).
     */
    public List<BudgetTransaction> getTransactions() {
        return tableModel.getNewTransactions();
    }

    // Sort so the newest (by created time) is first
    private List<BudgetTransaction> sortNewestFirst(List<BudgetTransaction> txs) {
        logger.info("Sorting transactions newest first by Created Time");
        List<BudgetTransaction> sorted = new ArrayList<>(txs);
        sorted.sort((a, b) -> {
            LocalDateTime da = parseDateTime(a.getCreatedTime());
            LocalDateTime db = parseDateTime(b.getCreatedTime());
            if (da == null && db == null) return 0;
            if (da == null) return 1;
            if (db == null) return -1;
            return db.compareTo(da); // descending (newest first)
        });
        logger.info("Sorted {} transactions by Created Time (newest first)", sorted.size());
        return sorted;
    }

    private LocalDateTime parseDateTime(String s) {
        if (s == null) return null;
        List<DateTimeFormatter> fmts = Arrays.asList(
                DateTimeFormatter.ofPattern("MMMM d, yyyy h:mm a", Locale.US),
                DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.US),
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd")
        );
        for (DateTimeFormatter fmt : fmts) {
            try {
                return LocalDateTime.parse(s, fmt);
            } catch (DateTimeParseException ignored) {}
            try {
                return LocalDateTime.of(LocalDateTime.parse(s, fmt).toLocalDate(), LocalDateTime.MIN.toLocalTime());
            } catch (DateTimeParseException ignored) {}
        }
        return null;
    }

    /**
     * Custom TableModel for import editing, aware of duplicates.
     * Expects BudgetTransaction#isDuplicate() to be set by ImportService.
     */
    static class ImportTableModel extends AbstractTableModel {
        private final List<BudgetTransaction> transactions;
        private final List<String> columns;

        ImportTableModel(List<BudgetTransaction> transactions) {
            this.transactions = new ArrayList<>(transactions);
            // Status is now excluded from the visible columns.
            this.columns = Arrays.asList("Name", "Amount", "Category", "Criticality", "Transaction Date", "Account", "Created Time", "Payment Method");
            logger.info("ImportTableModel initialized with columns: {}", columns);
        }

        @Override
        public int getRowCount() {
            return transactions.size();
        }

        @Override
        public int getColumnCount() {
            return columns.size();
        }

        @Override
        public String getColumnName(int column) {
            return columns.get(column);
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            BudgetTransaction tx = transactions.get(rowIndex);
            switch (columns.get(columnIndex)) {
                case "Name": return tx.getName();
                case "Amount": return tx.getAmount();
                case "Category": return tx.getCategory();
                case "Criticality": return tx.getCriticality();
                case "Transaction Date": return tx.getTransactionDate();
                case "Account": return tx.getAccount();
                case "Created Time": return tx.getCreatedTime();
                case "Payment Method": return tx.getPaymentMethod();
                default: return "";
            }
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            // No editing allowed
            return false;
        }

        public boolean isDuplicate(BudgetTransaction tx) {
            return tx.isDuplicate();
        }

        public boolean isNew(BudgetTransaction tx) {
            return !tx.isDuplicate();
        }

        public List<BudgetTransaction> getNewTransactions() {
            return transactions.stream().filter(this::isNew).collect(Collectors.toList());
        }
    }

    /**
     * Renderer to highlight duplicates gray, new transactions green.
     */
    static class DuplicationHighlightRenderer extends DefaultTableCellRenderer {
        private final ImportTableModel model;

        DuplicationHighlightRenderer(ImportTableModel model) {
            this.model = model;
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus,
                                                       int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            BudgetTransaction tx = model.transactions.get(table.convertRowIndexToModel(row));
            if (model.isDuplicate(tx)) {
                c.setBackground(new Color(220, 220, 220)); // light gray
                c.setForeground(Color.DARK_GRAY);
            } else {
                c.setBackground(new Color(200, 255, 200)); // pale green for new
                c.setForeground(Color.BLACK);
            }
            if (isSelected) {
                c.setBackground(table.getSelectionBackground());
                c.setForeground(table.getSelectionForeground());
            }
            return c;
        }
    }
}