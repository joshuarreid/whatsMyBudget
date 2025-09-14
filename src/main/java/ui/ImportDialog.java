package ui;

import model.BudgetTransaction;
import org.slf4j.Logger;
import util.AppLogger;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.function.Consumer;

/**
 * Dialog for previewing and confirming imported transactions.
 * No statement period assignment or validation is performed.
 * User simply reviews, confirms import, or cancels.
 */
public class ImportDialog extends JDialog {
    private static final Logger logger = AppLogger.getLogger(ImportDialog.class);

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

        importTablePanel = new ImportTablePanel(transactions, this::updateImportButtonState);
        add(importTablePanel, BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel(new BorderLayout());
        errorLabel = new JLabel();
        errorLabel.setForeground(Color.RED);
        bottomPanel.add(errorLabel, BorderLayout.WEST);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        importButton = new JButton("Import");
        importButton.setEnabled(!transactions.isEmpty());
        cancelButton = new JButton("Cancel");
        buttonPanel.add(importButton);
        buttonPanel.add(cancelButton);
        bottomPanel.add(buttonPanel, BorderLayout.EAST);

        add(bottomPanel, BorderLayout.SOUTH);

        importButton.addActionListener(e -> {
            logger.info("User clicked Import in ImportDialog.");
            List<BudgetTransaction> txs = importTablePanel.getTransactions();
            if (txs == null || txs.isEmpty()) {
                errorLabel.setText("No transactions to import.");
                logger.warn("Attempted import with empty transaction list.");
                return;
            }
            logger.info("ImportDialog: Sending {} transactions to callback.", txs.size());
            onImport.accept(txs);
            dispose();
        });

        cancelButton.addActionListener(e -> {
            logger.info("User cancelled ImportDialog.");
            dispose();
        });

        pack();
        setLocationRelativeTo(parent);
        updateImportButtonState();
    }

    /**
     * Enables or disables the import button based on transaction presence.
     */
    private void updateImportButtonState() {
        List<BudgetTransaction> txs = importTablePanel.getTransactions();
        boolean enabled = txs != null && !txs.isEmpty();
        importButton.setEnabled(enabled);
        errorLabel.setText(enabled ? "" : "No transactions to import.");
        logger.info("Import button state updated: enabled={}, transactionCount={}", enabled, txs != null ? txs.size() : 0);
    }
}