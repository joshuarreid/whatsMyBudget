package service;

import com.opencsv.exceptions.CsvValidationException;
import model.BudgetRow;
import model.BudgetTransaction;
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
 * Provides robust file creation and header validation.
 *
 * Only the following columns are used for the working budget CSV:
 * Name,Amount,Category,Criticality,Transaction Date,Account,status,Created time,Payment Method
 * (No "Statement Period" column for transaction CSVs.)
 */
@Service
public class BudgetFileService implements CSVFileService<BudgetRow> {
    private static final Logger logger = AppLogger.getLogger(BudgetFileService.class);

    private final String filePath;
    // This must exactly match the columns in your working CSV file.
    private final List<String> headers = Arrays.asList(
            "Name","Amount","Category","Criticality","Transaction Date","Account","status","Created time","Payment Method"
    );

    public BudgetFileService(String filePath) {
        this.filePath = filePath;
        logger.info("BudgetFileService initialized with filePath={}", filePath);
    }

    /**
     * Ensures the CSV file exists and has the correct header.
     * Creates or repairs the file as needed.
     */
    @Override
    public void ensureCsvFileReady() {
        logger.info("ensureCsvFileReady called for filePath={}", filePath);
        File file = new File(filePath);
        try {
            if (!file.exists()) {
                boolean created = file.createNewFile();
                if (created) {
                    logger.info("Created new budget CSV at {}", filePath);
                    writeCsvHeader(file);
                } else {
                    logger.warn("Budget CSV file was not created (may already exist): {}", filePath);
                }
            }
            ensureCsvHeader(file);
        } catch (IOException e) {
            logger.error("Failed to check or write header to CSV file: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to ensure header in budget CSV", e);
        }
    }

    /**
     * Writes the standard CSV header row to the provided file.
     */
    private void writeCsvHeader(File file) throws IOException {
        try (FileWriter writer = new FileWriter(file, false)) {
            writer.write(String.join(",", headers));
            writer.write(System.lineSeparator());
            logger.info("Wrote header row to CSV file: {}", file.getAbsolutePath());
        }
    }

    /**
     * Ensures the CSV file has the correct header row as the first line.
     * If the file is empty or header is missing/invalid, writes the correct header and keeps data rows.
     */
    private void ensureCsvHeader(File file) throws IOException {
        List<String> lines = Files.readAllLines(file.toPath());
        if (lines.isEmpty()) {
            logger.info("CSV file {} is empty, writing header.", file.getAbsolutePath());
            writeCsvHeader(file);
        } else {
            String firstLine = lines.get(0).trim();
            String expectedHeader = String.join(",", headers);
            if (!firstLine.equals(expectedHeader)) {
                logger.warn("CSV file {} missing or invalid header. Expected: '{}', Found: '{}'. Rewriting header.", file.getAbsolutePath(), expectedHeader, firstLine);
                try (FileWriter writer = new FileWriter(file, false)) {
                    writer.write(expectedHeader);
                    writer.write(System.lineSeparator());
                    // Write back all lines *except* the original (bad) header, if applicable.
                    if (lines.size() > 1) {
                        for (int i = 1; i < lines.size(); i++) {
                            writer.write(lines.get(i));
                            writer.write(System.lineSeparator());
                        }
                    }
                }
                logger.info("CSV file {} header corrected.", file.getAbsolutePath());
            }
        }
    }

    /**
     * Reads all BudgetRow objects from the CSV file.
     * @return List of BudgetRow objects (may be empty).
     */
    @Override
    public List<BudgetRow> readAll() {
        logger.info("readAll called for filePath={}", filePath);
        ensureCsvFileReady();
        List<BudgetRow> rows = new ArrayList<>();
        try (Reader reader = Files.newBufferedReader(Paths.get(filePath))) {
            CSVReader csvReader = new CSVReader(reader);
            String[] headers = csvReader.readNext();
            if (headers == null) {
                logger.warn("CSV file '{}' is empty, no headers found.", filePath);
                return rows;
            }
            // Remove BOM if present
            if (headers.length > 0 && headers[0] != null && headers[0].startsWith("\uFEFF")) {
                headers[0] = headers[0].substring(1);
            }
            int headerCount = headers.length;
            logger.info("CSV headers: {}", Arrays.toString(headers));
            String[] line;
            int lineNum = 1;
            while ((line = csvReader.readNext()) != null) {
                lineNum++;
                // Skip blank/empty lines
                if (line.length == 1 && (line[0] == null || line[0].trim().isEmpty())) {
                    logger.debug("Skipping blank line {}.", lineNum);
                    continue;
                }
                if (line.length != headerCount) {
                    logger.warn("Line {}: Expected {} columns but found {}. Skipping line. Content: {}", lineNum, headerCount, line.length, Arrays.toString(line));
                    continue;
                }
                Map<String, String> map = new LinkedHashMap<>();
                for (int i = 0; i < headerCount; i++) {
                    map.put(headers[i], line[i]);
                }
                logger.debug("Creating BudgetRow from map at line {}: {}", lineNum, map);
                try {
                    rows.add(BudgetRowConverter.mapToBudgetRow(map));
                } catch (Exception ex) {
                    logger.error("Failed to create BudgetRow at line {}: {}. Error: {}", lineNum, map, ex.getMessage());
                }
            }
            logger.info("Read {} rows from CSV file '{}'", rows.size(), filePath);
        } catch (IOException e) {
            logger.error("Failed to read CSV file '{}': {}", filePath, e.getMessage(), e);
        } catch (CsvValidationException e) {
            throw new RuntimeException(e);
        }
        return rows;
    }
    /**
     * Adds a BudgetRow to the CSV file.
     * @param transaction The BudgetRow to add.
     */
    @Override
    public void add(BudgetRow transaction) {
        logger.info("add called for filePath={}, transaction={}", filePath, transaction);
        ensureCsvFileReady();
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

    /**
     * Updates the first matching BudgetRow by key/value with the provided row.
     * @param key The column to match
     * @param value The value to match
     * @param updatedRow The new row to write in place
     * @return true if a row was updated
     */
    @Override
    public boolean update(String key, String value, BudgetRow updatedRow) {
        logger.info("update called for key={}, value={}, filePath={}", key, value, filePath);
        ensureCsvFileReady();
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

    /**
     * Deletes the first matching BudgetRow by key/value.
     * @param key The column to match
     * @param value The value to match
     * @return true if a row was deleted
     */
    @Override
    public boolean delete(String key, String value) {
        logger.info("delete called for key={}, value={}, filePath={}", key, value, filePath);
        ensureCsvFileReady();
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

    /**
     * Gets the standard headers for the working budget CSV file.
     * @return List of header strings.
     */
    @Override
    public List<String> getHeaders() {
        logger.info("getHeaders called for filePath={}", filePath);
        ensureCsvFileReady();
        return new ArrayList<>(headers);
    }

    /**
     * Writes all BudgetRow objects to the CSV file (overwrites file).
     * @param rows The rows to write.
     */
    private void writeAll(List<BudgetRow> rows) {
        logger.info("writeAll called for filePath={} with {} rows", filePath, rows.size());
        ensureCsvFileReady();
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

    /**
     * Overwrites the CSV file with the provided list of BudgetTransaction objects.
     * @param transactions List of BudgetTransaction objects to write.
     */
    public void overwriteAll(List<BudgetTransaction> transactions) {
        logger.info("Entering overwriteAll() in BudgetFileService with {} transactions.", transactions == null ? 0 : transactions.size());
        ensureCsvFileReady();
        if (transactions == null) {
            logger.warn("overwriteAll called with null transactions list. Aborting.");
            return;
        }
        try (
                Writer writer = new FileWriter(filePath, false);
                CSVWriter csvWriter = new CSVWriter(writer,
                        CSVWriter.DEFAULT_SEPARATOR,
                        CSVWriter.DEFAULT_QUOTE_CHARACTER,
                        CSVWriter.DEFAULT_ESCAPE_CHARACTER,
                        CSVWriter.DEFAULT_LINE_END)
        ) {
            csvWriter.writeNext(headers.toArray(new String[0]));
            for (BudgetTransaction tx : transactions) {
                Map<String, String> map = BudgetRowConverter.budgetRowToMap(tx);
                String[] values = headers.stream()
                        .map(h -> map.getOrDefault(h, ""))
                        .toArray(String[]::new);
                csvWriter.writeNext(values);
            }
            logger.info("Successfully overwrote {} transactions to CSV file '{}'.", transactions.size(), filePath);
        } catch (IOException e) {
            logger.error("Failed to overwrite transactions in CSV file '{}': {}", filePath, e.getMessage(), e);
        }
    }
}