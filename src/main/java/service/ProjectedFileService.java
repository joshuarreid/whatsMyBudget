package service;

import com.opencsv.exceptions.CsvValidationException;
import model.BudgetRow;
import model.ProjectedTransaction;
import org.springframework.stereotype.Service;
import util.BudgetRowConverter;
import util.ProjectedRowConverter;
import util.AppLogger;
import org.slf4j.Logger;
import com.opencsv.*;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Implements CSVFileService for Projected BudgetRow objects.
 * Handles projections.csv located in the same directory as the main budget CSV.
 * Provides robust file creation, header validation, and CRUD operations.
 */
@Service
public class ProjectedFileService implements CSVFileService<BudgetRow> {
    private static final Logger logger = AppLogger.getLogger(ProjectedFileService.class);

    private final String projectedFilePath;
    // Use projection-specific headers
    private final List<String> headers = ProjectedRowConverter.headers();

    /**
     * Constructs the ProjectedFileService using the directory of the provided budget CSV file.
     * @param budgetCsvPath The path to the main budget CSV file.
     */
    public ProjectedFileService(String budgetCsvPath) {
        this.projectedFilePath = getProjectedFilePath(budgetCsvPath);
        logger.info("ProjectedFileService initialized with projectedFilePath={}", projectedFilePath);
    }

    /**
     * Computes the projections CSV file path based on the main budget CSV's directory.
     * @param budgetCsvPath The path to the main budget CSV file.
     * @return String path to projections.csv in the same directory.
     */
    private String getProjectedFilePath(String budgetCsvPath) {
        logger.info("getProjectedFilePath called with budgetCsvPath={}", budgetCsvPath);
        if (budgetCsvPath == null || budgetCsvPath.isBlank()) {
            logger.error("budgetCsvPath is null or blank");
            throw new IllegalArgumentException("budgetCsvPath cannot be null or blank");
        }
        Path dir = Paths.get(budgetCsvPath).getParent();
        String projPath = dir.resolve("projections.csv").toString();
        logger.info("Resolved projections file path: {}", projPath);
        return projPath;
    }

    /**
     * Ensures the projections CSV file exists and has the correct header.
     * Creates or repairs the file as needed.
     */
    @Override
    public void ensureCsvFileReady() {
        logger.info("ensureCsvFileReady called for projectedFilePath={}", projectedFilePath);
        File file = new File(projectedFilePath);
        try {
            if (!file.exists()) {
                boolean created = file.createNewFile();
                if (created) {
                    logger.info("Created new projections CSV at {}", projectedFilePath);
                    writeCsvHeader(file);
                } else {
                    logger.warn("Projections CSV file was not created (may already exist): {}", projectedFilePath);
                }
            }
            ensureCsvHeader(file);
        } catch (IOException e) {
            logger.error("Failed to check or write header to projections CSV file: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to ensure header in projections CSV", e);
        }
    }

    /**
     * Writes the standard CSV header row to the provided file.
     */
    private void writeCsvHeader(File file) throws IOException {
        try (FileWriter writer = new FileWriter(file, false)) {
            writer.write(String.join(",", headers));
            writer.write(System.lineSeparator());
            logger.info("Wrote header row to projections CSV file: {}", file.getAbsolutePath());
        }
    }

    /**
     * Ensures the CSV file has the correct header row as the first line.
     * If the file is empty or header is missing/invalid, writes the correct header and preserves data rows.
     * Will not rewrite header if already present and correct.
     */
    private void ensureCsvHeader(File file) throws IOException {
        List<String> lines = Files.readAllLines(file.toPath());
        String expectedHeader = String.join(",", headers);

        if (lines.isEmpty()) {
            logger.info("Projections CSV file {} is empty, writing header.", file.getAbsolutePath());
            writeCsvHeader(file);
            return;
        }

        String firstLine = lines.get(0).trim();
        logger.info("Checking first line of projections CSV: '{}'", firstLine);

        if (firstLine.equals(expectedHeader)) {
            logger.info("Header is already correct in projections CSV file: {}", file.getAbsolutePath());
            return; // Header is correct, nothing to do.
        }

        logger.warn("Projections CSV file {} missing or invalid header. Expected: '{}', Found: '{}'. Rewriting header.", file.getAbsolutePath(), expectedHeader, firstLine);

        // Only keep lines after the first if the first was a header, otherwise keep all lines
        List<String> dataLines = lines;
        if (firstLine.contains("Name") && firstLine.contains("Amount")) {
            dataLines = lines.subList(1, lines.size());
        }
        try (FileWriter writer = new FileWriter(file, false)) {
            writer.write(expectedHeader);
            writer.write(System.lineSeparator());
            for (String line : dataLines) {
                writer.write(line);
                writer.write(System.lineSeparator());
            }
        }
        logger.info("Projections CSV file {} header corrected.", file.getAbsolutePath());
    }

    @Override
    public List<BudgetRow> readAll() {
        logger.info("readAll called for projectedFilePath={}", projectedFilePath);
        ensureCsvFileReady();
        List<BudgetRow> rows = new ArrayList<>();
        try (Reader reader = Files.newBufferedReader(Paths.get(projectedFilePath))) {
            CSVReaderHeaderAware csvReader = new CSVReaderHeaderAware(reader);
            Map<String, String> map;
            while ((map = csvReader.readMap()) != null) {
                // Use ProjectedRowConverter to map
                BudgetRow projectedRow = ProjectedRowConverter.mapToBudgetRow(map);
                rows.add(projectedRow);
            }
            logger.info("Read {} rows from projections CSV file '{}'", rows.size(), projectedFilePath);
        } catch (IOException e) {
            logger.error("Failed to read projections CSV file '{}': {}", projectedFilePath, e.getMessage());
        } catch (CsvValidationException e) {
            throw new RuntimeException(e);
        }
        return rows;
    }

    @Override
    public void add(BudgetRow projectedRow) {
        logger.info("add called for projectedFilePath={}, projectedRow={}", projectedFilePath, projectedRow);
        ensureCsvFileReady();
        boolean fileExists = Files.exists(Paths.get(projectedFilePath));
        try (
                Writer writer = new FileWriter(projectedFilePath, true);
                CSVWriter csvWriter = new CSVWriter(writer,
                        CSVWriter.DEFAULT_SEPARATOR,
                        CSVWriter.DEFAULT_QUOTE_CHARACTER,
                        CSVWriter.DEFAULT_ESCAPE_CHARACTER,
                        CSVWriter.DEFAULT_LINE_END)
        ) {
            if (!fileExists) {
                csvWriter.writeNext(headers.toArray(new String[0]));
            }
            Map<String, String> map = ProjectedRowConverter.budgetRowToMap(projectedRow);
            String[] values = headers.stream()
                    .map(h -> map.getOrDefault(h, ""))
                    .toArray(String[]::new);
            csvWriter.writeNext(values);
            logger.info("Added new projected row to projections CSV file '{}': {}", projectedFilePath, projectedRow);
        } catch (IOException e) {
            logger.error("Failed to add row to projections CSV file '{}': {}", projectedFilePath, e.getMessage());
        }
    }

    @Override
    public boolean update(String key, String value, BudgetRow updatedRow) {
        logger.info("update called for key={}, value={}, projectedFilePath={}", key, value, projectedFilePath);
        ensureCsvFileReady();
        List<BudgetRow> all = readAll();
        boolean updated = false;

        if ("UniqueMatch".equals(key)) {
            logger.info("Performing update using UniqueMatch key for projections.");
            for (int i = 0; i < all.size(); i++) {
                BudgetRow row = all.get(i);
                String rowKey = buildRowUniqueKey(row);
                logger.debug("Comparing row key '{}' with value '{}'", rowKey, value);
                if (rowKey.equals(value)) {
                    all.set(i, updatedRow);
                    updated = true;
                    logger.info("Updated projected row by unique key in projections CSV file '{}'", projectedFilePath);
                    break;
                }
            }
        } else {
            for (int i = 0; i < all.size(); i++) {
                BudgetRow row = all.get(i);
                Map<String, String> rowMap = ProjectedRowConverter.budgetRowToMap(row);
                if (rowMap.getOrDefault(key, "").equals(value)) {
                    all.set(i, updatedRow);
                    updated = true;
                    logger.info("Updated projected row with {}={} in projections CSV file '{}'", key, value, projectedFilePath);
                    break;
                }
            }
        }
        if (updated) {
            writeAll(all);
        } else {
            logger.warn("No projected row found with {}={} to update in projections CSV file '{}'", key, value, projectedFilePath);
        }
        return updated;
    }

    @Override
    public boolean delete(String key, String value) {
        logger.info("delete called for key={}, value={}, projectedFilePath={}", key, value, projectedFilePath);
        ensureCsvFileReady();
        List<BudgetRow> all = readAll();
        int originalSize = all.size();
        List<BudgetRow> newRows;

        if ("UniqueMatch".equals(key)) {
            logger.info("Performing delete using UniqueMatch key for projections.");
            newRows = all.stream()
                    .filter(row -> {
                        String rowKey = buildRowUniqueKey(row);
                        boolean keep = !rowKey.equals(value);
                        logger.debug("Row key '{}' vs value '{}', keep: {}", rowKey, value, keep);
                        return keep;
                    })
                    .collect(Collectors.toList());
        } else {
            newRows = all.stream()
                    .filter(row -> {
                        Map<String, String> rowMap = ProjectedRowConverter.budgetRowToMap(row);
                        boolean keep = !rowMap.getOrDefault(key, "").equals(value);
                        logger.debug("Row field '{}' vs value '{}', keep: {}", key, value, keep);
                        return keep;
                    })
                    .collect(Collectors.toList());
        }

        if (newRows.size() < originalSize) {
            writeAll(newRows);
            logger.info("Deleted projected row with {}={} from projections CSV file '{}'", key, value, projectedFilePath);
            return true;
        } else {
            logger.warn("No projected row found with {}={} to delete in projections CSV file '{}'", key, value, projectedFilePath);
            return false;
        }
    }

    @Override
    public List<String> getHeaders() {
        logger.info("getHeaders called for projectedFilePath={}", projectedFilePath);
        ensureCsvFileReady();
        return new ArrayList<>(headers);
    }

    private void writeAll(List<BudgetRow> rows) {
        logger.info("writeAll called for projectedFilePath={} with {} rows", projectedFilePath, rows.size());
        ensureCsvFileReady();
        try (
                Writer writer = new FileWriter(projectedFilePath);
                CSVWriter csvWriter = new CSVWriter(writer,
                        CSVWriter.DEFAULT_SEPARATOR,
                        CSVWriter.DEFAULT_QUOTE_CHARACTER,
                        CSVWriter.DEFAULT_ESCAPE_CHARACTER,
                        CSVWriter.DEFAULT_LINE_END)
        ) {
            csvWriter.writeNext(headers.toArray(new String[0]));
            for (BudgetRow row : rows) {
                Map<String, String> map = ProjectedRowConverter.budgetRowToMap(row);
                String[] values = headers.stream()
                        .map(h -> map.getOrDefault(h, ""))
                        .toArray(String[]::new);
                csvWriter.writeNext(values);
            }
            logger.info("Wrote {} rows to projections CSV file '{}'", rows.size(), projectedFilePath);
        } catch (IOException e) {
            logger.error("Failed to write to projections CSV file '{}': {}", projectedFilePath, e.getMessage());
        }
    }

    /**
     * Builds a unique key for a BudgetRow for robust update/delete operations.
     * Uses only fields shown in the current UI/table: Name, Amount, Category, Criticality, Account, Created Time, Statement Period.
     */
    private String buildRowUniqueKey(BudgetRow row) {
        logger.debug("Entering buildRowUniqueKey for row={}", row);
        String key = String.join("|",
                Objects.toString(row.getName(), ""),
                Objects.toString(row.getAmount(), ""),
                Objects.toString(row.getCategory(), ""),
                Objects.toString(row.getCriticality(), ""),
                Objects.toString(row.getAccount(), ""),
                Objects.toString(row.getCreatedTime(), ""),
                Objects.toString(getStatementPeriodForRow(row), "")
        );
        logger.debug("buildRowUniqueKey: generated key={}", key);
        return key;
    }

    /**
     * Retrieves the statement period for a BudgetRow, falling back to empty if not present.
     */
    private String getStatementPeriodForRow(BudgetRow row) {
        if (row instanceof model.ProjectedTransaction) {
            return ((model.ProjectedTransaction) row).getStatementPeriod();
        } else if (row instanceof model.BudgetTransaction) {
            return ((model.BudgetTransaction) row).getStatementPeriod();
        } else {
            return "";
        }
    }

    public void overwriteAll(List<ProjectedTransaction> projectedTransactions) {
        logger.info("Entering overwriteAll() in ProjectedFileService with {} projected transactions.", projectedTransactions == null ? 0 : projectedTransactions.size());
        ensureCsvFileReady();
        if (projectedTransactions == null) {
            logger.warn("overwriteAll called with null projectedTransactions list. Aborting.");
            return;
        }
        try (
                Writer writer = new FileWriter(projectedFilePath, false);
                CSVWriter csvWriter = new CSVWriter(writer,
                        CSVWriter.DEFAULT_SEPARATOR,
                        CSVWriter.DEFAULT_QUOTE_CHARACTER,
                        CSVWriter.DEFAULT_ESCAPE_CHARACTER,
                        CSVWriter.DEFAULT_LINE_END)
        ) {
            csvWriter.writeNext(headers.toArray(new String[0]));
            for (ProjectedTransaction tx : projectedTransactions) {
                Map<String, String> map = ProjectedRowConverter.budgetRowToMap(tx);
                String[] values = headers.stream()
                        .map(h -> map.getOrDefault(h, ""))
                        .toArray(String[]::new);
                csvWriter.writeNext(values);
            }
            logger.info("Successfully overwrote {} projected transactions to projections CSV file '{}'.", projectedTransactions.size(), projectedFilePath);
        } catch (IOException e) {
            logger.error("Failed to overwrite projected transactions in projections CSV file '{}': {}", projectedFilePath, e.getMessage(), e);
        }
    }

    public String getFilePath() {
        return projectedFilePath;
    }
}