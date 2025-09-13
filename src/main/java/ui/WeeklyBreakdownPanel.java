package ui;

import model.BudgetTransaction;
import org.slf4j.Logger;
import util.AppLogger;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.*;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Panel that summarizes and displays totals per week for a given category, account, or criticality.
 * Ready for drilldown (row click to see underlying transactions).
 */
public class WeeklyBreakdownPanel extends JPanel {
    private final Logger logger = AppLogger.getLogger(getClass());
    private final JTable table;
    private final DefaultTableModel tableModel;
    private String statementPeriod; // Optionally use for filtering

    /**
     * Constructs a weekly breakdown panel.
     */
    public WeeklyBreakdownPanel() {
        super(new BorderLayout());
        logger.info("Creating WeeklyBreakdownPanel");
        this.tableModel = new DefaultTableModel(new String[] {"Week", "Total Amount"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };
        this.table = new JTable(tableModel);
        this.add(new JScrollPane(table), BorderLayout.CENTER);
    }

    /**
     * Sets the transactions for weekly breakdown, filtered as needed.
     * @param transactions List of BudgetTransaction (not null)
     * @param filters      Optional predicates to apply (e.g., by category, account, criticality)
     */
    public void setTransactions(List<BudgetTransaction> transactions, Predicate<BudgetTransaction>... filters) {
        logger.info("setTransactions called with {} transaction(s)", transactions == null ? 0 : transactions.size());
        if (transactions == null) {
            logger.error("Received null transactions list; clearing table.");
            tableModel.setRowCount(0);
            return;
        }
        List<BudgetTransaction> filtered = transactions;
        if (filters != null) {
            for (Predicate<BudgetTransaction> filter : filters) {
                if (filter != null) filtered = filtered.stream().filter(filter).collect(Collectors.toList());
            }
        }
        logger.info("Filtered to {} transaction(s) after applying predicates", filtered.size());
        Map<String, Double> weeklyTotals = groupByWeek(filtered);
        updateTable(weeklyTotals);
    }

    /**
     * Groups transactions by week label (e.g., "2025-W36") and sums their amounts.
     */
    private Map<String, Double> groupByWeek(List<BudgetTransaction> transactions) {
        Map<String, Double> weekTotals = new TreeMap<>();
        for (BudgetTransaction tx : transactions) {
            String dateStr = tx.getTransactionDate();
            LocalDate date = parseDate(dateStr);
            if (date == null) {
                logger.warn("Could not parse date '{}', skipping transaction", dateStr);
                continue;
            }
            WeekFields weekFields = WeekFields.of(Locale.getDefault());
            int weekNum = date.get(weekFields.weekOfWeekBasedYear());
            int year = date.getYear();
            String weekLabel = String.format("%d-W%02d", year, weekNum);
            double amt = parseAmount(tx.getAmount());
            weekTotals.merge(weekLabel, amt, Double::sum);
        }
        logger.info("Grouped into {} week(s)", weekTotals.size());
        return weekTotals;
    }

    private void updateTable(Map<String, Double> weeklyTotals) {
        logger.info("updateTable called with {} weeks", weeklyTotals.size());
        tableModel.setRowCount(0);
        for (Map.Entry<String, Double> entry : weeklyTotals.entrySet()) {
            tableModel.addRow(new Object[] {entry.getKey(), String.format("$%.2f", entry.getValue())});
        }
        logger.info("Table updated with {} rows", tableModel.getRowCount());
    }

    private LocalDate parseDate(String dateStr) {
        if (dateStr == null) return null;
        try {
            // Example: "September 12, 2025"
            Date date = new SimpleDateFormat("MMMM d, yyyy", Locale.ENGLISH).parse(dateStr);
            return date.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate();
        } catch (ParseException e) {
            logger.error("parseDate failed for '{}': {}", dateStr, e.getMessage());
            return null;
        }
    }

    private static double parseAmount(String amt) {
        if (amt == null) return 0.0;
        String clean = amt.replace("$", "").replace(",", "").trim();
        if (clean.isEmpty()) return 0.0;
        try {
            return Double.parseDouble(clean);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
}