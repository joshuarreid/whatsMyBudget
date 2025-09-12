package util;

import model.BudgetRow;

import java.util.*;

public class BudgetRowConverter implements RowConverter<BudgetRow> {
    private static final List<String> HEADERS = Arrays.asList(
            "Name", "Amount", "Category", "Criticality", "Transaction Date",
            "Account", "status", "Created time", "Payment Method"
    );

    // Convert a CSV map to BudgetRow
    @Override
    public BudgetRow mapToRow(Map<String, String> map) {
        return new BudgetRow(
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
    }

    // Convert a BudgetRow to a CSV map
    @Override
    public Map<String, String> rowToMap(BudgetRow row) {
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
        return map;
    }


    public static List<String> headers() {
        return HEADERS;
    }

    // Static utility for legacy compatibility
    public static BudgetRow mapToBudgetRow(Map<String, String> map) {
        return new BudgetRowConverter().mapToRow(map);
    }

    public static Map<String, String> budgetRowToMap(BudgetRow row) {
        return new BudgetRowConverter().rowToMap(row);
    }

    public static List<String> headersStatic() {
        return HEADERS;
    }
}