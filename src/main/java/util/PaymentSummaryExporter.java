package util;

import model.BudgetTransaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Utility class for exporting a payment summary CSV for each card,
 * showing Anna's and Josh's payment responsibilities (with joint transactions split).
 * Output columns: Card,Anna Payment,Josh Payment
 * Robust logging and error handling included.
 */
public class PaymentSummaryExporter {
    private static final Logger logger = LoggerFactory.getLogger(PaymentSummaryExporter.class);

    /**
     * Exports a payment summary CSV for the given transactions.
     * The CSV will have columns: Card,Anna Payment,Josh Payment.
     * Payments are calculated as:
     *  - Anna: sum of Anna's txns for card + half of Joint txns for card
     *  - Josh: sum of Josh's txns for card + half of Joint txns for card
     *
     * @param transactions The list of BudgetTransaction (must not be null)
     * @param outputFile   The destination CSV file (will be overwritten if exists)
     * @throws IllegalArgumentException if transactions or outputFile is null
     */
    public static void exportPaymentSummaryToCSV(List<BudgetTransaction> transactions, File outputFile) {
        logger.info("exportPaymentSummaryToCSV called. Output path: '{}'", outputFile == null ? "null" : outputFile.getAbsolutePath());

        if (transactions == null) {
            logger.error("Cannot export payment summary: transactions is null.");
            throw new IllegalArgumentException("transactions must not be null");
        }
        if (outputFile == null) {
            logger.error("Cannot export payment summary: outputFile is null.");
            throw new IllegalArgumentException("outputFile must not be null");
        }

        // Find all unique cards/payment methods
        Set<String> cards = transactions.stream()
                .map(BudgetTransaction::getPaymentMethod)
                .filter(Objects::nonNull)
                .filter(s -> !s.trim().isEmpty())
                .collect(Collectors.toCollection(TreeSet::new));
        logger.info("Detected {} unique card(s): {}", cards.size(), cards);

        // Prepare summary rows
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"Card", "Anna Payment", "Josh Payment"}); // header row

        for (String card : cards) {
            double annaTotal = 0.0;
            double joshTotal = 0.0;

            // Anna transactions (full amount)
            double annaTx = transactions.stream()
                    .filter(tx -> "Anna".equalsIgnoreCase(tx.getAccount()))
                    .filter(tx -> card.equalsIgnoreCase(tx.getPaymentMethod()))
                    .mapToDouble(BudgetTransaction::getAmountValue)
                    .sum();

            // Josh transactions (full amount)
            double joshTx = transactions.stream()
                    .filter(tx -> "Josh".equalsIgnoreCase(tx.getAccount()))
                    .filter(tx -> card.equalsIgnoreCase(tx.getPaymentMethod()))
                    .mapToDouble(BudgetTransaction::getAmountValue)
                    .sum();

            // Joint transactions (split half to each)
            double jointTx = transactions.stream()
                    .filter(tx -> "Joint".equalsIgnoreCase(tx.getAccount()))
                    .filter(tx -> card.equalsIgnoreCase(tx.getPaymentMethod()))
                    .mapToDouble(BudgetTransaction::getAmountValue)
                    .sum();

            annaTotal = annaTx + (jointTx / 2.0);
            joshTotal = joshTx + (jointTx / 2.0);

            logger.info("Card '{}': Anna tx = {}, Josh tx = {}, Joint tx = {}. Anna total = {}, Josh total = {}",
                    card, annaTx, joshTx, jointTx, annaTotal, joshTotal);

            rows.add(new String[]{
                    card,
                    String.format("%.2f", annaTotal),
                    String.format("%.2f", joshTotal)
            });
        }

        // Write to CSV file
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile))) {
            for (String[] row : rows) {
                writer.write(String.join(",", row));
                writer.newLine();
            }
            logger.info("Payment summary CSV successfully written to '{}'.", outputFile.getAbsolutePath());
        } catch (IOException e) {
            logger.error("Failed to write payment summary CSV to '{}': {}", outputFile.getAbsolutePath(), e.getMessage(), e);
            throw new RuntimeException("Failed to write payment summary CSV: " + e.getMessage(), e);
        }
    }
}