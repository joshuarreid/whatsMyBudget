package model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Objects;

/**
 * Represents a transaction for a budgeting statement period, extending BudgetRow.
 * Adds statementPeriod and a routine flag (for recurring/routine spendings).
 */

public class BudgetTransaction extends BudgetRow {
    private static final Logger logger = LoggerFactory.getLogger(BudgetTransaction.class);

    /**
     * The credit card statement period this transaction belongs to.
     */
    private String statementPeriod;

    /**
     * True if this is a routine/recurring spending, false if out-of-cycle/unexpected.
     */
    private boolean routine;

    /**
     * Default constructor.
     */
    public BudgetTransaction() {
        super();
        logger.debug("BudgetTransaction default constructor called.");
    }

    /**
     * Constructs a BudgetTransaction with all base fields, plus statementPeriod and routine.
     *
     * @param name            the name/description of the transaction
     * @param amount          the transaction amount
     * @param category        the spending category
     * @param criticality     criticality or importance
     * @param transactionDate date of transaction
     * @param account         associated account
     * @param status          status (if any)
     * @param createdTime     creation time (if any)
     * @param statementPeriod credit statement period label (must not be null)
     * @param routine         true if routine/recurring, false if out of cycle
     */
    public BudgetTransaction(String name, String amount, String category, String criticality, String transactionDate,
                             String account, String status, String createdTime, String statementPeriod, boolean routine) {
        super(name, amount, category, criticality, transactionDate, account, status, createdTime);
        if (statementPeriod == null) {
            logger.error("Attempted to create BudgetTransaction with null statementPeriod");
            throw new IllegalArgumentException("statementPeriod cannot be null");
        }
        this.statementPeriod = statementPeriod;
        this.routine = routine;
        logger.debug("BudgetTransaction full constructor called with statementPeriod={}, routine={}", statementPeriod, routine);
    }

    /**
     * Gets the statement period for this transaction.
     * @return the statement period label
     */
    public String getStatementPeriod() {
        logger.debug("getStatementPeriod called, returning {}", statementPeriod);
        return statementPeriod;
    }

    /**
     * Sets the statement period for this transaction.
     * @param statementPeriod the statement period label; must not be null
     */
    public void setStatementPeriod(String statementPeriod) {
        logger.debug("setStatementPeriod called with {}", statementPeriod);
        if (statementPeriod == null) {
            logger.error("Attempted to set null statementPeriod");
            throw new IllegalArgumentException("statementPeriod cannot be null");
        }
        this.statementPeriod = statementPeriod;
    }

    /**
     * Returns whether this transaction is routine/recurring.
     * @return true if routine, false otherwise
     */
    public boolean isRoutine() {
        logger.debug("isRoutine called, returning {}", routine);
        return routine;
    }

    /**
     * Sets whether this transaction is routine/recurring.
     * @param routine true if routine, false otherwise
     */
    public void setRoutine(boolean routine) {
        logger.debug("setRoutine called with {}", routine);
        this.routine = routine;
    }

    /**
     * Generates a hash for this transaction for duplicate detection.
     * Uses key fields that uniquely identify a transaction.
     * @return hash string
     */
    public String getTransactionHash() {
        String source = getName() + "|" + getAmount() + "|" + getCategory() + "|" +
                getTransactionDate() + "|" + getAccount() + "|" + statementPeriod;
        String hash = Integer.toHexString(source.hashCode());
        logger.debug("getTransactionHash called, source='{}', hash={}", source, hash);
        return hash;
    }

    /**
     * Equality based on all relevant fields for duplicate detection.
     * @param o the object to compare to
     * @return true if duplicate, false otherwise
     */
    @Override
    public boolean equals(Object o) {
        logger.debug("equals called with object={}", o);
        if (this == o) return true;
        if (!(o instanceof BudgetTransaction)) return false;
        BudgetTransaction that = (BudgetTransaction) o;
        boolean result = Objects.equals(getName(), that.getName())
                && Objects.equals(getAmount(), that.getAmount())
                && Objects.equals(getCategory(), that.getCategory())
                && Objects.equals(getTransactionDate(), that.getTransactionDate())
                && Objects.equals(getAccount(), that.getAccount())
                && Objects.equals(statementPeriod, that.statementPeriod)
                && routine == that.routine;
        logger.debug("equals result: {}", result);
        return result;
    }

    @Override
    public int hashCode() {
        int hash = Objects.hash(getName(), getAmount(), getCategory(), getTransactionDate(),
                getAccount(), statementPeriod, routine);
        logger.debug("hashCode called, returning {}", hash);
        return hash;
    }

    @Override
    public String toString() {
        logger.debug("toString called.");
        return "BudgetTransaction{" +
                "name=" + getName() +
                ", amount=" + getAmount() +
                ", category=" + getCategory() +
                ", criticality=" + getCriticality() +
                ", transactionDate=" + getTransactionDate() +
                ", account=" + getAccount() +
                ", status=" + getStatus() +
                ", createdTime=" + getCreatedTime() +
                ", statementPeriod=" + statementPeriod +
                ", routine=" + routine +
                '}';
    }
}