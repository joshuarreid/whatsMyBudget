package model;

import org.slf4j.Logger;
import util.AppLogger;

/**
 * Represents a single budget transaction, extending BudgetRow.
 * Adds field for statement period.
 */
public class BudgetTransaction extends BudgetRow {
    private static final Logger logger = AppLogger.getLogger(BudgetTransaction.class);

    private final String statementPeriod;

    /**
     * Constructs a BudgetTransaction with all required fields, including statementPeriod.
     *
     * @param name             Transaction name/description
     * @param amount           Transaction amount (e.g., "$10.00")
     * @param category         Transaction category (e.g., "Dining")
     * @param criticality      "Essential" or "NonEssential"
     * @param transactionDate  Date of transaction
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
        logger.debug("BudgetTransaction successfully created with statementPeriod={}", statementPeriod);
    }

    /**
     * @return the statement period this transaction is associated with
     */
    public String getStatementPeriod() {
        logger.info("getStatementPeriod called, value={}", statementPeriod);
        return statementPeriod;
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
                '}';
    }
}