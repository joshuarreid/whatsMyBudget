package model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import util.AppLogger;

/**
 * Represents a projected (planned) transaction for a statement period.
 * Used for upcoming known expenses that should be included in budgeting and totals, but are not yet actual transactions.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@NoArgsConstructor
public class ProjectedTransaction extends BudgetTransaction {
    private static final Logger logger = AppLogger.getLogger(ProjectedTransaction.class);

    /**
     * Indicates that this transaction is a projection, not an actual transaction.
     */
    private final boolean isProjected = true;

    /**
     * Constructs a projected transaction with all standard transaction fields.
     * @param name         Name/description of the expense
     * @param amount       Amount as a string (e.g., "$100.00")
     * @param category     Category (or null)
     * @param criticality  "Essential" or "NonEssential"
     * @param transactionDate Intended payment date as a string (e.g., "September 29, 2025")
     * @param account      Account (e.g., "Josh", "Anna", "Joint")
     * @param status       Optional status
     * @param createdTime  When projection was created (may be null)
     * @param statementPeriod The statement period this projection is for (e.g., "2025-10-13_to_2025-11-12")
     */
    public ProjectedTransaction(String name, String amount, String category, String criticality,
                                String transactionDate, String account, String status, String createdTime,
                                String statementPeriod) {
        super(name, amount, category, criticality, transactionDate, account, status, createdTime, statementPeriod);
        logger.info("Creating ProjectedTransaction: name='{}', amount='{}', category='{}', criticality='{}', transactionDate='{}', account='{}', status='{}', createdTime='{}', statementPeriod='{}'",
                name, amount, category, criticality, transactionDate, account, status, createdTime, statementPeriod);

        if (statementPeriod == null || statementPeriod.isBlank()) {
            logger.error("Invalid statementPeriod for ProjectedTransaction: '{}'", statementPeriod);
            throw new IllegalArgumentException("Projected statement period must not be null or blank.");
        }
    }

    /**
     * Always returns true for projected transactions.
     * @return true
     */
    public boolean isProjected() {
        logger.info("isProjected called, returning true");
        return isProjected;
    }

    /**
     * Returns a BudgetTransaction copy of this ProjectedTransaction.
     * All fields are copied. Logs method entry, field values, and result.
     * @return BudgetTransaction with identical data
     */
    public BudgetTransaction asBudgetTransaction() {
        logger.info("asBudgetTransaction called for ProjectedTransaction: name='{}', amount='{}', category='{}', criticality='{}', transactionDate='{}', account='{}', status='{}', createdTime='{}', statementPeriod='{}'",
                getName(), getAmount(), getCategory(), getCriticality(), getTransactionDate(), getAccount(), getStatus(), getCreatedTime(), getStatementPeriod());
        BudgetTransaction bt = new BudgetTransaction(
                getName(),
                getAmount(),
                getCategory(),
                getCriticality(),
                getTransactionDate(),
                getAccount(),
                getStatus(),
                getCreatedTime(),
                getStatementPeriod()
        );
        logger.info("asBudgetTransaction returning BudgetTransaction: name='{}', amount='{}', category='{}', criticality='{}', transactionDate='{}', account='{}', status='{}', createdTime='{}', statementPeriod='{}'",
                bt.getName(), bt.getAmount(), bt.getCategory(), bt.getCriticality(), bt.getTransactionDate(), bt.getAccount(), bt.getStatus(), bt.getCreatedTime(), bt.getStatementPeriod());
        return bt;
    }
}