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
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Service for importing transactions from CSV files to the working budget file.
 * Handles duplicate prevention, CSV validation, and robust logging.
 * Deduplication and import always ignore statementPeriod (set to blank on import).
 */
@Service
public class ImportService {
    private static final Logger logger = AppLogger.getLogger(ImportService.class);

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

        public String getSummary() {
            return String.format(
                    "%d transactions detected, %d new imported, %d duplicates, %d errors",
                    detectedCount, importedCount, duplicateCount, errorCount
            );
        }
    }

    /**
     * Maps logical field names to possible header variants.
     */
    private static Map<String, String[]> getLogicalToActualHeaders() {
        Map<String, String[]> map = new HashMap<>();
        map.put("name", new String[]{"name", "Name"});
        map.put("amount", new String[]{"amount", "Amount"});
        map.put("category", new String[]{"category", "Category"});
        map.put("criticality", new String[]{"criticality", "Criticality"});
        map.put("transaction date", new String[]{"transaction date", "Transaction Date"});
        map.put("account", new String[]{"account", "Account"});
        map.put("status", new String[]{"status", "Status"});
        map.put("created time", new String[]{"created time", "Created time"});
        map.put("payment method", new String[]{"payment method", "Payment Method"});
        return map;
    }

    /**
     * Utility to map column headers to indices, case-insensitive, trimmed.
     */
    private static Map<String, Integer> mapHeaderIndices(String[] headers) {
        Map<String, Integer> map = new HashMap<>();
        for (int i = 0; i < headers.length; i++) {
            map.put(headers[i].trim().toLowerCase(), i);
        }
        logger.debug("Mapped columns: {}", map);
        return map;
    }

    /**
     * Returns the index for a logical key by searching possible header names.
     */
    private static Integer getHeaderIndex(Map<String, Integer> headerMap, String... logicalNames) {
        for (String name : logicalNames) {
            Integer idx = headerMap.get(name.toLowerCase());
            if (idx != null) return idx;
        }
        return null;
    }

    /**
     * Gets a field by logical header key(s) from a CSV row.
     * Tries all provided logical names for robustness.
     */
    private static String getField(String[] row, Map<String, Integer> headerMap, String... logicalNames) {
        Integer idx = getHeaderIndex(headerMap, logicalNames);
        if (idx == null || idx >= row.length) return "";
        return row[idx] == null ? "" : row[idx].trim();
    }

    /**
     * Parses the given CSV file into a list of BudgetTransaction, without saving to the working file.
     * Always maps fields by header name, robust to Notion/working file header variations.
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
            if (headers.length > 0 && headers[0] != null && headers[0].length() > 0 && headers[0].charAt(0) == '\uFEFF') {
                logger.info("Detected UTF-8 BOM in import file header, removing...");
                headers[0] = headers[0].substring(1);
            }
            Map<String, Integer> headerMap = mapHeaderIndices(headers);

            Map<String, String[]> logicalHeaders = getLogicalToActualHeaders();
            for (String logical : logicalHeaders.keySet()) {
                Integer idx = getHeaderIndex(headerMap, logicalHeaders.get(logical));
                if (idx == null) {
                    logger.error("Import file missing required column for '{}': tried {}", logical, Arrays.toString(logicalHeaders.get(logical)));
                    throw new IllegalArgumentException("Import file missing required column for: " + logical);
                }
            }

            String[] fields;
            int lineNum = 2; // header is line 1
            while ((fields = csvReader.readNext()) != null) {
                try {
                    BudgetTransaction tx = new BudgetTransaction(
                            getField(fields, headerMap, logicalHeaders.get("name")),
                            getField(fields, headerMap, logicalHeaders.get("amount")),
                            getField(fields, headerMap, logicalHeaders.get("category")),
                            getField(fields, headerMap, logicalHeaders.get("criticality")),
                            getField(fields, headerMap, logicalHeaders.get("transaction date")),
                            getField(fields, headerMap, logicalHeaders.get("account")),
                            getField(fields, headerMap, logicalHeaders.get("status")),
                            getField(fields, headerMap, logicalHeaders.get("created time")),
                            getField(fields, headerMap, logicalHeaders.get("payment method")), // <-- paymentMethod
                            "" // <-- statementPeriod (empty by default on import)
                    );
                    transactions.add(tx);
                    logger.debug("Parsed transaction at line {}: {}", lineNum, tx);
                } catch (Exception ex) {
                    logger.error("Failed to parse line {}: {}. Error: {}", lineNum, Arrays.toString(fields), ex.getMessage(), ex);
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
     * Imports transactions from the given import file into the working budget CSV,
     * checking for duplicates using a hash of normalized fields.
     * Always maps fields by header name, never by index.
     * @param csvImportFile The import file.
     * @param workingBudgetCsvPath The path to the working file.
     * @return ImportResult summarizing detected/imported/duplicates/errors.
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
        Map<String, String[]> logicalHeaders = getLogicalToActualHeaders();

        // --- Read working file, using robust header mapping ---
        try (
                InputStreamReader reader = new InputStreamReader(new FileInputStream(workingBudgetCsvPath), StandardCharsets.UTF_8);
                CSVReader csvReader = new CSVReader(reader)
        ) {
            String[] headers = csvReader.readNext();
            if (headers == null) {
                logger.error("Working file is empty: {}", workingBudgetCsvPath);
                return new ImportResult(0, 0, 0, 1, List.of("Working file is empty."), importedLines);
            }
            if (headers.length > 0 && headers[0] != null && headers[0].length() > 0 && headers[0].charAt(0) == '\uFEFF') {
                logger.info("Detected UTF-8 BOM in working file header, removing...");
                headers[0] = headers[0].substring(1);
            }
            Map<String, Integer> headerMap = mapHeaderIndices(headers);
            for (String logical : logicalHeaders.keySet()) {
                Integer idx = getHeaderIndex(headerMap, logicalHeaders.get(logical));
                if (idx == null) {
                    logger.error("Working file missing required column for '{}': tried {}", logical, Arrays.toString(logicalHeaders.get(logical)));
                    return new ImportResult(0, 0, 0, 1, List.of("Working file missing required column for: " + logical), importedLines);
                }
            }

            String[] fields;
            int readCount = 0;
            while ((fields = csvReader.readNext()) != null) {
                String hash = computeNormalizedTransactionHash(
                        getField(fields, headerMap, logicalHeaders.get("name")),
                        getField(fields, headerMap, logicalHeaders.get("amount")),
                        getField(fields, headerMap, logicalHeaders.get("category")),
                        getField(fields, headerMap, logicalHeaders.get("criticality")),
                        getField(fields, headerMap, logicalHeaders.get("transaction date")),
                        getField(fields, headerMap, logicalHeaders.get("account")),
                        getField(fields, headerMap, logicalHeaders.get("status")),
                        getField(fields, headerMap, logicalHeaders.get("created time")),
                        getField(fields, headerMap, logicalHeaders.get("payment method"))
                );
                logger.debug("Existing row hash: fields='{}' -> hash={}", Arrays.toString(fields), hash);
                existingHashes.add(hash);
                readCount++;
            }
            logger.info("Loaded {} existing transaction hashes for duplicate detection.", readCount);
        } catch (Exception e) {
            logger.error("Failed to load existing transactions for duplicate validation: {}", e.getMessage(), e);
            return new ImportResult(0, 0, 0, 1, List.of("Failed to load existing transactions: " + e.getMessage()), importedLines);
        }

        // --- Read import file, using robust header mapping ---
        try (
                InputStreamReader reader = new InputStreamReader(new FileInputStream(csvImportFile), StandardCharsets.UTF_8);
                CSVReader csvReader = new CSVReader(reader)
        ) {
            String[] headers = csvReader.readNext();
            if (headers == null) {
                logger.error("Import file is empty: {}", csvImportFile.getAbsolutePath());
                return new ImportResult(0, 0, 0, 1, List.of("Import file is empty."), importedLines);
            }
            if (headers.length > 0 && headers[0] != null && headers[0].length() > 0 && headers[0].charAt(0) == '\uFEFF') {
                logger.info("Detected UTF-8 BOM in import file header, removing...");
                headers[0] = headers[0].substring(1);
            }
            Map<String, Integer> headerMap = mapHeaderIndices(headers);
            for (String logical : logicalHeaders.keySet()) {
                Integer idx = getHeaderIndex(headerMap, logicalHeaders.get(logical));
                if (idx == null) {
                    logger.error("Import file missing required column for '{}': tried {}", logical, Arrays.toString(logicalHeaders.get(logical)));
                    return new ImportResult(0, 0, 0, 1, List.of("Import file missing required column for: " + logical), importedLines);
                }
            }

            String[] fields;
            int lineNum = 2; // since header is line 1
            while ((fields = csvReader.readNext()) != null) {
                detectedCount++;
                try {
                    String hash = computeNormalizedTransactionHash(
                            getField(fields, headerMap, logicalHeaders.get("name")),
                            getField(fields, headerMap, logicalHeaders.get("amount")),
                            getField(fields, headerMap, logicalHeaders.get("category")),
                            getField(fields, headerMap, logicalHeaders.get("criticality")),
                            getField(fields, headerMap, logicalHeaders.get("transaction date")),
                            getField(fields, headerMap, logicalHeaders.get("account")),
                            getField(fields, headerMap, logicalHeaders.get("status")),
                            getField(fields, headerMap, logicalHeaders.get("created time")),
                            getField(fields, headerMap, logicalHeaders.get("payment method"))
                    );
                    logger.debug("Import row hash: fields='{}' -> hash={}", Arrays.toString(fields), hash);

                    if (existingHashes.contains(hash)) {
                        logger.warn("Duplicate transaction detected at line {} (skipping): {}", lineNum, Arrays.toString(fields));
                        duplicateCount++;
                        errorLines.add("DUPLICATE: " + Arrays.toString(fields));
                        lineNum++;
                        continue;
                    }
                    BudgetTransaction tx = new BudgetTransaction(
                            getField(fields, headerMap, logicalHeaders.get("name")),
                            getField(fields, headerMap, logicalHeaders.get("amount")),
                            getField(fields, headerMap, logicalHeaders.get("category")),
                            getField(fields, headerMap, logicalHeaders.get("criticality")),
                            getField(fields, headerMap, logicalHeaders.get("transaction date")),
                            getField(fields, headerMap, logicalHeaders.get("account")),
                            getField(fields, headerMap, logicalHeaders.get("status")),
                            getField(fields, headerMap, logicalHeaders.get("created time")),
                            getField(fields, headerMap, logicalHeaders.get("payment method"))
                    );
                    budgetFileService.add(tx);
                    existingHashes.add(hash);
                    importedCount++;
                    importedLines.add(Arrays.toString(fields));
                } catch (Exception ex) {
                    logger.error("Failed to import line {}: {}. Error: {}", lineNum, Arrays.toString(fields), ex.getMessage(), ex);
                    errorCount++;
                    errorLines.add(Arrays.toString(fields));
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

    private static String computeNormalizedTransactionHash(
            String name, String amount, String category, String criticality,
            String transactionDate, String account, String status,
            String createdTime, String paymentMethod
    ) {
        String nName = normalizeString(name);
        String nAmount = normalizeAmount(amount);
        String nCategory = normalizeString(category);
        String nCriticality = normalizeString(criticality);
        String nDate = normalizeDate(transactionDate);
        String nAccount = normalizeString(account);
        String nStatus = normalizeString(status);
        String nCreated = normalizeString(createdTime);
        String nPayMethod = normalizeString(paymentMethod);
        String hash = BudgetRowHashUtil.computeTransactionHash(nName, nAmount, nCategory, nCriticality, nDate, nAccount, nStatus, nCreated, nPayMethod);
        logger.debug("Normalized transaction hash key: [name='{}', amount='{}', category='{}', criticality='{}', date='{}', account='{}', status='{}', created='{}', payMethod='{}'] => hash={}",
                nName, nAmount, nCategory, nCriticality, nDate, nAccount, nStatus, nCreated, nPayMethod, hash);
        return hash;
    }

    private static String normalizeString(String s) {
        return s == null ? "" : s.trim().toLowerCase();
    }

    private static String normalizeAmount(String s) {
        if (s == null) return "0";
        try {
            String cleaned = s.replace("$", "").replace(",", "").trim();
            double val = Double.parseDouble(cleaned);
            String result = String.format("%.2f", val);
            logger.trace("normalizeAmount: '{}' -> '{}'", s, result);
            return result;
        } catch (Exception e) {
            logger.warn("Failed to normalize amount '{}': {}", s, e.getMessage());
            return s == null ? "" : s.replace("$", "").replace(",", "").trim();
        }
    }

    private static String normalizeDate(String dateStr) {
        if (dateStr == null) return "";
        dateStr = dateStr.trim();
        List<String> patterns = Arrays.asList("MMMM d, yyyy", "MMM d, yyyy", "yyyy-MM-dd", "M/d/yyyy", "MMMM d yyyy", "MMMM d, yyyy h:mm a");
        for (String pattern : patterns) {
            try {
                SimpleDateFormat in = new SimpleDateFormat(pattern, Locale.US);
                in.setLenient(false);
                Date date = in.parse(dateStr);
                SimpleDateFormat out = new SimpleDateFormat("yyyy-MM-dd");
                String result = out.format(date);
                logger.trace("normalizeDate: '{}' [{}] -> '{}'", dateStr, pattern, result);
                return result;
            } catch (ParseException e) {
                // ignore and try next
            }
        }
        logger.warn("Could not normalize date '{}', using as-is lowercased/trimmed.", dateStr);
        return dateStr.toLowerCase();
    }
}