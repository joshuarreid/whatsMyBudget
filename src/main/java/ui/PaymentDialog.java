package ui;

import model.BudgetTransaction;
import org.slf4j.Logger;
import service.CSVStateService;
import util.AppLogger;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.List;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Dialog for viewing Anna's and Josh's payment responsibilities per credit card,
 * including side-by-side category breakdowns for each person, per card.
 * Uses PaymentSummaryPanel for the summary table at the top.
 * Panels are dynamically created for each detected card/payment method.
 * Robust logging and error handling included.
 */
public class PaymentDialog extends JDialog {
    private static final Logger logger = AppLogger.getLogger(PaymentDialog.class);

    private final CSVStateService csvStateService;
    private PaymentSummaryPanel summaryPanel;
    private JPanel breakdownPanel;

    /**
     * Constructs a PaymentDialog.
     * @param parent           The parent window (can be null).
     * @param csvStateService  Reference to the CSVStateService for fetching transactions.
     */
    public PaymentDialog(Window parent, CSVStateService csvStateService) {
        super(parent, "Payment Summary", ModalityType.APPLICATION_MODAL);
        logger.info("Initializing PaymentDialog.");
        this.csvStateService = csvStateService;

        setSize(900, 600);
        setLocationRelativeTo(parent);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        buildUI();

        logger.info("PaymentDialog initialized successfully.");
    }

    /**
     * Builds the UI: PaymentSummaryPanel at top, dynamic breakdown panels below.
     */
    private void buildUI() {
        logger.info("Building PaymentDialog UI.");
        setLayout(new BorderLayout());

        // PaymentSummaryPanel at the top, with fixed preferred height for the table
        summaryPanel = new PaymentSummaryPanel();
        JPanel summaryWrapper = new JPanel(new BorderLayout());
        summaryWrapper.setBorder(new EmptyBorder(10, 10, 10, 10));

        // Set a fixed preferred height for the summary table
        summaryPanel.setPreferredSize(new Dimension(Short.MAX_VALUE, 110));
        summaryWrapper.add(summaryPanel, BorderLayout.CENTER);
        add(summaryWrapper, BorderLayout.NORTH);

        // The panel that will hold the dynamic breakdown panels
        breakdownPanel = new JPanel();
        breakdownPanel.setLayout(new BoxLayout(breakdownPanel, BoxLayout.Y_AXIS));
        JScrollPane breakdownScroll = new JScrollPane(breakdownPanel);
        breakdownScroll.setBorder(new EmptyBorder(10, 10, 10, 10));
        // Expand breakdown area to fill remaining space
        breakdownScroll.setPreferredSize(new Dimension(Short.MAX_VALUE, 450));
        add(breakdownScroll, BorderLayout.CENTER);

        // Load and display all data
        loadAndDisplayPaymentSummary();
    }

    /**
     * Loads transactions, updates the PaymentSummaryPanel, and dynamically creates
     * category breakdown panels for each card/account combination.
     */
    private void loadAndDisplayPaymentSummary() {
        logger.info("Loading and displaying payment summary and breakdowns.");
        List<BudgetTransaction> transactions;
        try {
            transactions = csvStateService.getCurrentTransactions();
            logger.info("Fetched {} transactions from CSVStateService.", transactions.size());
        } catch (Exception ex) {
            logger.error("Failed to fetch transactions from CSVStateService: {}", ex.getMessage(), ex);
            transactions = Collections.emptyList();
        }

        summaryPanel.setTransactions(transactions);

        if (transactions == null || transactions.isEmpty()) {
            logger.warn("No transactions available. Breakdown panels will be empty.");
            breakdownPanel.removeAll();
            breakdownPanel.revalidate();
            breakdownPanel.repaint();
            return;
        }

        // Find all unique payment methods (cards)
        Set<String> paymentMethods = transactions.stream()
                .map(BudgetTransaction::getPaymentMethod)
                .filter(Objects::nonNull)
                .filter(s -> !s.trim().isEmpty())
                .collect(Collectors.toCollection(TreeSet::new));
        logger.info("Detected payment methods: {}", paymentMethods);

        // Dynamic breakdown panels
        breakdownPanel.removeAll();
        for (String card : paymentMethods) {
            logger.info("Building breakdown section for card '{}'", card);

            // Section label
            JLabel cardLabel = new JLabel(card + " - Category Breakdown");
            cardLabel.setFont(cardLabel.getFont().deriveFont(Font.BOLD, 14f));
            cardLabel.setBorder(new EmptyBorder(10, 0, 5, 0));
            breakdownPanel.add(cardLabel);

            // Panels for Josh and Anna side by side
            JPanel sideBySide = new JPanel();
            sideBySide.setLayout(new GridLayout(1, 2, 16, 0));
            sideBySide.setAlignmentX(Component.LEFT_ALIGNMENT);

            // Anna's and Josh's view is their own + half of joint
            List<BudgetTransaction> annaTx = buildSplitTransactions(transactions, "Anna", card);
            List<BudgetTransaction> joshTx = buildSplitTransactions(transactions, "Josh", card);

            logger.info("For card '{}': Josh tx count = {}, Anna tx count = {}", card, joshTx.size(), annaTx.size());

            CategorySummaryPanel joshPanel = new CategorySummaryPanel("Josh", null);
            joshPanel.setTransactions(joshTx);

            CategorySummaryPanel annaPanel = new CategorySummaryPanel("Anna", null);
            annaPanel.setTransactions(annaTx);

            sideBySide.add(wrapWithLabel(joshPanel, "Josh"));
            sideBySide.add(wrapWithLabel(annaPanel, "Anna"));
            breakdownPanel.add(sideBySide);
        }
        breakdownPanel.revalidate();
        breakdownPanel.repaint();
        logger.info("All breakdown panels built and displayed.");
    }

    /**
     * Builds the list of transactions for the category panel for a given person/card,
     * including all their own transactions at full value and all joint transactions at half value.
     *
     * @param allTx List of all transactions
     * @param person "Josh" or "Anna"
     * @param card Card/payment method name
     * @return List of BudgetTransaction objects (own + half of joint)
     */
    private List<BudgetTransaction> buildSplitTransactions(List<BudgetTransaction> allTx, String person, String card) {
        logger.debug("buildSplitTransactions for person='{}', card='{}'", person, card);
        List<BudgetTransaction> result = new java.util.ArrayList<>();

        // All transactions for this person and card (full value)
        allTx.stream()
                .filter(tx -> person.equalsIgnoreCase(tx.getAccount()))
                .filter(tx -> card.equalsIgnoreCase(tx.getPaymentMethod()))
                .forEach(result::add);

        // All joint transactions for this card (half value, clone and adjust amount)
        allTx.stream()
                .filter(tx -> "Joint".equalsIgnoreCase(tx.getAccount()))
                .filter(tx -> card.equalsIgnoreCase(tx.getPaymentMethod()))
                .forEach(jointTx -> {
                    try {
                        double half = jointTx.getAmountValue() / 2.0;
                        // Defensive: clone and set account to this person, optionally annotate category
                        BudgetTransaction splitTx = new BudgetTransaction(
                                jointTx.getName(),
                                String.format("%.2f", half),
                                jointTx.getCategory(), // optionally: + " (Joint Split)"
                                jointTx.getCriticality(),
                                jointTx.getTransactionDate(),
                                person, // Set to Anna or Josh for correct panel filtering
                                jointTx.getStatus(),
                                jointTx.getCreatedTime(),
                                jointTx.getPaymentMethod(),
                                jointTx.getStatementPeriod()
                        );
                        result.add(splitTx);
                    } catch (Exception e) {
                        logger.error("Failed to split joint transaction for card '{}': {}", card, e.getMessage(), e);
                    }
                });
        logger.debug("Split transactions built for person='{}', card='{}': {} tx", person, card, result.size());
        return result;
    }

    /**
     * Utility to wrap a panel with a titled border label for clarity.
     */
    private JPanel wrapWithLabel(JPanel inner, String label) {
        JPanel wrapper = new JPanel(new BorderLayout());
        JLabel title = new JLabel(label, SwingConstants.CENTER);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 12f));
        title.setBorder(new EmptyBorder(0, 0, 4, 0));
        wrapper.add(title, BorderLayout.NORTH);
        wrapper.add(inner, BorderLayout.CENTER);
        wrapper.setBorder(new EmptyBorder(0, 2, 0, 2));
        return wrapper;
    }
}