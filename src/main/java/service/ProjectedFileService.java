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
 * Implements CSVFileService for Projected BudgetRow objects.
 * Handles projections.csv located in the same directory as the main budget CSV.
 * Provides robust file creation, header validation, and CRUD operations.
 */
@Service
public class ProjectedFileService implements CSVFileService<BudgetRow> {
    private static final Logger logger = AppLogger.getLogger(ProjectedFileService.class);

    private final String projectedFilePath;
    private final List<String> headers = BudgetRowConverter.headers();

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
     * If the file is empty or header is missing/invalid, writes the correct header.
     */
    private void ensureCsvHeader(File file) throws IOException {
        List<String> lines = Files.readAllLines(file.toPath());
        if (lines.isEmpty()) {
            logger.info("Projections CSV file {} is empty, writing header.", file.getAbsolutePath());
            writeCsvHeader(file);
        } else {
            String firstLine = lines.get(0).trim();
            String expectedHeader = String.join(",", headers);
            if (!firstLine.equals(expectedHeader)) {
                logger.warn("Projections CSV file {} missing or invalid header. Rewriting header.", file.getAbsolutePath());
                try (FileWriter writer = new FileWriter(file, false)) {
                    writer.write(expectedHeader);
                    writer.write(System.lineSeparator());
                    if (!firstLine.isEmpty() && !firstLine.contains(",Amount,")) {
                        for (String line : lines) {
                            writer.write(line);
                            writer.write(System.lineSeparator());
                        }
                    } else if (lines.size() > 1) {
                        for (int i = 1; i < lines.size(); i++) {
                            writer.write(lines.get(i));
                            writer.write(System.lineSeparator());
                        }
                    }
                }
                logger.info("Projections CSV file {} header corrected.", file.getAbsolutePath());
            }
        }
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
                BudgetRow projectedRow = BudgetRowConverter.mapToBudgetRow(map);
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
            Map<String, String> map = BudgetRowConverter.budgetRowToMap(projectedRow);
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
        for (int i = 0; i < all.size(); i++) {
            BudgetRow row = all.get(i);
            Map<String, String> rowMap = BudgetRowConverter.budgetRowToMap(row);
            if (rowMap.getOrDefault(key, "").equals(value)) {
                all.set(i, updatedRow);
                updated = true;
                logger.info("Updated projected row with {}={} in projections CSV file '{}'", key, value, projectedFilePath);
                break;
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
        List<BudgetRow> newRows = all.stream()
                .filter(row -> {
                    Map<String, String> rowMap = BudgetRowConverter.budgetRowToMap(row);
                    return !rowMap.getOrDefault(key, "").equals(value);
                })
                .collect(Collectors.toList());
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
                Map<String, String> map = BudgetRowConverter.budgetRowToMap(row);
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
}