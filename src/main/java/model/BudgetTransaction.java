package model;

import org.slf4j.Logger;
import util.AppLogger;

import java.time.LocalDate;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Represents a single budget transaction, extending BudgetRow.
 * Adds field for statement period and parsed date.
 */
public class BudgetTransaction extends BudgetRow {
    private static final Logger logger = AppLogger.getLogger(BudgetTransaction.class);

    private final String statementPeriod;
    private final LocalDate parsedTransactionDate;

    /**
     * Constructs a BudgetTransaction with all required fields, including statementPeriod and parses the date.
     *
     * @param name             Transaction name/description
     * @param amount           Transaction amount (e.g., "$10.00")
     * @param category         Transaction category (e.g., "Dining")
     * @param criticality      "Essential" or "NonEssential"
     * @param transactionDate  Date of transaction (e.g., "September 12, 2025")
     * @param account          Account used ("Josh", "Anna", "Joint", etc.)
     * @param status           Status (empty or custom)
     * @param createdTime      Time the entry was created
     * @param statementPeriod  The associated statement period (must not be null)
     */
    public BudgetTransaction(String name, String amount, String category, String criticality, String transactionDate,
                             String account, String status, String createdTime, String statementPeriod) {
        super(name, amount, category, criticality, transactionDate, account, status, createdTime);
        logger.debug("BudgetTransaction constructor called with "
                        + "name={}, amount={}, category={}, criticality={}, transactionDate={}, account={}, status={}, createdTime={}, statementPeriod={}",
                name, amount, category, criticality, transactionDate, account, status, createdTime, statementPeriod);

        if (statementPeriod == null) {
            logger.error("Attempted to create BudgetTransaction with null statementPeriod");
            throw new IllegalArgumentException("statementPeriod cannot be null");
        }
        this.statementPeriod = statementPeriod;

        // Parse transaction date
        LocalDate parsedDate = null;
        if (transactionDate != null && !transactionDate.trim().isEmpty()) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat("MMMM d, yyyy", Locale.ENGLISH);
                Date legacyDate = sdf.parse(transactionDate.trim());
                parsedDate = legacyDate.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate();
                logger.debug("Successfully parsed transactionDate '{}' to LocalDate {}", transactionDate, parsedDate);
            } catch (ParseException e) {
                logger.error("Failed to parse transactionDate '{}': {}", transactionDate, e.getMessage());
            }
        } else {
            logger.warn("transactionDate is null or empty for transaction '{}'", name);
        }
        this.parsedTransactionDate = parsedDate;
        logger.debug("BudgetTransaction successfully created with statementPeriod={} and parsedTransactionDate={}", statementPeriod, parsedTransactionDate);
    }

    /**
     * @return the statement period this transaction is associated with
     */
    public String getStatementPeriod() {
        logger.info("getStatementPeriod called, value={}", statementPeriod);
        return statementPeriod;
    }

    /**
     * Returns the parsed LocalDate representation of the transaction date, or null if unparseable.
     * @return LocalDate transaction date or null
     */
    public LocalDate getDate() {
        logger.info("getDate called, returning {}", parsedTransactionDate);
        return parsedTransactionDate;
    }

    /**
     * Returns the transaction amount as a double, parsing the amount string.
     * Handles $, commas, and empty/null values robustly.
     * @return parsed double value of amount, or 0.0 if unparseable
     */
    public double getAmountValue() {
        logger.info("getAmountValue called for amount '{}'", getAmount());
        String amt = getAmount();
        if (amt == null) {
            logger.warn("Amount is null, returning 0.0");
            return 0.0;
        }
        String clean = amt.replace("$", "").replace(",", "").trim();
        if (clean.isEmpty()) {
            logger.warn("Amount string '{}' is empty after cleaning, returning 0.0", amt);
            return 0.0;
        }
        try {
            double result = Double.parseDouble(clean);
            logger.debug("Successfully parsed amount '{}' to {}", amt, result);
            return result;
        } catch (NumberFormatException e) {
            logger.error("Failed to parse amount '{}': {}", amt, e.getMessage());
            return 0.0;
        }
    }

    @Override
    public String toString() {
        logger.info("toString called");
        return "BudgetTransaction{" +
                "name='" + getName() + '\'' +
                ", amount='" + getAmount() + '\'' +
                ", category='" + getCategory() + '\'' +
                ", criticality='" + getCriticality() + '\'' +
                ", transactionDate='" + getTransactionDate() + '\'' +
                ", account='" + getAccount() + '\'' +
                ", status='" + getStatus() + '\'' +
                ", createdTime='" + getCreatedTime() + '\'' +
                ", statementPeriod='" + statementPeriod + '\'' +
                ", parsedTransactionDate=" + parsedTransactionDate +
                '}';
    }
}