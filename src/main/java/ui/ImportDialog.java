package ui;

import model.BudgetTransaction;
import org.slf4j.Logger;
import util.AppLogger;

import javax.swing.*;
import java.awt.*;
import java.time.Month;
import java.util.List;
import java.util.function.Consumer;

/**
 * Dialog for previewing, assigning statement periods, and confirming imported transactions.
 * Allows "Apply to All" and per-row editing of statement period before confirming import.
 * Always validates before enabling import.
 */
public class ImportDialog extends JDialog {
    private static final Logger logger = AppLogger.getLogger(ImportDialog.class);

    private final PeriodSelectorPanel periodSelectorPanel;
    private final ImportTablePanel importTablePanel;
    private final JButton importButton;
    private final JButton cancelButton;
    private final JLabel errorLabel;

    /**
     * Constructs the import dialog.
     *
     * @param parent        The parent frame.
     * @param transactions  List of transactions parsed from the CSV.
     * @param onImport      Callback accepting the finalized list when user confirms import.
     */
    public ImportDialog(Frame parent, List<BudgetTransaction> transactions, Consumer<List<BudgetTransaction>> onImport) {
        super(parent, "Review and Import Transactions", true);
        logger.info("Initializing ImportDialog with {} transactions", transactions != null ? transactions.size() : 0);

        setLayout(new BorderLayout());
        setPreferredSize(new Dimension(1100, 600));

        periodSelectorPanel = new PeriodSelectorPanel(this::applyPeriodToAll);
        add(periodSelectorPanel, BorderLayout.NORTH);

        importTablePanel = new ImportTablePanel(transactions, this::validateAndMaybeEnableImport);
        add(importTablePanel, BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel(new BorderLayout());
        errorLabel = new JLabel();
        errorLabel.setForeground(Color.RED);
        bottomPanel.add(errorLabel, BorderLayout.WEST);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        importButton = new JButton("Import");
        importButton.setEnabled(false);
        cancelButton = new JButton("Cancel");
        buttonPanel.add(importButton);
        buttonPanel.add(cancelButton);
        bottomPanel.add(buttonPanel, BorderLayout.EAST);

        add(bottomPanel, BorderLayout.SOUTH);

        importButton.addActionListener(e -> {
            logger.info("User clicked Import in ImportDialog.");
            List<BudgetTransaction> txs = importTablePanel.getTransactions();
            if (!validateAllPeriods(txs)) {
                errorLabel.setText("Please fix all statement periods before importing.");
                logger.warn("Attempted import with invalid statement periods.");
                return;
            }
            logger.info("ImportDialog: Validation passed. Sending {} transactions to callback.", txs.size());
            onImport.accept(txs);
            dispose();
        });

        cancelButton.addActionListener(e -> {
            logger.info("User cancelled ImportDialog.");
            dispose();
        });

        pack();
        setLocationRelativeTo(parent);
        validateAndMaybeEnableImport();
    }

    /**
     * Applies the selected period to all transactions in the table.
     */
    private void applyPeriodToAll(Month month, Integer year) {
        logger.info("Applying period to all transactions: {} {}", month, year);
        importTablePanel.applyPeriodToAll(month, year);
        validateAndMaybeEnableImport();
    }

    /**
     * Validates all statement periods and enables/disables import button accordingly.
     */
    private void validateAndMaybeEnableImport() {
        List<BudgetTransaction> txs = importTablePanel.getTransactions();
        boolean valid = validateAllPeriods(txs);
        importButton.setEnabled(valid && !txs.isEmpty());
        errorLabel.setText(valid ? "" : "Please fix all statement periods before importing.");
        logger.info("Validating statement periods: valid={}, transactionCount={}", valid, txs != null ? txs.size() : 0);
    }

    /**
     * Returns true if all statement periods in the list are valid and non-empty.
     */
    private boolean validateAllPeriods(List<BudgetTransaction> txs) {
        if (txs == null) return false;
        for (BudgetTransaction tx : txs) {
            String period = tx.getStatementPeriod();
            if (period == null || period.isBlank() || !util.StatementPeriodUtil.isValidStatementPeriod(period)) {
                logger.debug("Invalid statement period detected: '{}', transaction: {}", period, tx);
                return false;
            }
        }
        return true;
    }
}