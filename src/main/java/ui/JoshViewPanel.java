package ui;

import model.BudgetTransaction;
import model.BudgetTransactionList;
import org.slf4j.Logger;
import util.AppLogger;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * Panel for Josh's view: shows Essential and NonEssential spending by category.
 */
public class JoshViewPanel extends JPanel {
    private static final Logger logger = AppLogger.getLogger(JoshViewPanel.class);
    private final CategorySummaryPanel essentialPanel;
    private final CategorySummaryPanel nonEssentialPanel;

    /**
     * Constructs the JoshViewPanel with category summary panels.
     */
    public JoshViewPanel() {
        super(new GridLayout(2, 1));
        logger.info("Initializing JoshViewPanel");
        essentialPanel = new CategorySummaryPanel("Josh", "Essential");
        nonEssentialPanel = new CategorySummaryPanel("Josh", "NonEssential");
        this.add(essentialPanel);
        this.add(nonEssentialPanel);
    }

    /**
     * Sets the transaction list for this view using a raw list.
     * @param transactions List of BudgetTransaction (may be null or empty)
     */
    public void setTransactions(List<BudgetTransaction> transactions) {
        logger.info("setTransactions(List) called on JoshViewPanel with {} transaction(s)", transactions == null ? 0 : transactions.size());
        essentialPanel.setTransactions(transactions);
        nonEssentialPanel.setTransactions(transactions);
    }

    /**
     * Sets the transaction list for this view using a BudgetTransactionList model.
     * @param transactionList BudgetTransactionList (may be null)
     */
    public void setTransactions(BudgetTransactionList transactionList) {
        logger.info("setTransactions(BudgetTransactionList) called on JoshViewPanel");
        essentialPanel.setTransactions(transactionList);
        nonEssentialPanel.setTransactions(transactionList);
    }
}