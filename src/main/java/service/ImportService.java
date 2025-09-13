package service;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;
import model.BudgetTransaction;
import org.slf4j.Logger;
import org.springframework.stereotype.Service;
import util.AppLogger;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * ImportService handles the import of transactions from external CSV files (e.g., Notion exports).
 * It parses, validates, and writes transactions to the current working budget CSV, logging all operations robustly.
 */
@Service
public class ImportService {
    private static final Logger logger = AppLogger.getLogger(ImportService.class);

    /**
     * Result object for import operations.
     */
    public static class ImportResult {
        public final int importedCount;
        public final int errorCount;
        public final List<String> errorLines;
        public final List<String> importedLines;

        public ImportResult(int importedCount, int errorCount, List<String> errorLines, List<String> importedLines) {
            this.importedCount = importedCount;
            this.errorCount = errorCount;
            this.errorLines = errorLines;
            this.importedLines = importedLines;
        }
    }

    /**
     * Imports transactions from the given CSV file and appends them to the working budget CSV.
     * Uses OpenCSV for robust parsing.
     * @param csvImportFile The file to import.
     * @param workingBudgetCsvPath The current working budget CSV file to append to.
     * @return ImportResult with counts and error lines.
     */
    public ImportResult importTransactions(File csvImportFile, String workingBudgetCsvPath) {
        logger.info("Entering importTransactions(): importFile='{}', workingFile='{}'",
                csvImportFile != null ? csvImportFile.getAbsolutePath() : null,
                workingBudgetCsvPath);

        int importedCount = 0;
        int errorCount = 0;
        List<String> errorLines = new ArrayList<>();
        List<String> importedLines = new ArrayList<>();

        if (csvImportFile == null || !csvImportFile.exists()) {
            logger.error("Import file does not exist: {}", csvImportFile);
            return new ImportResult(0, 1, List.of("Import file does not exist."), importedLines);
        }
        if (workingBudgetCsvPath == null || workingBudgetCsvPath.isEmpty()) {
            logger.error("Working budget CSV path is null or empty.");
            return new ImportResult(0, 1, List.of("Working budget CSV path is invalid."), importedLines);
        }

        try (
                InputStreamReader reader = new InputStreamReader(new FileInputStream(csvImportFile), StandardCharsets.UTF_8);
                CSVReader csvReader = new CSVReader(reader)
        ) {
            String[] headers = csvReader.readNext();
            if (headers == null) {
                logger.error("Import file is empty: {}", csvImportFile.getAbsolutePath());
                return new ImportResult(0, 1, List.of("Import file is empty."), importedLines);
            }
            // Handle UTF-8 BOM if present
            if (headers.length > 0 && headers[0] != null && headers[0].length() > 0 && headers[0].charAt(0) == '\uFEFF') {
                logger.info("Detected UTF-8 BOM in import file header, removing...");
                headers[0] = headers[0].substring(1);
            }
            if (headers.length < 9 || !headers[0].trim().equalsIgnoreCase("Name")) {
                logger.error("Import file header invalid or missing expected columns: {}", String.join(",", headers));
                return new ImportResult(0, 1, List.of("Import file is missing expected columns: " + String.join(",", headers)), importedLines);
            }

            BudgetFileService budgetFileService = new BudgetFileService(workingBudgetCsvPath);

            String[] fields;
            int lineNum = 2; // since header is line 1
            while ((fields = csvReader.readNext()) != null) {
                try {
                    if (fields.length < 9) {
                        logger.warn("Skipping malformed CSV line {} (wrong column count): {}", lineNum, String.join(",", fields));
                        errorCount++;
                        errorLines.add(String.join(",", fields));
                        lineNum++;
                        continue;
                    }
                    BudgetTransaction tx = new BudgetTransaction(
                            fields[0].trim(), // Name
                            fields[1].trim(), // Amount
                            fields[2].trim(), // Category
                            fields[3].trim(), // Criticality
                            fields[4].trim(), // Transaction Date
                            fields[5].trim(), // Account
                            fields[6].trim(), // status
                            fields[7].trim(), // Created time
                            fields[8].trim()  // Payment Method
                    );
                    budgetFileService.add(tx);
                    importedCount++;
                    importedLines.add(String.join(",", fields));
                } catch (Exception ex) {
                    logger.error("Failed to import line {}: {}. Error: {}", lineNum, String.join(",", fields), ex.getMessage(), ex);
                    errorCount++;
                    errorLines.add(String.join(",", fields));
                }
                lineNum++;
            }
            logger.info("Import complete. Imported {} transactions with {} errors.", importedCount, errorCount);
            return new ImportResult(importedCount, errorCount, errorLines, importedLines);

        } catch (IOException | CsvValidationException ex) {
            logger.error("Error reading import file: {}", ex.getMessage(), ex);
            return new ImportResult(0, 1, List.of("Error reading import file: " + ex.getMessage()), importedLines);
        }
    }
}