package ui;

import model.BudgetTransaction;
import model.BudgetTransactionList;
import model.ProjectedTransaction;
import org.slf4j.Logger;
import util.AppLogger;
import service.CSVStateService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import javax.swing.plaf.basic.BasicScrollBarUI;
import javax.swing.table.*;
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
 * including projected transactions for the current statement period, highlighted in yellow.
 * Both real and projected transactions are included in the table and grand total.
 * UI inspired by modern budgeting apps: flat, whitespace, clean grouping.
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

        setOpaque(false);

        JPanel cardPanel = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int arc = 10;
                int shadow = 3;
                Color shadowColor = new Color(0, 0, 0, 10);
                g2d.setColor(shadowColor);
                g2d.fillRoundRect(shadow, shadow, getWidth() - shadow * 2, getHeight() - shadow * 2, arc, arc);
                g2d.setColor(getBackground());
                g2d.fillRoundRect(0, 0, getWidth() - shadow, getHeight() - shadow, arc, arc);
                g2d.dispose();
            }
        };
        cardPanel.setOpaque(false);
        cardPanel.setBorder(new EmptyBorder(5, 5, 5, 5));
        cardPanel.setBackground(Color.WHITE);

        JLabel titleLabel = new JLabel(
                "<html><span style=\"font-size:12pt;font-weight:600;color:#253858;\">" +
                        (account != null ? account : "All Accounts") +
                        " &mdash; " +
                        (criticality != null ? criticality : "All Criticalities") +
                        "</span></html>"
        );
        titleLabel.setBorder(new EmptyBorder(0, 0, 5, 0));
        cardPanel.add(titleLabel, BorderLayout.NORTH);

        this.tableModel = new DefaultTableModel(new String[]{"Category", "Total Amount", "Type"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };
        this.table = new JTable(tableModel) {
            @Override
            public Component prepareRenderer(TableCellRenderer renderer, int row, int column) {
                Component c = super.prepareRenderer(renderer, row, column);
                // Subtle separator for compact rows
                if (!isRowSelected(row) && c instanceof JComponent jc) {
                    jc.setBorder(new MatteBorder(0, 0, 1, 0, new Color(234, 236, 240)));
                }
                return c;
            }
        };

        this.table.setDefaultRenderer(Object.class, new ModernCategoryCellRenderer());
        this.table.setRowHeight(22); // compact
        this.table.setShowGrid(false);
        this.table.setIntercellSpacing(new Dimension(0, 0));
        this.table.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        this.table.setSelectionBackground(new Color(232, 242, 255));
        this.table.setSelectionForeground(Color.BLACK);

        JTableHeader header = this.table.getTableHeader();
        header.setFont(new Font("Segoe UI", Font.BOLD, 12));
        header.setBackground(new Color(244, 247, 255));
        header.setForeground(new Color(26, 68, 151));
        header.setBorder(new MatteBorder(0, 0, 2, 0, new Color(210, 220, 240)));
        header.setPreferredSize(new Dimension(header.getWidth(), 24));
        ((DefaultTableCellRenderer) header.getDefaultRenderer()).setHorizontalAlignment(SwingConstants.LEFT);
        header.setOpaque(true);
        header.setReorderingAllowed(false);

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.getVerticalScrollBar().setUI(new BasicScrollBarUI() {
            protected void configureScrollBarColors(){
                this.thumbColor = new Color(210, 220, 240);
                this.trackColor = new Color(245, 247, 250);
            }
        });

        cardPanel.add(scrollPane, BorderLayout.CENTER);
        add(cardPanel, BorderLayout.CENTER);

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
     * with projected rows highlighted yellow and included in the grand total.
     * Always displays both actual and projected rows for the same category if both exist.
     * Negative projected values (extra cash) are shown as positive and green.
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
                // If negative, show as positive (extra cash)
                String displayAmount = (val < 0)
                        ? String.format("$%.2f", Math.abs(val))
                        : String.format("$%.2f", val);
                boolean wasNegative = val < 0;
                // Mark the value as green for rendering if negative; otherwise normal
                tableModel.addRow(new Object[]{category, displayAmount + (wasNegative ? ":NEG" : ""), "Projected"});
                logger.debug("Added Projected row: Category='{}', Amount={}, wasNegative={}", category, displayAmount, wasNegative);
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
     * Modern cell renderer for clean, web-like look with yellow accent, bold for totals/projected.
     * Displays negative projected values in green and without the minus sign.
     */
    private static class ModernCategoryCellRenderer extends DefaultTableCellRenderer {
        private static final Color PROJECTION_YELLOW = new Color(255, 249, 196); // Faint yellow
        private static final Color PROJECTION_GREEN = new Color(0, 153, 0); // For extra cash (negative projected)
        private static final Color TOTAL_BG = new Color(237, 242, 250);
        private static final Color HOVER_BG = new Color(244, 247, 255);
        private static final Color CATEGORY_TEXT = new Color(30, 61, 161);

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus, int row, int column) {
            // Remove marker if present and detect green highlight
            boolean showGreen = false;
            Object displayValue = value;
            if (value instanceof String str && str.endsWith(":NEG")) {
                displayValue = str.substring(0, str.length() - 4);
                showGreen = true;
            }

            Component c = super.getTableCellRendererComponent(table, displayValue, isSelected, hasFocus, row, column);
            DefaultTableModel model = (DefaultTableModel) table.getModel();
            Object typeObj = model.getValueAt(row, 2);
            Object catObj = model.getValueAt(row, 0);

            // Clean base
            c.setForeground(new Color(41, 47, 64));
            c.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            setBorder(noFocusBorder);

            // TOTAL row
            if (catObj != null && "TOTAL".equalsIgnoreCase(catObj.toString())) {
                c.setBackground(TOTAL_BG);
                c.setFont(c.getFont().deriveFont(Font.BOLD, 12f));
                c.setForeground(new Color(26, 68, 151));
            }
            // Projected row
            else if (typeObj != null && "Projected".equalsIgnoreCase(typeObj.toString())) {
                c.setBackground(PROJECTION_YELLOW);
                c.setFont(c.getFont().deriveFont(Font.BOLD, 12f));
                // For green: only apply to Amount column, and only if value is positive (was originally negative)
                if (column == 1 && showGreen) {
                    c.setForeground(PROJECTION_GREEN);
                } else if (column == 1) {
                    c.setForeground(new Color(140, 110, 0));
                } else {
                    c.setForeground(new Color(140, 110, 0));
                }
            }
            // Hover/selected row
            else if (isSelected) {
                c.setBackground(HOVER_BG);
                c.setFont(c.getFont().deriveFont(Font.PLAIN));
            }
            // Normal row
            else {
                c.setBackground(Color.WHITE);
                c.setFont(c.getFont().deriveFont(Font.PLAIN));
            }

            // Blue accent for "Category" column except TOTAL
            if (column == 0) {
                if (catObj != null && !"TOTAL".equalsIgnoreCase(catObj.toString())) {
                    setForeground(CATEGORY_TEXT);
                    setFont(getFont().deriveFont(Font.BOLD, 12f));
                }
                setHorizontalAlignment(SwingConstants.LEFT);
            } else if (column == 1) {
                setHorizontalAlignment(SwingConstants.RIGHT);
            } else {
                setHorizontalAlignment(SwingConstants.CENTER);
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
        table.addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int row = table.rowAtPoint(e.getPoint());
                if (row >= 0 && row < tableModel.getRowCount() - 1) {
                    table.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                } else {
                    table.setCursor(Cursor.getDefaultCursor());
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