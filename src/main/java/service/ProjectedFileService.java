package service;

import com.opencsv.CSVReaderHeaderAware;
import com.opencsv.CSVWriter;
import com.opencsv.exceptions.CsvValidationException;
import model.ProjectedRow;
import org.slf4j.Logger;
import org.springframework.stereotype.Service;
import util.AppLogger;
import util.ProjectedRowConverter;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Implements CSVFileService for ProjectedRow objects.
 */
@Service
public class ProjectedFileService implements CSVFileService<ProjectedRow> {
    private static final Logger logger = AppLogger.getLogger(ProjectedFileService.class);

    private final String filePath;
    private final List<String> headers = ProjectedRowConverter.headersStatic();

    public ProjectedFileService(String filePath) {
        this.filePath = filePath;
    }

    @Override
    public List<ProjectedRow> readAll() {
        List<ProjectedRow> rows = new ArrayList<>();
        try (Reader reader = Files.newBufferedReader(Paths.get(filePath))) {
            CSVReaderHeaderAware csvReader = new CSVReaderHeaderAware(reader);
            Map<String, String> map;
            while ((map = csvReader.readMap()) != null) {
                ProjectedRow projectedRow = ProjectedRowConverter.mapToProjectedRow(map);
                rows.add(projectedRow);
            }
            logger.info("Read {} rows from Projected CSV file '{}'", rows.size(), filePath);
        } catch (IOException e) {
            logger.error("Failed to read Projected CSV file '{}': {}", filePath, e.getMessage());
        } catch (CsvValidationException e) {
            logger.error("CSV validation error in '{}': {}", filePath, e.getMessage());
        }
        return rows;
    }

    @Override
    public void add(ProjectedRow row) {
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
            Map<String, String> map = ProjectedRowConverter.projectedRowToMap(row);
            String[] values = headers.stream()
                    .map(h -> map.getOrDefault(h, ""))
                    .toArray(String[]::new);
            csvWriter.writeNext(values);
            logger.info("Added new projected row to CSV file '{}': {}", filePath, row);
        } catch (IOException e) {
            logger.error("Failed to add projected row to CSV file '{}': {}", filePath, e.getMessage());
        }
    }

    @Override
    public boolean update(String key, String value, ProjectedRow updatedRow) {
        List<ProjectedRow> all = readAll();
        boolean updated = false;
        for (int i = 0; i < all.size(); i++) {
            ProjectedRow row = all.get(i);
            Map<String, String> rowMap = ProjectedRowConverter.projectedRowToMap(row);
            if (rowMap.getOrDefault(key, "").equals(value)) {
                all.set(i, updatedRow);
                updated = true;
                logger.info("Updated projected row with {}={} in CSV file '{}'", key, value, filePath);
                break;
            }
        }
        if (updated) {
            writeAll(all);
        } else {
            logger.warn("No projected row found with {}={} to update in CSV file '{}'", key, value, filePath);
        }
        return updated;
    }

    @Override
    public boolean delete(String key, String value) {
        List<ProjectedRow> all = readAll();
        int originalSize = all.size();
        List<ProjectedRow> newRows = all.stream()
                .filter(row -> {
                    Map<String, String> rowMap = ProjectedRowConverter.projectedRowToMap(row);
                    return !rowMap.getOrDefault(key, "").equals(value);
                })
                .collect(Collectors.toList());
        if (newRows.size() < originalSize) {
            writeAll(newRows);
            logger.info("Deleted projected row with {}={} from CSV file '{}'", key, value, filePath);
            return true;
        } else {
            logger.warn("No projected row found with {}={} to delete in CSV file '{}'", key, value, filePath);
            return false;
        }
    }

    @Override
    public List<String> getHeaders() {
        return new ArrayList<>(headers);
    }

    private void writeAll(List<ProjectedRow> rows) {
        try (
                Writer writer = new FileWriter(filePath);
                CSVWriter csvWriter = new CSVWriter(writer,
                        CSVWriter.DEFAULT_SEPARATOR,
                        CSVWriter.DEFAULT_QUOTE_CHARACTER,
                        CSVWriter.DEFAULT_ESCAPE_CHARACTER,
                        CSVWriter.DEFAULT_LINE_END)
        ) {
            csvWriter.writeNext(headers.toArray(new String[0]));
            for (ProjectedRow row : rows) {
                Map<String, String> map = ProjectedRowConverter.projectedRowToMap(row);
                String[] values = headers.stream()
                        .map(h -> map.getOrDefault(h, ""))
                        .toArray(String[]::new);
                csvWriter.writeNext(values);
            }
            logger.info("Wrote {} projected rows to CSV file '{}'", rows.size(), filePath);
        } catch (IOException e) {
            logger.error("Failed to write projected rows to CSV file '{}': {}", filePath, e.getMessage());
        }
    }
}