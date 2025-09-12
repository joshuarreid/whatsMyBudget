package service;

import com.opencsv.exceptions.CsvValidationException;
import model.BudgetRow;
import org.springframework.stereotype.Service;
import util.BudgetRowConverter;
import util.AppLogger;
import org.slf4j.Logger;
import com.opencsv.*;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Implements CSVFileService for BudgetRow objects.
 */
@Service
public class BudgetFileService implements CSVFileService<BudgetRow> {
    private static final Logger logger = AppLogger.getLogger(BudgetFileService.class);

    private final String filePath;
    private final List<String> headers = BudgetRowConverter.headers();

    public BudgetFileService(String filePath) {
        this.filePath = filePath;
    }

    @Override
    public List<BudgetRow> readAll() {
        List<BudgetRow> rows = new ArrayList<>();
        try (Reader reader = Files.newBufferedReader(Paths.get(filePath))) {
            CSVReaderHeaderAware csvReader = new CSVReaderHeaderAware(reader);
            Map<String, String> map;
            while ((map = csvReader.readMap()) != null) {
                BudgetRow budgetRow = BudgetRowConverter.mapToBudgetRow(map);
                rows.add(budgetRow);
            }
            logger.info("Read {} rows from CSV file '{}'", rows.size(), filePath);
        } catch (IOException e) {
            logger.error("Failed to read CSV file '{}': {}", filePath, e.getMessage());
        } catch (CsvValidationException e) {
            throw new RuntimeException(e);
        }
        return rows;
    }

    @Override
    public void add(BudgetRow transaction) {
        boolean fileExists = Files.exists(Paths.get(filePath));
        try (
                Writer writer = new FileWriter(filePath, true);
                CSVWriter csvWriter = new CSVWriter(writer,
                        CSVWriter.DEFAULT_SEPARATOR,
                        CSVWriter.DEFAULT_QUOTE_CHARACTER,
                        CSVWriter.DEFAULT_ESCAPE_CHARACTER,
                        CSVWriter.DEFAULT_LINE_END)
        ) {
            if (!fileExists) {
                csvWriter.writeNext(headers.toArray(new String[0]));
            }
            Map<String, String> map = BudgetRowConverter.budgetRowToMap(transaction);
            String[] values = headers.stream()
                    .map(h -> map.getOrDefault(h, ""))
                    .toArray(String[]::new);
            csvWriter.writeNext(values);
            logger.info("Added new row to CSV file '{}': {}", filePath, transaction);
        } catch (IOException e) {
            logger.error("Failed to add row to CSV file '{}': {}", filePath, e.getMessage());
        }
    }

    @Override
    public boolean update(String key, String value, BudgetRow updatedRow) {
        List<BudgetRow> all = readAll();
        boolean updated = false;
        for (int i = 0; i < all.size(); i++) {
            BudgetRow row = all.get(i);
            Map<String, String> rowMap = BudgetRowConverter.budgetRowToMap(row);
            if (rowMap.getOrDefault(key, "").equals(value)) {
                all.set(i, updatedRow);
                updated = true;
                logger.info("Updated row with {}={} in CSV file '{}'", key, value, filePath);
                break;
            }
        }
        if (updated) {
            writeAll(all);
        } else {
            logger.warn("No row found with {}={} to update in CSV file '{}'", key, value, filePath);
        }
        return updated;
    }

    @Override
    public boolean delete(String key, String value) {
        List<BudgetRow> all = readAll();
        int originalSize = all.size();
        List<BudgetRow> newRows = all.stream()
                .filter(row -> {
                    Map<String, String> rowMap = BudgetRowConverter.budgetRowToMap(row);
                    return !rowMap.getOrDefault(key, "").equals(value);
                })
                .collect(Collectors.toList());
        if (newRows.size() < originalSize) {
            writeAll(newRows);
            logger.info("Deleted row with {}={} from CSV file '{}'", key, value, filePath);
            return true;
        } else {
            logger.warn("No row found with {}={} to delete in CSV file '{}'", key, value, filePath);
            return false;
        }
    }

    @Override
    public List<String> getHeaders() {
        return new ArrayList<>(headers);
    }

    private void writeAll(List<BudgetRow> rows) {
        try (
                Writer writer = new FileWriter(filePath);
                CSVWriter csvWriter = new CSVWriter(writer,
                        CSVWriter.DEFAULT_SEPARATOR,
                        CSVWriter.DEFAULT_QUOTE_CHARACTER,
                        CSVWriter.DEFAULT_ESCAPE_CHARACTER,
                        CSVWriter.DEFAULT_LINE_END)
        ) {
            csvWriter.writeNext(headers.toArray(new String[0]));
            for (BudgetRow row : rows) {
                Map<String, String> map = BudgetRowConverter.budgetRowToMap(row);
                String[] values = headers.stream()
                        .map(h -> map.getOrDefault(h, ""))
                        .toArray(String[]::new);
                csvWriter.writeNext(values);
            }
            logger.info("Wrote {} rows to CSV file '{}'", rows.size(), filePath);
        } catch (IOException e) {
            logger.error("Failed to write to CSV file '{}': {}", filePath, e.getMessage());
        }
    }
}