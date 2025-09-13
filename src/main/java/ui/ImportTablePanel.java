package ui;

import model.BudgetTransaction;
import org.slf4j.Logger;
import util.AppLogger;
import util.StatementPeriodUtil;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellEditor;
import java.awt.*;
import java.time.Month;
import java.time.Year;
import java.util.List;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Panel displaying imported transactions in a table.
 * Allows editing of the Statement Period column.
 * Validates that all periods are set and valid before confirming import.
 */
public class ImportTablePanel extends JPanel {
    private static final Logger logger = AppLogger.getLogger(ImportTablePanel.class);

    private final JTable table;
    private final ImportTableModel tableModel;
    private final JButton importButton;
    private final JLabel errorLabel;

    public ImportTablePanel(List<BudgetTransaction> transactions, Runnable onImportConfirmed) {
        logger.info("Initializing ImportTablePanel with {} transactions", transactions.size());
        setLayout(new BorderLayout());

        tableModel = new ImportTableModel(transactions);
        table = new JTable(tableModel);

        // Use custom cell editor for Statement Period (Month dropdown + year spinner)
        setUpStatementPeriodEditor();

        // Highlight invalid statement periods
        table.setDefaultRenderer(String.class, new PeriodValidationRenderer(tableModel));

        JScrollPane scrollPane = new JScrollPane(table);
        add(scrollPane, BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel(new BorderLayout());

        errorLabel = new JLabel();
        errorLabel.setForeground(Color.RED);
        bottomPanel.add(errorLabel, BorderLayout.WEST);

        importButton = new JButton("Import");
        bottomPanel.add(importButton, BorderLayout.EAST);

        importButton.addActionListener(e -> {
            logger.info("Import button pressed");
            List<String> invalids = tableModel.getInvalidPeriods();
            if (!invalids.isEmpty()) {
                errorLabel.setText("Please fix statement periods (see highlighted rows)");
                logger.warn("Import prevented: {} invalid statement periods", invalids.size());
                return;
            }
            logger.info("All statement periods valid, proceeding with import");
            errorLabel.setText("");
            if (onImportConfirmed != null) onImportConfirmed.run();
        });

        add(bottomPanel, BorderLayout.SOUTH);
    }

    /**
     * Applies the given Month and year to all selected rows' statement periods.
     */
    public void applyPeriodToAll(Month month, int year) {
        logger.info("Applying period {} {} to all transactions", month, year);
        tableModel.applyPeriodToAll(month, year);
        table.repaint();
    }

    private void setUpStatementPeriodEditor() {
        // For "Statement Period" column, use a custom editor (month + year)
        table.getColumnModel().getColumn(tableModel.getStatementPeriodColIndex())
                .setCellEditor(new StatementPeriodCellEditor());
    }

    /**
     * Returns the table's data after editing.
     */
    public List<BudgetTransaction> getTransactions() {
        return tableModel.getTransactions();
    }

    /**
     * Custom TableModel for import editing.
     */
    static class ImportTableModel extends AbstractTableModel {
        private final List<BudgetTransaction> transactions;
        private final List<String> columns;

        ImportTableModel(List<BudgetTransaction> transactions) {
            this.transactions = new ArrayList<>(transactions);
            this.columns = Arrays.asList("Name", "Amount", "Category", "Criticality", "Transaction Date", "Account", "Status", "Created Time", "Payment Method", "Statement Period");
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
                case "Status": return tx.getStatus();
                case "Created Time": return tx.getCreatedTime();
                case "Payment Method": return tx.getPaymentMethod();
                case "Statement Period": return tx.getStatementPeriod();
                default: return "";
            }
        }

        @Override
        public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
            BudgetTransaction tx = transactions.get(rowIndex);
            if ("Statement Period".equals(columns.get(columnIndex))) {
                String period = String.valueOf(aValue);
                logger.info("Setting statement period for row {}: {}", rowIndex, period);
                tx.setStatementPeriod(period);
                fireTableCellUpdated(rowIndex, columnIndex);
            }
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return "Statement Period".equals(columns.get(columnIndex));
        }

        public int getStatementPeriodColIndex() {
            return columns.indexOf("Statement Period");
        }

        public List<BudgetTransaction> getTransactions() {
            return transactions;
        }

        public void applyPeriodToAll(Month month, int year) {
            String period = util.StatementPeriodUtil.buildStatementPeriod(month, year);
            for (BudgetTransaction tx : transactions) {
                tx.setStatementPeriod(period);
            }
            fireTableDataChanged();
        }

        public List<String> getInvalidPeriods() {
            return transactions.stream()
                    .map(BudgetTransaction::getStatementPeriod)
                    .filter(p -> !StatementPeriodUtil.isValidStatementPeriod(p))
                    .collect(Collectors.toList());
        }
    }

    /**
     * Custom cell editor for statement period (Month dropdown + year spinner).
     */
    static class StatementPeriodCellEditor extends AbstractCellEditor implements TableCellEditor {
        private final JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        private final JComboBox<Month> monthComboBox = new JComboBox<>(Month.values());
        private final JSpinner yearSpinner = new JSpinner(new SpinnerNumberModel(Year.now().getValue(), 2000, 2100, 1));
        private String currentValue;

        StatementPeriodCellEditor() {
            panel.add(monthComboBox);
            panel.add(yearSpinner);
        }

        @Override
        public Object getCellEditorValue() {
            Month m = (Month) monthComboBox.getSelectedItem();
            int y = (Integer) yearSpinner.getValue();
            String period = util.StatementPeriodUtil.buildStatementPeriod(m, y);
            return period;
        }

        @Override
        public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
            try {
                if (value instanceof String && util.StatementPeriodUtil.isValidStatementPeriod((String) value)) {
                    java.time.YearMonth ym = util.StatementPeriodUtil.parseStatementPeriod((String) value);
                    monthComboBox.setSelectedItem(ym.getMonth());
                    yearSpinner.setValue(ym.getYear());
                }
            } catch (Exception e) {
                logger.error("Error initializing statement period editor: {}", e.getMessage(), e);
            }
            return panel;
        }
    }

    /**
     * Renderer to highlight invalid statement periods.
     */
    static class PeriodValidationRenderer extends DefaultTableCellRenderer {
        private final ImportTableModel model;

        PeriodValidationRenderer(ImportTableModel model) {
            this.model = model;
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus,
                                                       int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            if (model.getColumnName(column).equals("Statement Period")) {
                String period = (String) value;
                if (!StatementPeriodUtil.isValidStatementPeriod(period)) {
                    c.setBackground(new Color(255, 220, 220)); // light red
                } else {
                    c.setBackground(isSelected ? table.getSelectionBackground() : Color.WHITE);
                }
            } else {
                c.setBackground(isSelected ? table.getSelectionBackground() : Color.WHITE);
            }
            return c;
        }
    }
}