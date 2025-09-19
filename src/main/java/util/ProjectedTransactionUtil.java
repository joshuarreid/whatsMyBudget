package util;

import model.ProjectedTransaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Utility class for handling ProjectedTransaction business logic,
 * including splitting "Joint" projections for individual accounts.
 */
public class ProjectedTransactionUtil {
    private static final Logger logger = LoggerFactory.getLogger(ProjectedTransactionUtil.class);

    /**
     * Returns a new list of ProjectedTransaction for the given account, where:
     * - All projections with Account == accountName are included as-is.
     * - All projections with Account == "Joint" are duplicated with amount halved and assigned to accountName.
     * - All others are ignored.
     *
     * This mimics the logic for splitting real "Joint" transactions in the app.
     *
     * @param projections Input list of ProjectedTransaction (may be null or empty)
     * @param accountName The target account (e.g. "Josh", "Anna")
     * @return List of ProjectedTransaction for the given account, with "Joint" projections split/assigned.
     */
    public static List<ProjectedTransaction> splitJointProjectedTransactionsForAccount(List<ProjectedTransaction> projections, String accountName) {
        logger.info("splitJointProjectedTransactionsForAccount called for account '{}', input size={}", accountName, projections == null ? 0 : projections.size());
        if (projections == null || projections.isEmpty() || accountName == null || accountName.isBlank()) {
            logger.warn("Input projections list is null/empty or accountName is null/blank. Returning empty list.");
            return Collections.emptyList();
        }
        List<ProjectedTransaction> result = new ArrayList<>();
        for (ProjectedTransaction tx : projections) {
            if (accountName.equalsIgnoreCase(tx.getAccount())) {
                // Own projections: include as-is
                result.add(tx);
            } else if ("Joint".equalsIgnoreCase(tx.getAccount())) {
                // Split joint projection: halve amount, assign to accountName
                try {
                    double originalAmount = tx.getAmountValue();
                    double splitAmount = originalAmount / 2.0;
                    ProjectedTransaction splitTx = new ProjectedTransaction(
                            tx.getName(),
                            String.format("%.2f", splitAmount),
                            tx.getCategory(),
                            tx.getCriticality(),
                            tx.getTransactionDate(),
                            accountName,
                            tx.getStatus(),
                            tx.getCreatedTime(),
                            tx.getStatementPeriod()
                    );
                    result.add(splitTx);
                    logger.info("Split Joint projected transaction '{}' (${:.2f}) to '{}' (${:.2f}) for account '{}'", tx.getName(), originalAmount, splitAmount, accountName);
                } catch (Exception ex) {
                    logger.error("Error splitting Joint projected transaction '{}': {}", tx.getName(), ex.getMessage(), ex);
                }
            }
            // Ignore projections for other accounts
        }
        logger.info("splitJointProjectedTransactionsForAccount for account '{}' returning {} transaction(s)", accountName, result.size());
        return result;
    }
}