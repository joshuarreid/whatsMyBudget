package model;

import org.slf4j.Logger;
import util.AppLogger;

import java.io.Serializable;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Hybrid wrapper for a list of BudgetTransaction objects.
 * Provides both flat list access and efficient category breakdown via map.
 * Supports flexible filtering by each CSV header/field and account.
 * Maintains total amount (sum) and total count of transactions for reporting.
 * Splits "Joint" transactions in half for individual accounts (Josh, Anna).
 */
public class BudgetTransactionList implements Serializable {
    private static final Logger logger = AppLogger.getLogger(BudgetTransactionList.class);

    private final List<BudgetTransaction> transactions;
    private final int total;
    private final String description;
    private final double totalAmount;
    private final int totalCount;

    private Map<String, List<BudgetTransaction>> categoryMap;

    public BudgetTransactionList(List<BudgetTransaction> transactions, String description) {
        logger.info("BudgetTransactionList constructor entered with {} transactions, description={}",
                transactions == null ? 0 : transactions.size(), description);
        if (transactions == null) {
            this.transactions = Collections.emptyList();
            this.total = 0;
            logger.warn("Null transactions list provided; using empty list.");
        } else {
            this.transactions = List.copyOf(transactions);
            this.total = this.transactions.size();
        }
        this.description = description;
        this.totalAmount = computeTotalAmount(this.transactions);
        this.totalCount = this.transactions.size();
        logger.info("BudgetTransactionList initialized: totalAmount=${}, totalCount={}", this.totalAmount, this.totalCount);
    }

    /**
     * Returns the immutable list of all transactions.
     */
    public List<BudgetTransaction> getTransactions() {
        logger.info("getTransactions called, returning {} transactions", transactions.size());
        return transactions;
    }

    /**
     * Returns the total number of transactions.
     */
    public int getTotal() {
        logger.info("getTotal called, value={}", total);
        return total;
    }

    /**
     * Returns the optional description for this list.
     */
    public String getDescription() {
        logger.info("getDescription called, value={}", description);
        return description;
    }

    /**
     * Returns a map of category -> List of BudgetTransaction for efficient breakdowns.
     */
    public Map<String, List<BudgetTransaction>> byCategory() {
        logger.info("byCategory called");
        if (categoryMap == null) {
            categoryMap = transactions.stream()
                    .collect(Collectors.groupingBy(tx -> {
                        String cat = tx.getCategory();
                        return cat == null ? "" : cat;
                    }));
            logger.info("Category map built with {} categories", categoryMap.size());
        } else {
            logger.info("Category map retrieved from cache with {} categories", categoryMap.size());
        }
        return categoryMap;
    }

    // ------------------- Filter Methods For Each Header (with account) -------------------

    public BudgetTransactionList filterByName(String name) {
        return filterByName(name, null);
    }

    public BudgetTransactionList filterByName(String name, String account) {
        logger.info("filterByName called with name='{}', account='{}'", name, account);
        return filterHelper("Name", name, BudgetTransaction::getName, account);
    }

    public BudgetTransactionList filterByAmount(String amount) {
        return filterByAmount(amount, null);
    }

    public BudgetTransactionList filterByAmount(String amount, String account) {
        logger.info("filterByAmount called with amount='{}', account='{}'", amount, account);
        return filterHelper("Amount", amount, BudgetTransaction::getAmount, account);
    }

    public BudgetTransactionList filterByCategory(String category) {
        return filterByCategory(category, null);
    }

    public BudgetTransactionList filterByCategory(String category, String account) {
        logger.info("filterByCategory called with category='{}', account='{}'", category, account);
        return filterHelper("Category", category, BudgetTransaction::getCategory, account);
    }

    public BudgetTransactionList filterByCriticality(String criticality) {
        return filterByCriticality(criticality, null);
    }

    public BudgetTransactionList filterByCriticality(String criticality, String account) {
        logger.info("filterByCriticality called with criticality='{}', account='{}'", criticality, account);
        return filterHelper("Criticality", criticality, BudgetTransaction::getCriticality, account);
    }

    public BudgetTransactionList filterByTransactionDate(String transactionDate) {
        return filterByTransactionDate(transactionDate, null);
    }

    public BudgetTransactionList filterByTransactionDate(String transactionDate, String account) {
        logger.info("filterByTransactionDate called with date='{}', account='{}'", transactionDate, account);
        return filterHelper("Transaction Date", transactionDate, BudgetTransaction::getTransactionDate, account);
    }

    /**
     * Filters by account. For "Josh" or "Anna", splits joint transactions and includes half for each.
     * @param account The account to filter by ("Josh", "Anna", "Joint", etc.)
     * @return a new BudgetTransactionList with filtered (and split) transactions
     */
    public BudgetTransactionList filterByAccount(String account) {
        logger.info("filterByAccount called with account='{}'", account);
        if (account == null) {
            logger.warn("filterByAccount called with null account; returning original list");
            return this;
        }
        String acct = account.trim();
        List<BudgetTransaction> filtered = new ArrayList<>();
        for (BudgetTransaction tx : transactions) {
            if (acct.equalsIgnoreCase("Josh") || acct.equalsIgnoreCase("Anna")) {
                if (tx.getAccount().equalsIgnoreCase(acct)) {
                    filtered.add(tx);
                } else if (tx.getAccount().equalsIgnoreCase("Joint")) {
                    BudgetTransaction splitTx = splitJointTransactionForAccount(tx, acct);
                    if (splitTx != null) {
                        filtered.add(splitTx);
                    }
                }
            } else {
                if (tx.getAccount().equalsIgnoreCase(acct)) {
                    filtered.add(tx);
                }
            }
        }
        logger.info("filterByAccount returning {} transactions for account '{}'", filtered.size(), account);
        return new BudgetTransactionList(filtered, this.description + " (Filtered: Account=" + account + ")");
    }

    public BudgetTransactionList filterByStatus(String status) {
        return filterByStatus(status, null);
    }

    public BudgetTransactionList filterByStatus(String status, String account) {
        logger.info("filterByStatus called with status='{}', account='{}'", status, account);
        return filterHelper("Status", status, BudgetTransaction::getStatus, account);
    }

    public BudgetTransactionList filterByCreatedTime(String createdTime) {
        return filterByCreatedTime(createdTime, null);
    }

    public BudgetTransactionList filterByCreatedTime(String createdTime, String account) {
        logger.info("filterByCreatedTime called with createdTime='{}', account='{}'", createdTime, account);
        return filterHelper("Created time", createdTime, BudgetTransaction::getCreatedTime, account);
    }

    public BudgetTransactionList filterByPaymentMethod(String paymentMethod) {
        return filterByPaymentMethod(paymentMethod, null);
    }

    public BudgetTransactionList filterByPaymentMethod(String paymentMethod, String account) {
        logger.info("filterByPaymentMethod called with paymentMethod='{}', account='{}'", paymentMethod, account);
        return filterHelper("Payment Method", paymentMethod, BudgetTransaction::getPaymentMethod, account);
    }

    public BudgetTransactionList filterByStatementPeriod(String statementPeriod) {
        return filterByStatementPeriod(statementPeriod, null);
    }

    public BudgetTransactionList filterByStatementPeriod(String statementPeriod, String account) {
        logger.info("filterByStatementPeriod called with statementPeriod='{}', account='{}'", statementPeriod, account);
        return filterHelper("Statement Period", statementPeriod, BudgetTransaction::getStatementPeriod, account);
    }

    /**
     * Generic filter method by column name (as in the CSV header) and value, with account support.
     * @param column The CSV header/field name
     * @param value Value to match (case-insensitive, trimmed)
     * @param account Optional account to filter by
     * @return Filtered BudgetTransactionList
     */
    public BudgetTransactionList filter(String column, String value, String account) {
        logger.info("filter called with column='{}', value='{}', account='{}'", column, value, account);
        if (column == null || value == null) {
            logger.warn("Null column or value for filter; returning original list");
            return this;
        }
        switch (column.trim().toLowerCase(Locale.ROOT)) {
            case "name": return filterByName(value, account);
            case "amount": return filterByAmount(value, account);
            case "category": return filterByCategory(value, account);
            case "criticality": return filterByCriticality(value, account);
            case "transaction date": return filterByTransactionDate(value, account);
            case "account": return filterByAccount(value);
            case "status": return filterByStatus(value, account);
            case "created time": return filterByCreatedTime(value, account);
            case "payment method": return filterByPaymentMethod(value, account);
            case "statement period": return filterByStatementPeriod(value, account);
            default:
                logger.error("Unknown column '{}' for filter; returning original list", column);
                return this;
        }
    }

    // --------------- Helper for Filtering By Field and Account ---------------

    private BudgetTransactionList filterHelper(
            String field, String value, Function<BudgetTransaction, String> getter, String account
    ) {
        logger.info("filterHelper called with field='{}', value='{}', account='{}'", field, value, account);
        if (field.equalsIgnoreCase("Account")) {
            // Use the account splitting logic for "Josh" and "Anna"
            return filterByAccount(value);
        }
        List<BudgetTransaction> filtered = new ArrayList<>();
        for (BudgetTransaction tx : transactions) {
            boolean matchesValue = (value == null ||
                    (getter.apply(tx) != null && getter.apply(tx).trim().equalsIgnoreCase(value.trim())));
            if (account == null) {
                if (matchesValue) {
                    filtered.add(tx);
                }
            } else if (account.equalsIgnoreCase("Josh") || account.equalsIgnoreCase("Anna")) {
                if (tx.getAccount().equalsIgnoreCase(account) && matchesValue) {
                    filtered.add(tx);
                } else if (tx.getAccount().equalsIgnoreCase("Joint") && matchesValue) {
                    BudgetTransaction splitTx = splitJointTransactionForAccount(tx, account);
                    if (splitTx != null) {
                        filtered.add(splitTx);
                    }
                }
            } else {
                if (tx.getAccount().equalsIgnoreCase(account) && matchesValue) {
                    filtered.add(tx);
                }
            }
        }
        logger.info("filterHelper: '{}'='{}', account='{}' -> {} transactions", field, value, account, filtered.size());
        String desc = (description == null ? "" : description + " ") + "(Filtered: " + field + "=" + value;
        if (account != null) desc += ", Account=" + account;
        desc += ")";
        return new BudgetTransactionList(filtered, desc.trim());
    }

    /**
     * Splits a joint transaction for an individual account.
     * Amount is divided by 2, account is set to the individual.
     * @param tx The original joint transaction
     * @param account The individual account ("Josh" or "Anna")
     * @return The split BudgetTransaction, or null if error
     */
    private BudgetTransaction splitJointTransactionForAccount(BudgetTransaction tx, String account) {
        logger.info("splitJointTransactionForAccount called for account '{}', transaction '{}'", account, tx);
        try {
            double originalAmount = parseAmount(tx.getAmount());
            double splitAmount = originalAmount / 2.0;
            String newAmount = String.format("$%.2f", splitAmount);
            // Create a copy with adjusted fields
            BudgetTransaction splitTx = new BudgetTransaction(
                    tx.getName() + " [Split Joint]",
                    newAmount,
                    tx.getCategory(),
                    tx.getCriticality(),
                    tx.getTransactionDate(),
                    account,
                    tx.getStatus(),
                    tx.getCreatedTime(),
                    tx.getStatementPeriod()
            );
            logger.info("Joint transaction '{}' split for '{}': amount {} -> {}", tx.getName(), account, tx.getAmount(), newAmount);
            return splitTx;
        } catch (Exception e) {
            logger.error("Failed to split joint transaction '{}': {}", tx, e.getMessage(), e);
            return null;
        }
    }

    // ------------------- Personalized Transaction Halving Logic -------------------

    /**
     * Returns transactions for a given individual, including their half of all Joint transactions (amount halved).
     * This should be used by Josh/Anna views and drilldowns to ensure correct split for all summaries.
     * @param individual The individual account (e.g., "Josh", "Anna")
     * @param criticality Criticality filter ("Essential", "NonEssential", or null for all)
     * @return List of BudgetTransaction, with Joint transactions halved and attributed to the individual
     */
    public List<BudgetTransaction> getPersonalizedTransactions(String individual, String criticality) {
        logger.info("getPersonalizedTransactions called for individual='{}', criticality='{}'", individual, criticality);
        List<BudgetTransaction> personalized = new ArrayList<>();
        if (individual == null) {
            logger.warn("getPersonalizedTransactions called with null individual; returning original list");
            return new ArrayList<>(transactions);
        }
        for (BudgetTransaction tx : transactions) {
            boolean matchesCriticality = (criticality == null || criticality.equalsIgnoreCase(tx.getCriticality()));
            if (individual.equalsIgnoreCase(tx.getAccount())) {
                if (matchesCriticality) {
                    personalized.add(tx);
                }
            } else if ("Joint".equalsIgnoreCase(tx.getAccount())) {
                if (matchesCriticality) {
                    BudgetTransaction halfTx = splitJointTransactionForAccount(tx, individual);
                    if (halfTx != null) {
                        personalized.add(halfTx);
                    }
                }
            }
        }
        logger.info("getPersonalizedTransactions returning {} transactions for '{}'", personalized.size(), individual);
        return personalized;
    }

    // ------------------- Totals and Amounts -------------------

    /**
     * Returns the sum of all transaction amounts in this list.
     * @return double total amount
     */
    public double getTotalAmount() {
        logger.info("getTotalAmount called, value={}", totalAmount);
        return totalAmount;
    }

    /**
     * Returns the total count of transactions in this list.
     * @return int total count
     */
    public int getTotalCount() {
        logger.info("getTotalCount called, value={}", totalCount);
        return totalCount;
    }

    /**
     * Utility method to sum the amounts in a list of transactions.
     */
    private static double computeTotalAmount(List<BudgetTransaction> transactions) {
        logger.info("computeTotalAmount called on {} transactions", transactions == null ? 0 : transactions.size());
        double sum = 0.0;
        if (transactions == null) return sum;
        for (BudgetTransaction tx : transactions) {
            String amt = tx.getAmount();
            try {
                sum += parseAmount(amt);
            } catch (NumberFormatException e) {
                logger.warn("Failed to parse amount '{}': {}", amt, e.getMessage());
            }
        }
        logger.info("Computed total amount: {}", sum);
        return sum;
    }

    private static double parseAmount(String amt) {
        if (amt == null) return 0.0;
        String clean = amt.replace("$", "").replace(",", "").trim();
        if (clean.isEmpty()) return 0.0;
        return Double.parseDouble(clean);
    }

    // ------------------- Convenience Methods for Panels -------------------

    /**
     * Returns a filtered list for a specific account and criticality.
     * @param account Account name (e.g., "Josh", "Anna", "Joint")
     * @param criticality Criticality ("Essential", "NonEssential")
     * @return List of BudgetTransaction
     */
    public List<BudgetTransaction> getByAccountAndCriticality(String account, String criticality) {
        logger.info("getByAccountAndCriticality called with account='{}', criticality='{}'", account, criticality);
        List<BudgetTransaction> filtered = this.filterByAccount(account)
                .filterByCriticality(criticality)
                .getTransactions();
        logger.info("getByAccountAndCriticality returning {} transactions", filtered.size());
        return filtered;
    }

    /**
     * Returns a map of category -> total amount, filtered by account and criticality.
     * @param account Account name to filter
     * @param criticality Criticality to filter
     * @return Map of category to total amount
     */
    public Map<String, Double> getCategoryTotals(String account, String criticality) {
        logger.info("getCategoryTotals called with account='{}', criticality='{}'", account, criticality);
        List<BudgetTransaction> filtered = getByAccountAndCriticality(account, criticality);
        Map<String, Double> result = filtered.stream()
                .collect(Collectors.groupingBy(
                        tx -> tx.getCategory() == null ? "(Uncategorized)" : tx.getCategory(),
                        Collectors.summingDouble(tx -> parseAmount(tx.getAmount()))
                ));
        logger.info("getCategoryTotals returning {} categories", result.size());
        return result;
    }

    @Override
    public boolean equals(Object o) {
        logger.info("equals called");
        if (this == o) return true;
        if (!(o instanceof BudgetTransactionList)) return false;
        BudgetTransactionList that = (BudgetTransactionList) o;
        return total == that.total &&
                Objects.equals(transactions, that.transactions) &&
                Objects.equals(description, that.description) &&
                Double.compare(totalAmount, that.totalAmount) == 0 &&
                totalCount == that.totalCount;
    }

    @Override
    public int hashCode() {
        logger.info("hashCode called");
        return Objects.hash(transactions, total, description, totalAmount, totalCount);
    }

    @Override
    public String toString() {
        return "BudgetTransactionList{" +
                "transactions=" + transactions +
                ", total=" + total +
                ", description='" + description + '\'' +
                ", totalAmount=" + totalAmount +
                ", totalCount=" + totalCount +
                '}';
    }
}