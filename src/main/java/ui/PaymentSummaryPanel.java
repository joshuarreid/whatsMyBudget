package ui;

import model.BudgetTransaction;
import org.slf4j.Logger;
import util.AppLogger;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Panel that displays a summary table of Anna's and Josh's payment responsibility per card.
 * Table is dynamically generated from the provided transaction list.
 * Supports robust logging, error handling, and dynamic refresh via setTransactions.
 */
public class PaymentSummaryPanel extends JPanel {
    private static final Logger logger = AppLogger.getLogger(PaymentSummaryPanel.class);

    private DefaultTableModel tableModel;
    private JTable table;

    /**
     * Constructs the PaymentSummaryPanel with an initially empty table.
     */
    public PaymentSummaryPanel() {
        logger.info("Initializing PaymentSummaryPanel.");
        setLayout(new BorderLayout());

        String[] columns = {"Card", "Anna's Payment", "Josh's Payment"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };
        table = new JTable(tableModel);
        table.setFillsViewportHeight(true);
        table.setRowHeight(24);

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setBorder(new EmptyBorder(10, 10, 10, 10));
        add(scrollPane, BorderLayout.CENTER);

        logger.info("PaymentSummaryPanel initialized successfully.");
    }

    /**
     * Updates the table with new transactions, recalculating payment responsibilities per card.
     * @param transactions List of BudgetTransaction objects
     */
    public void setTransactions(List<BudgetTransaction> transactions) {
        logger.info("setTransactions called with {} transaction(s)", transactions == null ? 0 : transactions.size());
        tableModel.setRowCount(0);

        if (transactions == null || transactions.isEmpty()) {
            logger.warn("No transactions provided; summary table will be empty.");
            return;
        }

        // Find all unique payment methods (cards)
        Set<String> paymentMethods = transactions.stream()
                .map(BudgetTransaction::getPaymentMethod)
                .filter(Objects::nonNull)
                .filter(s -> !s.trim().isEmpty())
                .collect(Collectors.toCollection(TreeSet::new));

        logger.info("Detected payment methods: {}", paymentMethods);

        for (String card : paymentMethods) {
            double annaDirect = sumByAccountAndCard(transactions, "Anna", card);
            double joshDirect = sumByAccountAndCard(transactions, "Josh", card);
            double jointTotal = sumByAccountAndCard(transactions, "Joint", card);

            double annaTotal = annaDirect + jointTotal / 2.0;
            double joshTotal = joshDirect + jointTotal / 2.0;

            logger.info("Card '{}': Anna Direct = {}, Josh Direct = {}, Joint = {}, Anna Total = {}, Josh Total = {}",
                    card, annaDirect, joshDirect, jointTotal, annaTotal, joshTotal);

            tableModel.addRow(new Object[]{
                    card,
                    String.format("$%.2f", annaTotal),
                    String.format("$%.2f", joshTotal)
            });
        }
        logger.info("Summary table updated with {} row(s).", tableModel.getRowCount());
    }

    /**
     * Utility to sum transactions for a given account and card.
     */
    private double sumByAccountAndCard(List<BudgetTransaction> txs, String account, String card) {
        logger.debug("Summing transactions for account='{}', card='{}'", account, card);
        return txs.stream()
                .filter(tx -> account.equalsIgnoreCase(tx.getAccount()))
                .filter(tx -> card.equalsIgnoreCase(tx.getPaymentMethod()))
                .mapToDouble(BudgetTransaction::getAmountValue)
                .sum();
    }
}