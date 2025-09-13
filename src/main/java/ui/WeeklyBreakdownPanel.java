package ui;

import model.BudgetTransaction;
import org.slf4j.Logger;
import util.AppLogger;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;

/**
 * Panel that summarizes and displays weekly totals for a given category, based on statement-relative weeks.
 * Each week is 7 days, starting from statementStartDate. The last week may be shorter.
 */
public class WeeklyBreakdownPanel extends JPanel {
    private final Logger logger = AppLogger.getLogger(getClass());
    private final JTable table;
    private final DefaultTableModel tableModel;
    private final JLabel titleLabel;
    private final String categoryName;
    private final List<BudgetTransaction> transactions;
    private final LocalDate statementStartDate;
    private final LocalDate statementEndDate;

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

        // Title label
        titleLabel = new JLabel(this.categoryName + " Weekly Breakdown", SwingConstants.CENTER);
        titleLabel.setFont(new Font("Arial", Font.BOLD, 16));
        this.add(titleLabel, BorderLayout.NORTH);

        // Table setup
        this.tableModel = new DefaultTableModel(new String[] {"Week", "Total Amount"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };
        this.table = new JTable(tableModel);
        this.add(new JScrollPane(table), BorderLayout.CENTER);

        updateTable();
    }

    /**
     * Recomputes and displays the weekly breakdown table, using statement-relative weeks.
     */
    private void updateTable() {
        logger.info("updateTable called for category='{}' with {} transaction(s)", categoryName, transactions.size());
        tableModel.setRowCount(0);
        if (transactions.isEmpty()) {
            logger.warn("No transactions provided for category '{}'. Table will remain empty.", categoryName);
            return;
        }

        // Map: weekIndex -> totalAmount
        Map<Integer, Double> weekTotals = new LinkedHashMap<>();
        Map<Integer, LocalDate[]> weekRanges = getWeekRanges(statementStartDate, statementEndDate);

        // Assign each transaction to a week, based on days since statement start
        for (BudgetTransaction tx : transactions) {
            LocalDate txDate = tx.getDate();
            if (txDate == null) {
                logger.warn("Transaction '{}' has null parsed date; skipping.", tx.getName());
                continue;
            }
            if (txDate.isBefore(statementStartDate) || txDate.isAfter(statementEndDate)) {
                logger.warn("Transaction '{}' (date {}) outside statement period ({} to {}); skipping.", tx.getName(), txDate, statementStartDate, statementEndDate);
                continue;
            }
            int weekIndex = getStatementWeekIndex(txDate, statementStartDate);
            double amt = tx.getAmountValue();
            weekTotals.merge(weekIndex, amt, Double::sum);
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
     * Returns a map of weekIndex to date ranges, based on the statement period.
     */
    private Map<Integer, LocalDate[]> getWeekRanges(LocalDate start, LocalDate end) {
        Map<Integer, LocalDate[]> weekRanges = new LinkedHashMap<>();
        int week = 1;
        LocalDate weekStart = start;
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
     */
    private int getStatementWeekIndex(LocalDate date, LocalDate statementStart) {
        long daysBetween = java.time.temporal.ChronoUnit.DAYS.between(statementStart, date);
        int weekIndex = (int)(daysBetween / 7) + 1;
        logger.debug("getStatementWeekIndex: date={}, statementStart={}, daysBetween={}, weekIndex={}", date, statementStart, daysBetween, weekIndex);
        return weekIndex;
    }
}