package util;

import model.BudgetRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Converts between BudgetRow and CSV row representations.
 * Provides robust header-based mapping for all fields, supporting both Notion and working file column orders.
 * All conversions and map accesses are logged for debugging and traceability.
 */
public class BudgetRowConverter implements RowConverter<BudgetRow> {
    private static final Logger logger = LoggerFactory.getLogger(BudgetRowConverter.class);

    private static final List<String> HEADERS = Arrays.asList(
            "Name", "Amount", "Category", "Criticality", "Transaction Date",
            "Account", "status", "Created time", "Payment Method"
    );

    /**
     * Converts a CSV map (header to value) to a BudgetRow.
     * @param map The CSV row as a map.
     * @return The BudgetRow instance.
     */
    @Override
    public BudgetRow mapToRow(Map<String, String> map) {
        logger.info("Entering mapToRow with map: {}", map);
        if (map == null) {
            logger.error("Input map is null in mapToRow.");
            throw new IllegalArgumentException("Input map cannot be null");
        }
        BudgetRow row = new BudgetRow(
                map.getOrDefault("Name", ""),
                map.getOrDefault("Amount", ""),
                map.getOrDefault("Category", ""),
                map.getOrDefault("Criticality", ""),
                map.getOrDefault("Transaction Date", ""),
                map.getOrDefault("Account", ""),
                map.getOrDefault("status", ""),
                map.getOrDefault("Created time", ""),
                map.getOrDefault("Payment Method", "")
        );
        logger.info("mapToRow created BudgetRow: {}", row);
        return row;
    }

    /**
     * Converts a BudgetRow to a CSV map (header to value).
     * @param row The BudgetRow instance.
     * @return Map representation for CSV output.
     */
    @Override
    public Map<String, String> rowToMap(BudgetRow row) {
        logger.info("Entering rowToMap with row: {}", row);
        if (row == null) {
            logger.error("Input BudgetRow is null in rowToMap.");
            throw new IllegalArgumentException("Input BudgetRow cannot be null");
        }
        Map<String, String> map = new LinkedHashMap<>();
        map.put("Name", row.getName());
        map.put("Amount", row.getAmount());
        map.put("Category", row.getCategory());
        map.put("Criticality", row.getCriticality());
        map.put("Transaction Date", row.getTransactionDate());
        map.put("Account", row.getAccount());
        map.put("status", row.getStatus());
        map.put("Created time", row.getCreatedTime());
        map.put("Payment Method", row.getPaymentMethod());
        logger.info("rowToMap produced map: {}", map);
        return map;
    }

    /**
     * Gets the static headers list for CSV export.
     * @return List of header strings.
     */
    public static List<String> headers() {
        logger.info("headers() called, returning HEADERS list.");
        return HEADERS;
    }

    /**
     * Static utility for legacy compatibility: map to BudgetRow.
     * @param map The CSV row as a map.
     * @return The BudgetRow instance.
     */
    public static BudgetRow mapToBudgetRow(Map<String, String> map) {
        logger.info("mapToBudgetRow called with map: {}", map);
        return new BudgetRowConverter().mapToRow(map);
    }

    /**
     * Static utility for legacy compatibility: BudgetRow to map.
     * @param row The BudgetRow instance.
     * @return Map representation for CSV output.
     */
    public static Map<String, String> budgetRowToMap(BudgetRow row) {
        logger.info("budgetRowToMap called with row: {}", row);
        return new BudgetRowConverter().rowToMap(row);
    }

    /**
     * Gets the static headers list for CSV export (legacy alias).
     * @return List of header strings.
     */
    public static List<String> headersStatic() {
        logger.info("headersStatic() called, returning HEADERS list.");
        return HEADERS;
    }
}