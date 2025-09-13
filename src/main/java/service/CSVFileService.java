package service;

import org.springframework.stereotype.Service;
import java.util.List;

/**
 * Interface for CSV file services handling CRUD and validation operations.
 *
 * @param <T> The type of row this service manages (e.g., BudgetRow, ProjectedRow).
 */
@Service
public interface CSVFileService<T> {
    /**
     * Reads all rows from the CSV file.
     *
     * @return a list of all rows
     */
    List<T> readAll();

    /**
     * Appends a row to the CSV file.
     *
     * @param row the row to add
     */
    void add(T row);

    /**
     * Updates the first row matching key/value with updatedRow.
     *
     * @param key the field to match
     * @param value the value to match
     * @param updatedRow the new row data
     * @return true if an update occurred
     */
    boolean update(String key, String value, T updatedRow);

    /**
     * Deletes the first row matching key/value.
     *
     * @param key the field to match
     * @param value the value to match
     * @return true if a row was deleted
     */
    boolean delete(String key, String value);

    /**
     * Returns the CSV header fields in order.
     *
     * @return list of header names
     */
    List<String> getHeaders();

    /**
     * Ensures the CSV file exists and has the correct header.
     * Creates or repairs the file as needed.
     * Implementations should log all actions.
     */
    void ensureCsvFileReady();
}