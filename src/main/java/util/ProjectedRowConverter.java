package util;

import model.ProjectedRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Converter for ProjectedRow objects and CSV map representations.
 */
public class ProjectedRowConverter implements RowConverter<ProjectedRow> {
    private static final Logger logger = LoggerFactory.getLogger(ProjectedRowConverter.class);

    private static final List<String> HEADERS = Arrays.asList(
            "Name", "Amount", "Category", "Criticality", "Projected Date",
            "Account", "status", "Created time", "Payment Method", "Notes"
    );

    @Override
    public ProjectedRow mapToRow(Map<String, String> map) {
        ProjectedRow row = new ProjectedRow(
                map.getOrDefault("Name", ""),
                map.getOrDefault("Amount", ""),
                map.getOrDefault("Category", ""),
                map.getOrDefault("Criticality", ""),
                map.getOrDefault("Projected Date", ""),
                map.getOrDefault("Account", ""),
                map.getOrDefault("status", ""),
                map.getOrDefault("Created time", ""),
                map.getOrDefault("Payment Method", ""),
                map.getOrDefault("Notes", "")
        );
        logger.debug("Mapped CSV map to ProjectedRow: {}", row);
        return row;
    }

    @Override
    public Map<String, String> rowToMap(ProjectedRow row) {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("Name", row.getName());
        map.put("Amount", row.getAmount());
        map.put("Category", row.getCategory());
        map.put("Criticality", row.getCriticality());
        map.put("Projected Date", row.getProjectedDate());
        map.put("Account", row.getAccount());
        map.put("status", row.getStatus());
        map.put("Created time", row.getCreatedTime());
        map.put("Payment Method", row.getPaymentMethod());
        map.put("Notes", row.getNotes());
        logger.debug("Mapped ProjectedRow to CSV map: {}", map);
        return map;
    }

    public List<String> headers() {
        logger.debug("Retrieved ProjectedRow headers: {}", HEADERS);
        return HEADERS;
    }

    // Static utility methods for compatibility with static-style usage
    public static ProjectedRow mapToProjectedRow(Map<String, String> map) {
        ProjectedRow row = new ProjectedRowConverter().mapToRow(map);
        logger.debug("Static: Mapped CSV map to ProjectedRow: {}", row);
        return row;
    }

    public static Map<String, String> projectedRowToMap(ProjectedRow row) {
        Map<String, String> map = new ProjectedRowConverter().rowToMap(row);
        logger.debug("Static: Mapped ProjectedRow to CSV map: {}", map);
        return map;
    }

    public static List<String> headersStatic() {
        logger.debug("Static: Retrieved ProjectedRow headers: {}", HEADERS);
        return HEADERS;
    }
}