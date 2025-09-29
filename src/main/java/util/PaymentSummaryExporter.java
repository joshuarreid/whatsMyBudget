package util;

import model.BudgetTransaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Utility class for exporting a payment summary PDF for each card,
 * showing Anna's and Josh's payment responsibilities (with joint transactions split),
 * and for each card, side-by-side category breakdown tables matching the UI.
 * Each card's breakdown is placed on a separate PDF page.
 * Robust logging and error handling included.
 */
public class PaymentSummaryExporter {
    private static final Logger logger = LoggerFactory.getLogger(PaymentSummaryExporter.class);

    /**
     * Exports a payment summary PDF matching the payments screen UI.
     * Each card's breakdown is rendered on a separate PDF page.
     * The PDF shows:
     * - Top summary table: Card | Anna Payment | Josh Payment
     * - For each card, a section with Anna and Josh breakdown tables side by side,
     *   each with headers ("Category", "Total Amount", "Type") and a TOTAL row.
     *
     * @param transactions The list of BudgetTransaction (must not be null)
     * @param outputFile   The destination PDF file (will be overwritten if exists)
     */
    public static void exportPaymentSummaryToPDF(List<BudgetTransaction> transactions, File outputFile) {
        logger.info("exportPaymentSummaryToPDF called. Output path: '{}'", outputFile == null ? "null" : outputFile.getAbsolutePath());

        if (transactions == null) {
            logger.error("Cannot export payment summary PDF: transactions is null.");
            throw new IllegalArgumentException("transactions must not be null");
        }
        if (outputFile == null) {
            logger.error("Cannot export payment summary PDF: outputFile is null.");
            throw new IllegalArgumentException("outputFile must not be null");
        }

        Set<String> cards = transactions.stream()
                .map(BudgetTransaction::getPaymentMethod)
                .filter(Objects::nonNull)
                .filter(s -> !s.trim().isEmpty())
                .collect(Collectors.toCollection(TreeSet::new));
        logger.info("Detected {} unique card(s): {}", cards.size(), cards);

        List<String[]> summaryRows = new ArrayList<>();
        summaryRows.add(new String[]{"Card", "Anna Payment", "Josh Payment"}); // header row

        double annaGrandTotal = 0.0;
        double joshGrandTotal = 0.0;

        Map<String, Double> annaTotalsByCard = new LinkedHashMap<>();
        Map<String, Double> joshTotalsByCard = new LinkedHashMap<>();
        Map<String, LinkedHashMap<String, Double>> annaBreakdown = new LinkedHashMap<>();
        Map<String, LinkedHashMap<String, Double>> joshBreakdown = new LinkedHashMap<>();

        for (String card : cards) {
            double annaTotal = 0.0;
            double joshTotal = 0.0;

            double annaTx = transactions.stream()
                    .filter(tx -> "Anna".equalsIgnoreCase(tx.getAccount()))
                    .filter(tx -> card.equalsIgnoreCase(tx.getPaymentMethod()))
                    .mapToDouble(BudgetTransaction::getAmountValue)
                    .sum();

            double joshTx = transactions.stream()
                    .filter(tx -> "Josh".equalsIgnoreCase(tx.getAccount()))
                    .filter(tx -> card.equalsIgnoreCase(tx.getPaymentMethod()))
                    .mapToDouble(BudgetTransaction::getAmountValue)
                    .sum();

            double jointTx = transactions.stream()
                    .filter(tx -> "Joint".equalsIgnoreCase(tx.getAccount()))
                    .filter(tx -> card.equalsIgnoreCase(tx.getPaymentMethod()))
                    .mapToDouble(BudgetTransaction::getAmountValue)
                    .sum();

            annaTotal = annaTx + (jointTx / 2.0);
            joshTotal = joshTx + (jointTx / 2.0);

            annaGrandTotal += annaTotal;
            joshGrandTotal += joshTotal;

            annaTotalsByCard.put(card, annaTotal);
            joshTotalsByCard.put(card, joshTotal);

            summaryRows.add(new String[]{
                    card,
                    String.format("$%.2f", annaTotal),
                    String.format("$%.2f", joshTotal)
            });

            // Anna breakdown by category for this card
            LinkedHashMap<String, Double> annaCatTotals = transactions.stream()
                    .filter(tx -> "Anna".equalsIgnoreCase(tx.getAccount()))
                    .filter(tx -> card.equalsIgnoreCase(tx.getPaymentMethod()))
                    .collect(Collectors.groupingBy(
                            tx -> tx.getCategory() == null ? "(Uncategorized)" : tx.getCategory(),
                            LinkedHashMap::new,
                            Collectors.summingDouble(BudgetTransaction::getAmountValue)
                    ));
            LinkedHashMap<String, Double> jointCatTotalsAnna = transactions.stream()
                    .filter(tx -> "Joint".equalsIgnoreCase(tx.getAccount()))
                    .filter(tx -> card.equalsIgnoreCase(tx.getPaymentMethod()))
                    .collect(Collectors.groupingBy(
                            tx -> tx.getCategory() == null ? "(Uncategorized)" : tx.getCategory(),
                            LinkedHashMap::new,
                            Collectors.summingDouble(tx -> tx.getAmountValue() / 2.0)
                    ));
            for (Map.Entry<String, Double> entry : jointCatTotalsAnna.entrySet()) {
                annaCatTotals.merge(entry.getKey(), entry.getValue(), Double::sum);
            }
            annaBreakdown.put(card, annaCatTotals);

            // Josh breakdown by category for this card
            LinkedHashMap<String, Double> joshCatTotals = transactions.stream()
                    .filter(tx -> "Josh".equalsIgnoreCase(tx.getAccount()))
                    .filter(tx -> card.equalsIgnoreCase(tx.getPaymentMethod()))
                    .collect(Collectors.groupingBy(
                            tx -> tx.getCategory() == null ? "(Uncategorized)" : tx.getCategory(),
                            LinkedHashMap::new,
                            Collectors.summingDouble(BudgetTransaction::getAmountValue)
                    ));
            LinkedHashMap<String, Double> jointCatTotalsJosh = transactions.stream()
                    .filter(tx -> "Joint".equalsIgnoreCase(tx.getAccount()))
                    .filter(tx -> card.equalsIgnoreCase(tx.getPaymentMethod()))
                    .collect(Collectors.groupingBy(
                            tx -> tx.getCategory() == null ? "(Uncategorized)" : tx.getCategory(),
                            LinkedHashMap::new,
                            Collectors.summingDouble(tx -> tx.getAmountValue() / 2.0)
                    ));
            for (Map.Entry<String, Double> entry : jointCatTotalsJosh.entrySet()) {
                joshCatTotals.merge(entry.getKey(), entry.getValue(), Double::sum);
            }
            joshBreakdown.put(card, joshCatTotals);
        }

        summaryRows.add(new String[]{
                "Total",
                String.format("$%.2f", annaGrandTotal),
                String.format("$%.2f", joshGrandTotal)
        });

        logger.info("Grand Total row: Anna = {}, Josh = {}", annaGrandTotal, joshGrandTotal);

        try (PDDocument document = new PDDocument()) {
            // First page: summary table
            PDPage summaryPage = new PDPage();
            document.addPage(summaryPage);
            PDPageContentStream content = new PDPageContentStream(document, summaryPage);

            float margin = 40;
            float yStart = summaryPage.getMediaBox().getHeight() - margin;
            float xStart = margin;
            float y = yStart;
            float rowHeight = 18;
            float fontSize = 12;
            float tableColSpacing = 120;

            // Title
            content.beginText();
            content.setFont(PDType1Font.HELVETICA_BOLD, fontSize + 2);
            content.newLineAtOffset(xStart, y);
            content.showText("Payment Summary");
            content.endText();
            y -= rowHeight * 2;

            // Draw summary table
            content.setFont(PDType1Font.HELVETICA_BOLD, fontSize);
            for (int col = 0; col < summaryRows.get(0).length; col++) {
                content.beginText();
                content.newLineAtOffset(xStart + col * tableColSpacing, y);
                content.showText(summaryRows.get(0)[col]);
                content.endText();
            }
            y -= rowHeight;

            content.setFont(PDType1Font.HELVETICA, fontSize);
            for (int i = 1; i < summaryRows.size(); i++) {
                for (int col = 0; col < summaryRows.get(i).length; col++) {
                    content.beginText();
                    content.newLineAtOffset(xStart + col * tableColSpacing, y);
                    content.showText(summaryRows.get(i)[col]);
                    content.endText();
                }
                y -= rowHeight;
            }
            content.close();

            // Each card on its own page
            for (String card : cards) {
                PDPage cardPage = new PDPage();
                document.addPage(cardPage);
                PDPageContentStream cardContent = new PDPageContentStream(document, cardPage);

                y = cardPage.getMediaBox().getHeight() - margin;
                float annaTableX = xStart;
                float joshTableX = xStart + 230;

                // Card heading
                cardContent.setFont(PDType1Font.HELVETICA_BOLD, fontSize + 1);
                cardContent.beginText();
                cardContent.newLineAtOffset(xStart, y);
                cardContent.showText(card + " - Category Breakdown");
                cardContent.endText();
                y -= rowHeight;

                // Draw Anna and Josh header
                cardContent.setFont(PDType1Font.HELVETICA_BOLD, fontSize);
                cardContent.beginText();
                cardContent.newLineAtOffset(annaTableX, y);
                cardContent.showText("Anna");
                cardContent.endText();
                cardContent.beginText();
                cardContent.newLineAtOffset(joshTableX, y);
                cardContent.showText("Josh");
                cardContent.endText();
                y -= rowHeight;

                // Table headers
                String[] tableHeaders = {"Category", "Total Amount", "Type"};
                for (int col = 0; col < tableHeaders.length; col++) {
                    cardContent.beginText();
                    cardContent.setFont(PDType1Font.HELVETICA_BOLD, fontSize);
                    cardContent.newLineAtOffset(annaTableX + col * 80, y);
                    cardContent.showText(tableHeaders[col]);
                    cardContent.endText();

                    cardContent.beginText();
                    cardContent.setFont(PDType1Font.HELVETICA_BOLD, fontSize);
                    cardContent.newLineAtOffset(joshTableX + col * 80, y);
                    cardContent.showText(tableHeaders[col]);
                    cardContent.endText();
                }
                y -= rowHeight;

                List<Map.Entry<String, Double>> annaEntries = new ArrayList<>(annaBreakdown.get(card).entrySet());
                List<Map.Entry<String, Double>> joshEntries = new ArrayList<>(joshBreakdown.get(card).entrySet());
                int maxRows = Math.max(annaEntries.size(), joshEntries.size());

                for (int i = 0; i < maxRows; i++) {
                    // Anna's row
                    String annaCategory = i < annaEntries.size() ? annaEntries.get(i).getKey() : "";
                    String annaAmount = i < annaEntries.size() ? String.format("$%.2f", annaEntries.get(i).getValue()) : "";
                    String annaType = (i < annaEntries.size() && annaEntries.get(i).getValue() >= 0) ? "Actual" : "";

                    cardContent.setFont(PDType1Font.HELVETICA, fontSize);
                    cardContent.beginText();
                    cardContent.newLineAtOffset(annaTableX, y);
                    cardContent.showText(annaCategory);
                    cardContent.endText();
                    cardContent.beginText();
                    cardContent.newLineAtOffset(annaTableX + 80, y);
                    cardContent.showText(annaAmount);
                    cardContent.endText();
                    cardContent.beginText();
                    cardContent.newLineAtOffset(annaTableX + 160, y);
                    cardContent.showText(annaType);
                    cardContent.endText();

                    // Josh's row
                    String joshCategory = i < joshEntries.size() ? joshEntries.get(i).getKey() : "";
                    String joshAmount = i < joshEntries.size() ? String.format("$%.2f", joshEntries.get(i).getValue()) : "";
                    String joshType = (i < joshEntries.size() && joshEntries.get(i).getValue() >= 0) ? "Actual" : "";

                    cardContent.setFont(PDType1Font.HELVETICA, fontSize);
                    cardContent.beginText();
                    cardContent.newLineAtOffset(joshTableX, y);
                    cardContent.showText(joshCategory);
                    cardContent.endText();
                    cardContent.beginText();
                    cardContent.newLineAtOffset(joshTableX + 80, y);
                    cardContent.showText(joshAmount);
                    cardContent.endText();
                    cardContent.beginText();
                    cardContent.newLineAtOffset(joshTableX + 160, y);
                    cardContent.showText(joshType);
                    cardContent.endText();

                    y -= rowHeight;
                }

                // Totals row, bold and with extra space below
                cardContent.setFont(PDType1Font.HELVETICA_BOLD, fontSize);
                cardContent.beginText();
                cardContent.newLineAtOffset(annaTableX, y);
                cardContent.showText("TOTAL");
                cardContent.endText();
                cardContent.beginText();
                cardContent.newLineAtOffset(annaTableX + 80, y);
                cardContent.showText(String.format("$%.2f", annaTotalsByCard.get(card)));
                cardContent.endText();
                cardContent.beginText();
                cardContent.newLineAtOffset(annaTableX + 160, y);
                cardContent.showText("");
                cardContent.endText();

                cardContent.beginText();
                cardContent.newLineAtOffset(joshTableX, y);
                cardContent.showText("TOTAL");
                cardContent.endText();
                cardContent.beginText();
                cardContent.newLineAtOffset(joshTableX + 80, y);
                cardContent.showText(String.format("$%.2f", joshTotalsByCard.get(card)));
                cardContent.endText();
                cardContent.beginText();
                cardContent.newLineAtOffset(joshTableX + 160, y);
                cardContent.showText("");
                cardContent.endText();

                logger.info("Finished rendering breakdown for card '{}'", card);

                y -= rowHeight * 2; // extra space after totals so next page's header never overlaps

                cardContent.close();
            }

            document.save(outputFile);
            logger.info("Payment summary PDF successfully written to '{}'.", outputFile.getAbsolutePath());
        } catch (IOException e) {
            logger.error("Failed to write payment summary PDF to '{}': {}", outputFile.getAbsolutePath(), e.getMessage(), e);
            throw new RuntimeException("Failed to write payment summary PDF: " + e.getMessage(), e);
        }
    }
}