package service;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;
import model.BudgetTransaction;
import org.slf4j.Logger;
import org.springframework.stereotype.Service;
import util.AppLogger;
import util.BudgetRowHashUtil;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Service for importing transactions from CSV files to the working budget file.
 * Handles duplicate prevention, CSV validation, and robust logging.
 * Deduplication and import always ignore statementPeriod (set to blank on import).
 */
@Service
public class ImportService {
    private static final Logger logger = AppLogger.getLogger(ImportService.class);

    /**
     * Result object for import operations. Contains counts for detected, imported, duplicate, and error lines.
     */
    public static class ImportResult {
        public final int detectedCount;
        public final int importedCount;
        public final int duplicateCount;
        public final int errorCount;
        public final List<String> errorLines;
        public final List<String> importedLines;

        public ImportResult(int detectedCount, int importedCount, int duplicateCount, int errorCount, List<String> errorLines, List<String> importedLines) {
            this.detectedCount = detectedCount;
            this.importedCount = importedCount;
            this.duplicateCount = duplicateCount;
            this.errorCount = errorCount;
            this.errorLines = errorLines;
            this.importedLines = importedLines;
        }

        /**
         * @return A user-friendly summary string for display after import.
         */
        public String getSummary() {
            return String.format(
                    "%d transactions detected, %d new imported, %d duplicates, %d errors",
                    detectedCount, importedCount, duplicateCount, errorCount
            );
        }
    }

    /**
     * Parses the given CSV file into a list of BudgetTransaction, without saving to the working file.
     * @param importFile The file to parse.
     * @return List of BudgetTransaction objects parsed from the file.
     * @throws Exception if parsing fails.
     */
    public List<BudgetTransaction> parseFileToBudgetTransactions(File importFile) throws Exception {
        logger.info("parseFileToBudgetTransactions called for file: {}", importFile);
        List<BudgetTransaction> transactions = new ArrayList<>();

        if (importFile == null || !importFile.exists() || !importFile.isFile()) {
            logger.error("Import file does not exist or is not a regular file: {}", importFile);
            throw new FileNotFoundException("Import file does not exist: " + (importFile != null ? importFile.getAbsolutePath() : "null"));
        }

        try (
                InputStreamReader reader = new InputStreamReader(new FileInputStream(importFile), StandardCharsets.UTF_8);
                CSVReader csvReader = new CSVReader(reader)
        ) {
            String[] headers = csvReader.readNext();
            if (headers == null) {
                logger.error("Import file is empty: {}", importFile.getAbsolutePath());
                throw new IOException("Import file is empty.");
            }
            // Handle UTF-8 BOM if present
            if (headers.length > 0 && headers[0] != null && headers[0].length() > 0 && headers[0].charAt(0) == '\uFEFF') {
                logger.info("Detected UTF-8 BOM in import file header, removing...");
                headers[0] = headers[0].substring(1);
            }
            if (headers.length < 9 || !headers[0].trim().equalsIgnoreCase("Name")) {
                logger.error("Import file header invalid or missing expected columns: {}", String.join(",", headers));
                throw new IllegalArgumentException("Import file is missing expected columns: " + String.join(",", headers));
            }

            String[] fields;
            int lineNum = 2; // header is line 1
            while ((fields = csvReader.readNext()) != null) {
                if (fields.length < 9) {
                    logger.warn("Skipping malformed CSV line {} (wrong column count): {}", lineNum, String.join(",", fields));
                    lineNum++;
                    continue;
                }
                try {
                    BudgetTransaction tx = new BudgetTransaction(
                            safeTrim(fields[0]), // Name
                            safeTrim(fields[1]), // Amount
                            safeTrim(fields[2]), // Category
                            safeTrim(fields[3]), // Criticality
                            safeTrim(fields[4]), // Transaction Date
                            safeTrim(fields[5]), // Account
                            safeTrim(fields[6]), // Status
                            safeTrim(fields[7]), // Created Time
                            safeTrim(fields[8])  // Payment Method or Statement Period, as appropriate
                    );
                    transactions.add(tx);
                    logger.debug("Parsed transaction at line {}: {}", lineNum, tx);
                } catch (Exception ex) {
                    logger.error("Failed to parse line {}: {}. Error: {}", lineNum, String.join(",", fields), ex.getMessage(), ex);
                }
                lineNum++;
            }
            logger.info("parseFileToBudgetTransactions completed: {} transactions parsed from file {}", transactions.size(), importFile.getName());
            return transactions;

        } catch (IOException | CsvValidationException ex) {
            logger.error("Error reading/parsing import file '{}': {}", importFile.getAbsolutePath(), ex.getMessage(), ex);
            throw ex;
        } catch (Exception ex) {
            logger.error("Unexpected error parsing import file '{}': {}", importFile.getAbsolutePath(), ex.getMessage(), ex);
            throw ex;
        }
    }

    /**
     * Imports transactions from the given CSV file and appends them to the working budget CSV.
     * Deduplication and import always ignore statementPeriod (set to blank on import).
     *
     * @param csvImportFile The file to import.
     * @param workingBudgetCsvPath The current working budget CSV file to append to.
     * @return ImportResult with counts and error lines.
     */
    public ImportResult importTransactions(File csvImportFile, String workingBudgetCsvPath) {
        logger.info("Entering importTransactions(): importFile='{}', workingFile='{}'",
                csvImportFile != null ? csvImportFile.getAbsolutePath() : null,
                workingBudgetCsvPath);

        int detectedCount = 0;
        int importedCount = 0;
        int duplicateCount = 0;
        int errorCount = 0;
        List<String> errorLines = new ArrayList<>();
        List<String> importedLines = new ArrayList<>();

        if (csvImportFile == null || !csvImportFile.exists()) {
            logger.error("Import file does not exist: {}", csvImportFile);
            return new ImportResult(0, 0, 0, 1, List.of("Import file does not exist."), importedLines);
        }
        if (workingBudgetCsvPath == null || workingBudgetCsvPath.isEmpty()) {
            logger.error("Working budget CSV path is null or empty.");
            return new ImportResult(0, 0, 0, 1, List.of("Working budget CSV path is invalid."), importedLines);
        }

        BudgetFileService budgetFileService = new BudgetFileService(workingBudgetCsvPath);
        Set<String> existingHashes = new HashSet<>();
        try {
            budgetFileService.ensureCsvFileReady();
            List<model.BudgetRow> existingRows = budgetFileService.readAll();
            for (model.BudgetRow row : existingRows) {
                // Always use blank for statementPeriod in deduplication
                String hash = BudgetRowHashUtil.computeTransactionHash(
                        safeTrim(row.getName()),
                        safeTrim(row.getAmount()),
                        safeTrim(row.getCategory()),
                        safeTrim(row.getCriticality()),
                        safeTrim(row.getTransactionDate()),
                        safeTrim(row.getAccount()),
                        safeTrim(row.getStatus()),
                        safeTrim(row.getCreatedTime()),
                        safeTrim(row.getPaymentMethod())
                        // statementPeriod is ignored
                );
                logger.debug("Existing row hash: [name='{}', amount='{}', category='{}', criticality='{}', date='{}', account='{}', status='{}', created='{}', payMethod='{}'] -> hash={}",
                        row.getName(), row.getAmount(), row.getCategory(), row.getCriticality(), row.getTransactionDate(), row.getAccount(), row.getStatus(), row.getCreatedTime(), row.getPaymentMethod(), hash);
                existingHashes.add(hash);
            }
            logger.info("Loaded {} existing transaction hashes for duplicate detection.", existingHashes.size());
        } catch (Exception e) {
            logger.error("Failed to load existing transactions for duplicate validation: {}", e.getMessage(), e);
            return new ImportResult(0, 0, 0, 1, List.of("Failed to load existing transactions: " + e.getMessage()), importedLines);
        }

        try (
                InputStreamReader reader = new InputStreamReader(new FileInputStream(csvImportFile), StandardCharsets.UTF_8);
                CSVReader csvReader = new CSVReader(reader)
        ) {
            String[] headers = csvReader.readNext();
            if (headers == null) {
                logger.error("Import file is empty: {}", csvImportFile.getAbsolutePath());
                return new ImportResult(0, 0, 0, 1, List.of("Import file is empty."), importedLines);
            }
            // Handle UTF-8 BOM if present
            if (headers.length > 0 && headers[0] != null && headers[0].length() > 0 && headers[0].charAt(0) == '\uFEFF') {
                logger.info("Detected UTF-8 BOM in import file header, removing...");
                headers[0] = headers[0].substring(1);
            }
            if (headers.length < 9 || !headers[0].trim().equalsIgnoreCase("Name")) {
                logger.error("Import file header invalid or missing expected columns: {}", String.join(",", headers));
                return new ImportResult(0, 0, 0, 1, List.of("Import file is missing expected columns: " + String.join(",", headers)), importedLines);
            }

            String[] fields;
            int lineNum = 2; // since header is line 1
            while ((fields = csvReader.readNext()) != null) {
                detectedCount++;
                try {
                    if (fields.length < 9) {
                        logger.warn("Skipping malformed CSV line {} (wrong column count): {}", lineNum, String.join(",", fields));
                        errorCount++;
                        errorLines.add(String.join(",", fields));
                        lineNum++;
                        continue;
                    }
                    // Always blank out statementPeriod for import/deduplication
                    String[] normalizedFields = Arrays.copyOf(fields, fields.length);
                    normalizedFields[8] = ""; // index 8 is "statementPeriod"/Payment Method, but we treat as blank

                    String hash = BudgetRowHashUtil.computeTransactionHash(
                            safeTrim(normalizedFields[0]),
                            safeTrim(normalizedFields[1]),
                            safeTrim(normalizedFields[2]),
                            safeTrim(normalizedFields[3]),
                            safeTrim(normalizedFields[4]),
                            safeTrim(normalizedFields[5]),
                            safeTrim(normalizedFields[6]),
                            safeTrim(normalizedFields[7]),
                            "" // always blank for statementPeriod/paymentMethod
                    );
                    logger.debug("Import row hash: [name='{}', amount='{}', category='{}', criticality='{}', date='{}', account='{}', status='{}', created='{}', payMethod(blanked)] -> hash={}",
                            normalizedFields[0], normalizedFields[1], normalizedFields[2], normalizedFields[3], normalizedFields[4], normalizedFields[5], normalizedFields[6], normalizedFields[7], hash);

                    if (existingHashes.contains(hash)) {
                        logger.warn("Duplicate transaction detected at line {} (skipping): {}", lineNum, String.join(",", normalizedFields));
                        duplicateCount++;
                        errorLines.add("DUPLICATE: " + String.join(",", normalizedFields));
                        lineNum++;
                        continue;
                    }
                    // Only pass blank for statementPeriod (last argument)
                    BudgetTransaction tx = new BudgetTransaction(
                            safeTrim(normalizedFields[0]),
                            safeTrim(normalizedFields[1]),
                            safeTrim(normalizedFields[2]),
                            safeTrim(normalizedFields[3]),
                            safeTrim(normalizedFields[4]),
                            safeTrim(normalizedFields[5]),
                            safeTrim(normalizedFields[6]),
                            safeTrim(normalizedFields[7]),
                            "" // Always blank
                    );
                    budgetFileService.add(tx);
                    existingHashes.add(hash);
                    importedCount++;
                    importedLines.add(String.join(",", normalizedFields));
                } catch (Exception ex) {
                    logger.error("Failed to import line {}: {}. Error: {}", lineNum, String.join(",", fields), ex.getMessage(), ex);
                    errorCount++;
                    errorLines.add(String.join(",", fields));
                }
                lineNum++;
            }
            logger.info("Import complete. {} transactions detected. {} new imported, {} duplicates, {} errors.",
                    detectedCount, importedCount, duplicateCount, errorCount);
            return new ImportResult(detectedCount, importedCount, duplicateCount, errorCount, errorLines, importedLines);

        } catch (IOException | CsvValidationException ex) {
            logger.error("Error reading import file: {}", ex.getMessage(), ex);
            return new ImportResult(0, 0, 0, 1, List.of("Error reading import file: " + ex.getMessage()), importedLines);
        }
    }

    private static String safeTrim(String s) {
        return s == null ? "" : s.trim();
    }
}