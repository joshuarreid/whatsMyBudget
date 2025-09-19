package util;

import model.BudgetRow;
import model.ProjectedTransaction;
import org.slf4j.Logger;
import util.AppLogger;

import java.util.*;

/**
 * Converts between CSV map and ProjectedTransaction objects for projections file.
 * Provides robust handling and static headers.
 */
public class ProjectedRowConverter implements RowConverter<ProjectedTransaction> {
    private static final Logger logger = AppLogger.getLogger(ProjectedRowConverter.class);

    private static final List<String> HEADERS = Arrays.asList(
            "Name", "Amount", "Category", "Criticality", "Transaction Date",
            "Account", "status", "Created time", "Statement Period"
    );

    /**
     * Converts a CSV map to a ProjectedTransaction.
     * @param map CSV field map
     * @return ProjectedTransaction object
     */
    @Override
    public ProjectedTransaction mapToRow(Map<String, String> map) {
        logger.info("mapToRow called with map: {}", map);
        try {
            ProjectedTransaction tx = new ProjectedTransaction(
                    map.getOrDefault("Name", ""),
                    map.getOrDefault("Amount", ""),
                    map.getOrDefault("Category", ""),
                    map.getOrDefault("Criticality", ""),
                    map.getOrDefault("Transaction Date", ""),
                    map.getOrDefault("Account", ""),
                    map.getOrDefault("status", ""),
                    map.getOrDefault("Created time", ""),
                    map.getOrDefault("Statement Period", "")
            );
            logger.info("Successfully mapped to ProjectedTransaction: {}", tx);
            return tx;
        } catch (Exception e) {
            logger.error("Failed to map mapToRow in ProjectedRowConverter: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Converts a ProjectedTransaction to a CSV map.
     * @param tx ProjectedTransaction object
     * @return Map of CSV fields
     */
    @Override
    public Map<String, String> rowToMap(ProjectedTransaction tx) {
        logger.info("rowToMap called for ProjectedTransaction: {}", tx);
        Map<String, String> map = new LinkedHashMap<>();
        map.put("Name", tx.getName());
        map.put("Amount", tx.getAmount());
        map.put("Category", tx.getCategory());
        map.put("Criticality", tx.getCriticality());
        map.put("Transaction Date", tx.getTransactionDate());
        map.put("Account", tx.getAccount());
        map.put("status", tx.getStatus());
        map.put("Created time", tx.getCreatedTime());
        map.put("Statement Period", tx.getStatementPeriod());
        return map;
    }

    /**
     * Returns the headers for projections CSV.
     */
    public static List<String> headers() {
        return HEADERS;
    }

    // Static utility for compatibility
    public static ProjectedTransaction mapToProjectedTransaction(Map<String, String> map) {
        return new ProjectedRowConverter().mapToRow(map);
    }

    public static Map<String, String> projectedRowToMap(ProjectedTransaction tx) {
        return new ProjectedRowConverter().rowToMap(tx);
    }

    /**
     * Converts a CSV map to a BudgetRow (specifically a ProjectedTransaction).
     * @param map CSV field map
     * @return BudgetRow object (ProjectedTransaction)
     */
    public static BudgetRow mapToBudgetRow(Map<String, String> map) {
        logger.info("mapToBudgetRow called with map: {}", map);
        try {
            ProjectedTransaction tx = mapToProjectedTransaction(map);
            logger.info("Successfully mapped to BudgetRow (ProjectedTransaction): {}", tx);
            return tx;
        } catch (Exception e) {
            logger.error("Failed to map mapToBudgetRow in ProjectedRowConverter: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Converts a BudgetRow (specifically a ProjectedTransaction) to a CSV map.
     * @param row BudgetRow object
     * @return Map of CSV fields
     */
    public static Map<String, String> budgetRowToMap(BudgetRow row) {
        logger.info("budgetRowToMap called for BudgetRow: {}", row);
        if (!(row instanceof ProjectedTransaction)) {
            logger.error("budgetRowToMap called with non-ProjectedTransaction BudgetRow: {}", row);
            return Collections.emptyMap();
        }
        return projectedRowToMap((ProjectedTransaction) row);
    }
}