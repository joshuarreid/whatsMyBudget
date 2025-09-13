package ui;

import model.BudgetTransaction;
import model.BudgetTransactionList;
import org.slf4j.Logger;
import util.AppLogger;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * Panel for Anna's view: shows Essential and NonEssential spending by category.
 */
public class AnnaViewPanel extends JPanel {
    private static final Logger logger = AppLogger.getLogger(AnnaViewPanel.class);
    private final CategorySummaryPanel essentialPanel;
    private final CategorySummaryPanel nonEssentialPanel;

    /**
     * Constructs the AnnaViewPanel with category summary panels.
     */
    public AnnaViewPanel() {
        super(new GridLayout(2, 1));
        logger.info("Initializing AnnaViewPanel");
        essentialPanel = new CategorySummaryPanel("Anna", "Essential");
        nonEssentialPanel = new CategorySummaryPanel("Anna", "NonEssential");
        this.add(essentialPanel);
        this.add(nonEssentialPanel);
    }

    /**
     * Sets the transaction list for this view using a raw list.
     * @param transactions List of BudgetTransaction (may be null or empty)
     */
    public void setTransactions(List<BudgetTransaction> transactions) {
        logger.info("setTransactions(List) called on AnnaViewPanel with {} transaction(s)", transactions == null ? 0 : transactions.size());
        essentialPanel.setTransactions(transactions);
        nonEssentialPanel.setTransactions(transactions);
    }

    /**
     * Sets the transaction list for this view using a BudgetTransactionList model.
     * @param transactionList BudgetTransactionList (may be null)
     */
    public void setTransactions(BudgetTransactionList transactionList) {
        logger.info("setTransactions(BudgetTransactionList) called on AnnaViewPanel");
        essentialPanel.setTransactions(transactionList);
        nonEssentialPanel.setTransactions(transactionList);
    }
}