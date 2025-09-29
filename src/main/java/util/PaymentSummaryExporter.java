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
 * Output columns: Card,Anna Payment,Josh Payment, followed by a detailed breakdown by person/card/category.
 * Robust logging and error handling included.
 */
public class PaymentSummaryExporter {
    private static final Logger logger = LoggerFactory.getLogger(PaymentSummaryExporter.class);

    /**
     * Exports a payment summary CSV for the given transactions, including a total row at the end
     * and a detailed breakdown by person/card/category.
     * The CSV will have columns: Card,Anna Payment,Josh Payment, and a final Total row.
     * Then it will have: Person,Card,Category,Amount breakdown.
     * Payments are calculated as:
     *  - Anna: sum of Anna's txns for card + half of Joint txns for card
     *  - Josh: sum of Josh's txns for card + half of Joint txns for card
     *
     * @param transactions The list of BudgetTransaction (must not be null)
     * @param outputFile   The destination CSV file (will be overwritten if exists)
     * @throws IllegalArgumentException if transactions or outputFile is null
     */
    public static void exportPaymentSummaryToCSV(List<BudgetTransaction> transactions, File outputFile) {
        logger.info("exportPaymentSummaryToCSVWithTotalRowAndBreakdown called. Output path: '{}'", outputFile == null ? "null" : outputFile.getAbsolutePath());

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
        List<String[]> summaryRows = new ArrayList<>();
        summaryRows.add(new String[]{"Card", "Anna Payment", "Josh Payment"}); // header row

        double annaGrandTotal = 0.0;
        double joshGrandTotal = 0.0;

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

            annaGrandTotal += annaTotal;
            joshGrandTotal += joshTotal;

            logger.info("Card '{}': Anna tx = {}, Josh tx = {}, Joint tx = {}. Anna total = {}, Josh total = {}",
                    card, annaTx, joshTx, jointTx, annaTotal, joshTotal);

            summaryRows.add(new String[]{
                    card,
                    String.format("%.2f", annaTotal),
                    String.format("%.2f", joshTotal)
            });
        }

        // Add total row at the bottom
        summaryRows.add(new String[]{
                "Total",
                String.format("%.2f", annaGrandTotal),
                String.format("%.2f", joshGrandTotal)
        });
        logger.info("Grand Total row: Anna = {}, Josh = {}", annaGrandTotal, joshGrandTotal);

        // Prepare breakdown rows: Person, Card, Category, Amount
        List<String[]> breakdownRows = new ArrayList<>();
        breakdownRows.add(new String[]{"Person", "Card", "Category", "Amount"}); // header

        // For each card and person, calculate per-category breakdown. Joint transactions are split.
        String[] people = new String[]{"Anna", "Josh"};
        for (String card : cards) {
            for (String person : people) {
                // Own transactions
                Map<String, Double> ownCategoryTotals = transactions.stream()
                        .filter(tx -> person.equalsIgnoreCase(tx.getAccount()))
                        .filter(tx -> card.equalsIgnoreCase(tx.getPaymentMethod()))
                        .collect(Collectors.groupingBy(
                                tx -> tx.getCategory() == null ? "(Uncategorized)" : tx.getCategory(),
                                Collectors.summingDouble(BudgetTransaction::getAmountValue)
                        ));

                // Joint transactions (half value)
                Map<String, Double> jointCategoryTotals = transactions.stream()
                        .filter(tx -> "Joint".equalsIgnoreCase(tx.getAccount()))
                        .filter(tx -> card.equalsIgnoreCase(tx.getPaymentMethod()))
                        .collect(Collectors.groupingBy(
                                tx -> tx.getCategory() == null ? "(Uncategorized)" : tx.getCategory(),
                                Collectors.summingDouble(tx -> tx.getAmountValue() / 2.0)
                        ));

                // Merge own and joint breakdowns
                Map<String, Double> mergedTotals = new TreeMap<>(ownCategoryTotals);
                for (Map.Entry<String, Double> entry : jointCategoryTotals.entrySet()) {
                    mergedTotals.merge(entry.getKey(), entry.getValue(), Double::sum);
                }

                // Write each category breakdown row
                for (Map.Entry<String, Double> entry : mergedTotals.entrySet()) {
                    breakdownRows.add(new String[]{
                            person,
                            card,
                            entry.getKey(),
                            String.format("%.2f", entry.getValue())
                    });
                    logger.debug("Breakdown row: person={}, card={}, category={}, amount={}",
                            person, card, entry.getKey(), entry.getValue());
                }
            }
        }

        // Write to CSV file: summary section, then a blank line, then breakdown section
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile))) {
            for (String[] row : summaryRows) {
                writer.write(String.join(",", row));
                writer.newLine();
                logger.debug("Wrote summary row to CSV: {}", Arrays.toString(row));
            }
            writer.newLine(); // blank line separator

            for (String[] row : breakdownRows) {
                writer.write(String.join(",", row));
                writer.newLine();
                logger.debug("Wrote breakdown row to CSV: {}", Arrays.toString(row));
            }
            logger.info("Payment summary CSV with total row and breakdown successfully written to '{}'.", outputFile.getAbsolutePath());
        } catch (IOException e) {
            logger.error("Failed to write payment summary CSV to '{}': {}", outputFile.getAbsolutePath(), e.getMessage(), e);
            throw new RuntimeException("Failed to write payment summary CSV: " + e.getMessage(), e);
        }
    }
}