package util;

import java.util.List;
import java.util.Map;

/**
 * Generic interface for converting between CSV maps and row objects.
 * @param <T> The row object type (e.g., BudgetRow, ProjectedRow)
 */
public interface RowConverter<T> {
    /**
     * Convert a CSV map to a row object.
     * @param map The map of CSV headers to values.
     * @return The row object.
     */
    T mapToRow(Map<String, String> map);

    /**
     * Convert a row object to a CSV map.
     * @param row The row object.
     * @return The map of CSV headers to values.
     */
    Map<String, String> rowToMap(T row);

}