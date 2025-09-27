package ui;

import model.BudgetTransaction;
import org.slf4j.Logger;
import util.AppLogger;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

import static ui.GenericTablePanel.formatAmount;

/**
 * Panel that summarizes and displays weekly totals for a given category, based on statement-relative weeks.
 * Each week is 7 days, starting from the first of the month of statementStartDate. The last week may be shorter.
 * For individual views, ensures that split [Split Joint] entries replace the original Joint entry.
 */
public class WeeklyBreakdownPanel extends JPanel {
    private final Logger logger = AppLogger.getLogger(getClass());
    private final JTable table;
    private final DefaultTableModel tableModel;
    private final String categoryName;
    private final List<BudgetTransaction> transactions;
    private final LocalDate statementStartDate;
    private final LocalDate statementEndDate;
    private Map<Integer, List<BudgetTransaction>> weekToTransactions; // For drilldown
    private LocalDate anchorStartDate; // The first of the month for week 1

    /**
     * Constructs a WeeklyBreakdownPanel for the given category and transactions, using statement-relative weeks.
     * @param categoryName        Name of the category (not null)
     * @param transactions        Filtered list of transactions for this category (not null)
     * @param statementStartDate  Start date of the statement period (not null)
     * @param statementEndDate    End date of the statement period (not null, inclusive)
     */
    public WeeklyBreakdownPanel(String categoryName, List<BudgetTransaction> transactions, LocalDate statementStartDate, LocalDate statementEndDate) {
        super(new BorderLayout());
        logger.info("Creating WeeklyBreakdownPanel for category='{}', statementStartDate={}, statementEndDate={}, transactions={}",
                categoryName, statementStartDate, statementEndDate, transactions == null ? 0 : transactions.size());
        if (categoryName == null || statementStartDate == null || statementEndDate == null || transactions == null) {
            logger.error("Null input(s) to WeeklyBreakdownPanel. categoryName={}, statementStartDate={}, statementEndDate={}, transactions={}",
                    categoryName, statementStartDate, statementEndDate, transactions == null ? "null" : transactions.size());
            throw new IllegalArgumentException("Category name, transactions, statement start and end dates must not be null.");
        }
        if (statementEndDate.isBefore(statementStartDate)) {
            logger.error("statementEndDate {} is before statementStartDate {}", statementEndDate, statementStartDate);
            throw new IllegalArgumentException("statementEndDate must not be before statementStartDate");
        }
        this.categoryName = categoryName;
        this.transactions = transactions;
        this.statementStartDate = statementStartDate;
        this.statementEndDate = statementEndDate;
        this.anchorStartDate = statementStartDate.withDayOfMonth(1);

        this.tableModel = new DefaultTableModel(new String[] {"Week", "Total Amount"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };
        this.table = new JTable(tableModel);
        this.add(new JScrollPane(table), BorderLayout.CENTER);

        updateTable();
        setupTableRowClickListener();
        setupWeeklySelectionAverageTooltip();
    }

    /**
     * Recomputes and displays the weekly breakdown table, using statement-relative weeks.
     * Also builds a map of week index to transactions for drilldown.
     * Filters out original Joint transactions if a split (personalized) version for Josh/Anna exists,
     * so totals match the rows shown in the drilldown modal.
     */
    private void updateTable() {
        logger.info("updateTable called for category='{}' with {} transaction(s)", categoryName, transactions.size());
        tableModel.setRowCount(0);
        if (transactions.isEmpty()) {
            logger.warn("No transactions provided for category '{}'. Table will remain empty.", categoryName);
            return;
        }

        // --- Fix: filter out duplicate Joint+Split-Joint for individual views at the start ---
        List<BudgetTransaction> filteredTransactions = filterPersonalizedNoJointDuplicatesForWeek(transactions);

        // Map: weekIndex -> totalAmount, and weekIndex -> list of transactions
        Map<Integer, Double> weekTotals = new LinkedHashMap<>();
        weekToTransactions = new LinkedHashMap<>();
        Map<Integer, LocalDate[]> weekRanges = getWeekRanges(anchorStartDate, statementEndDate);

        // Assign each transaction to a week, based on days since anchorStartDate (first of month)
        for (BudgetTransaction tx : filteredTransactions) {
            LocalDate txDate = tx.getDate();
            if (txDate == null) {
                logger.warn("Transaction '{}' has null parsed date; skipping.", tx.getName());
                continue;
            }
            if (txDate.isBefore(statementStartDate) || txDate.isAfter(statementEndDate)) {
                logger.warn("Transaction '{}' (date {}) outside statement period ({} to {}); skipping.", tx.getName(), txDate, statementStartDate, statementEndDate);
                continue;
            }
            int weekIndex = getStatementWeekIndex(txDate, anchorStartDate); // Always use anchorStartDate
            double amt = tx.getAmountValue();
            weekTotals.merge(weekIndex, amt, Double::sum);
            weekToTransactions.computeIfAbsent(weekIndex, k -> new ArrayList<>()).add(tx);
            logger.debug("Added amount ${} to week {} (date {}), tx='{}'", amt, weekIndex, txDate, tx.getName());
        }

        // Build table rows for each week in the statement period
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("MMM d");
        int totalWeeks = weekRanges.size();
        for (int week = 1; week <= totalWeeks; week++) {
            LocalDate[] range = weekRanges.get(week);
            String label = String.format("Week %d (%s–%s)", week, dtf.format(range[0]), dtf.format(range[1]));
            Double total = weekTotals.getOrDefault(week, 0.0);
            tableModel.addRow(new Object[] {label, String.format("$%.2f", total)});
            logger.info("Table row: {} -> {}", label, String.format("$%.2f", total));
        }
        logger.info("Weekly breakdown table complete: {} rows.", tableModel.getRowCount());
    }

    /**
     * Adds mouse listener for week row clicks to allow drilldown.
     */
    private void setupTableRowClickListener() {
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int row = table.rowAtPoint(e.getPoint());
                logger.info("Table row clicked: row={}", row);
                if (row >= 0 && row < tableModel.getRowCount()) {
                    handleWeekRowClick(row);
                }
            }
        });
    }

    /**
     * Handles clicking on a week row and shows the transactions for that week.
     * For individual views, ensures that only the personalized split is shown when both the split and the
     * original joint transaction are present for the same underlying transaction.
     * @param row The row index clicked.
     */
    private void handleWeekRowClick(int row) {
        logger.info("handleWeekRowClick called: row={}", row);
        // The week index is row+1, since weeks are 1-based.
        int weekIndex = row + 1;
        List<BudgetTransaction> txForWeek = weekToTransactions != null ? weekToTransactions.getOrDefault(weekIndex, List.of()) : List.of();
        logger.info("Transactions found for week {}: count={}", weekIndex, txForWeek.size());

        // No need to filter again since updateTable already filtered, but keep for robustness if table logic changes
        List<BudgetTransaction> filteredTxForWeek = filterPersonalizedNoJointDuplicatesForWeek(txForWeek);

        if (filteredTxForWeek.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No transactions for this week.", "No Data", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        // Show transactions in a dialog using GenericTablePanel
        String[] columns = {"Date", "Name", "Amount", "Category", "Criticality", "Account"};
        GenericTablePanel txPanel = new GenericTablePanel(
                columns,
                tx -> new Object[] {
                        tx.getDate() != null ? tx.getDate().toString() : "",
                        tx.getName(),
                        formatAmount(tx.getAmount()),
                        tx.getCategory(),
                        tx.getCriticality(),
                        tx.getAccount()
                });

        txPanel.setTransactions(filteredTxForWeek);

        // Add selection listener to the GenericTablePanel's JTable for tooltip sum on selection
        JTable innerTable = txPanel.getTable();
        innerTable.getSelectionModel().addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            int[] selectedRows = innerTable.getSelectedRows();
            logger.info("Drilldown table selection changed: selectedRows={}", Arrays.toString(selectedRows));
            double sum = 0.0;
            for (int selRow : selectedRows) {
                Object val = innerTable.getValueAt(selRow, 2); // "Amount" column
                if (val != null && val.toString().startsWith("$")) {
                    try {
                        sum += Double.parseDouble(val.toString().replace("$", "").replace(",", ""));
                    } catch (Exception ex) {
                        logger.warn("Failed to parse amount from '{}': {}", val, ex.getMessage());
                    }
                }
            }
            String tooltip = selectedRows.length <= 1
                    ? null
                    : "Total of selected transactions: $" + String.format("%.2f", sum);
            innerTable.setToolTipText(tooltip);
            logger.info("Drilldown table tooltip updated: {}", tooltip);
        });

        JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(this), "Transactions for " + tableModel.getValueAt(row, 0), Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.getContentPane().add(txPanel, BorderLayout.CENTER);
        dialog.setSize(700, 300);
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);

        logger.info("Displayed transaction table for week {}", weekIndex);
    }


    /**
     * Adds a selection listener to the weekly table so that when rows are selected,
     * the tooltip shows the average of the selected weeks' totals.
     */
    private void setupWeeklySelectionAverageTooltip() {
        table.getSelectionModel().addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            int[] selectedRows = table.getSelectedRows();
            logger.info("Weekly table selection changed: selectedRows={}", Arrays.toString(selectedRows));
            double sum = 0.0;
            int count = 0;
            for (int row : selectedRows) {
                Object val = table.getValueAt(row, 1); // "Total Amount" column
                if (val != null && val.toString().startsWith("$")) {
                    try {
                        sum += Double.parseDouble(val.toString().replace("$", "").replace(",", ""));
                        count++;
                    } catch (Exception ex) {
                        logger.warn("Failed to parse weekly amount from '{}': {}", val, ex.getMessage());
                    }
                }
            }
            String tooltip = count > 1
                    ? "Average of selected weeks: $" + String.format("%.2f", sum / count)
                    : null;
            table.setToolTipText(tooltip);
            logger.info("Weekly table tooltip updated: {}", tooltip);
        });
    }

    /**
     * Filters out original Joint transactions if a split (personalized) version for Josh/Anna exists.
     * For Joint views, leaves only Joint transactions.
     * Assumes all [Split Joint] txs have names ending with "[Split Joint]" and account of Josh/Anna.
     */
    private List<BudgetTransaction> filterPersonalizedNoJointDuplicatesForWeek(List<BudgetTransaction> txs) {
        logger.info("filterPersonalizedNoJointDuplicatesForWeek called on {} txs", txs == null ? 0 : txs.size());
        if (txs == null || txs.isEmpty()) return txs;

        // If the only accounts are Joint, do nothing.
        boolean onlyJoint = txs.stream().allMatch(tx -> "Joint".equalsIgnoreCase(tx.getAccount()));
        if (onlyJoint) {
            logger.info("All transactions are Joint; no filtering needed.");
            return txs;
        }

        // For individual views: remove original Joint tx if a split for the same name/date/category exists
        Set<TxKey> personalizedKeys = txs.stream()
                .filter(tx -> (tx.getAccount().equalsIgnoreCase("Josh") || tx.getAccount().equalsIgnoreCase("Anna"))
                        && tx.getName().endsWith("[Split Joint]"))
                .map(tx -> new TxKey(tx))
                .collect(Collectors.toSet());

        logger.info("Found {} personalized split joint tx keys.", personalizedKeys.size());

        // Remove original joint if there's a split version for same (date, name w/o [Split Joint], category)
        List<BudgetTransaction> result = txs.stream()
                .filter(tx -> {
                    if ("Joint".equalsIgnoreCase(tx.getAccount())) {
                        TxKey key = new TxKey(tx, true); // base key, no split
                        if (personalizedKeys.contains(key)) {
                            logger.info("Filtering out original Joint tx '{}' as split exists.", tx.getName());
                            return false;
                        }
                    }
                    return true;
                })
                .collect(Collectors.toList());

        logger.info("filterPersonalizedNoJointDuplicatesForWeek returning {} txs after filtering.", result.size());
        return result;
    }

    /**
     * Helper class for identifying duplicate split vs. joint transactions.
     */
    private static class TxKey {
        private final String date;
        private final String nameBase;
        private final String category;

        TxKey(BudgetTransaction tx) {
            this(tx, false);
        }

        TxKey(BudgetTransaction tx, boolean removeSplitSuffix) {
            this.date = tx.getTransactionDate();
            String name = tx.getName();
            if (removeSplitSuffix && name != null && name.endsWith("[Split Joint]")) {
                name = name.substring(0, name.length() - "[Split Joint]".length()).trim();
            }
            // If not removing, and name ends with [Split Joint], remove for matching with Joint
            if (!removeSplitSuffix && name != null && name.endsWith("[Split Joint]")) {
                name = name.substring(0, name.length() - "[Split Joint]".length()).trim();
            }
            this.nameBase = name == null ? "" : name;
            this.category = tx.getCategory() == null ? "" : tx.getCategory();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof TxKey)) return false;
            TxKey txKey = (TxKey) o;
            return Objects.equals(date, txKey.date)
                    && Objects.equals(nameBase, txKey.nameBase)
                    && Objects.equals(category, txKey.category);
        }

        @Override
        public int hashCode() {
            return Objects.hash(date, nameBase, category);
        }
    }

    /**
     * Returns a map of weekIndex to date ranges, based on the anchor (first of month) and statement end.
     */
    private Map<Integer, LocalDate[]> getWeekRanges(LocalDate anchorStart, LocalDate end) {
        Map<Integer, LocalDate[]> weekRanges = new LinkedHashMap<>();
        int week = 1;
        LocalDate weekStart = anchorStart;
        while (!weekStart.isAfter(end)) {
            LocalDate weekEnd = weekStart.plusDays(6);
            if (weekEnd.isAfter(end)) weekEnd = end;
            weekRanges.put(week, new LocalDate[] {weekStart, weekEnd});
            logger.debug("Week {}: {} to {}", week, weekStart, weekEnd);
            weekStart = weekEnd.plusDays(1);
            week++;
        }
        return weekRanges;
    }

    /**
     * Returns the statement-relative week index for a given date.
     * Day 0-6: week 1, 7-13: week 2, etc.
     * @param date The transaction date.
     * @param anchorStart The anchor start date (first day of the statement's month).
     * @return The week index (1-based).
     */
    private int getStatementWeekIndex(LocalDate date, LocalDate anchorStart) {
        long daysBetween = java.time.temporal.ChronoUnit.DAYS.between(anchorStart, date);
        int weekIndex = (int)(daysBetween / 7) + 1;
        logger.debug("getStatementWeekIndex: date={}, anchorStart={}, daysBetween={}, weekIndex={}", date, anchorStart, daysBetween, weekIndex);
        return weekIndex;
    }
}