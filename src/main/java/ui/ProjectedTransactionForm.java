package ui;

import model.ProjectedTransaction;
import org.slf4j.Logger;
import util.AppLogger;

import javax.swing.*;
import java.awt.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Modal form dialog for creating or editing a ProjectedTransaction.
 */
public class ProjectedTransactionForm extends JDialog {
    private static final Logger logger = AppLogger.getLogger(ProjectedTransactionForm.class);

    private JTextField nameField, amountField, categoryField, createdField;
    private JComboBox<String> criticalityCombo, accountCombo;
    private final String statementPeriod;
    private ProjectedTransaction result = null;

    // Possible values for Criticality and Account
    private static final String[] CRITICALITIES = {"Essential", "NonEssential"};
    private static final String[] ACCOUNTS = {"Josh", "Anna", "Joint"};

    /**
     * Constructs the modal form for adding or editing a ProjectedTransaction.
     * @param parent The parent window.
     * @param tx The ProjectedTransaction to edit, or null to add.
     * @param statementPeriod The statement period for the projected transaction.
     */
    public ProjectedTransactionForm(Window parent, ProjectedTransaction tx, String statementPeriod) {
        super(parent, tx == null ? "Add Projected Expense" : "Edit Projected Expense", ModalityType.APPLICATION_MODAL);
        logger.info("Initializing ProjectedTransactionForm for statementPeriod '{}', mode: {}", statementPeriod, tx == null ? "add" : "edit");
        this.statementPeriod = statementPeriod;
        setSize(450, 270);
        setLocationRelativeTo(parent);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        buildForm(tx);
    }

    /**
     * Builds the form fields and layout.
     * @param tx The ProjectedTransaction to edit, or null to add.
     */
    private void buildForm(ProjectedTransaction tx) {
        logger.info("Building ProjectedTransactionForm fields.");
        setLayout(new BorderLayout());
        JPanel fields = new JPanel(new GridLayout(0, 2, 4, 4));

        fields.add(new JLabel("Name:"));
        nameField = new JTextField(tx == null ? "" : tx.getName());
        fields.add(nameField);

        fields.add(new JLabel("Amount:"));
        amountField = new JTextField(tx == null ? "" : tx.getAmount());
        fields.add(amountField);

        fields.add(new JLabel("Category:"));
        categoryField = new JTextField(tx == null ? "" : tx.getCategory());
        fields.add(categoryField);

        fields.add(new JLabel("Criticality:"));
        criticalityCombo = new JComboBox<>(CRITICALITIES);
        if (tx != null && tx.getCriticality() != null) {
            criticalityCombo.setSelectedItem(tx.getCriticality());
        }
        fields.add(criticalityCombo);

        fields.add(new JLabel("Account:"));
        accountCombo = new JComboBox<>(ACCOUNTS);
        if (tx != null && tx.getAccount() != null) {
            accountCombo.setSelectedItem(tx.getAccount());
        }
        fields.add(accountCombo);

        fields.add(new JLabel("Created Time:"));
        String createdTime = (tx == null || tx.getCreatedTime() == null || tx.getCreatedTime().isBlank())
                ? LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                : tx.getCreatedTime();
        createdField = new JTextField(createdTime);
        createdField.setEditable(false);
        fields.add(createdField);

        add(fields, BorderLayout.CENTER);

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton okBtn = new JButton("OK");
        JButton cancelBtn = new JButton("Cancel");

        okBtn.addActionListener(e -> {
            logger.info("OK clicked in ProjectedTransactionForm.");
            String name = nameField.getText().trim();
            String amountRaw = amountField.getText().trim();

            // Validate required fields
            if (name.isEmpty()) {
                logger.warn("Validation failed: Name is required.");
                JOptionPane.showMessageDialog(this, "Name is required.", "Validation Error", JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (amountRaw.isEmpty()) {
                logger.warn("Validation failed: Amount is required.");
                JOptionPane.showMessageDialog(this, "Amount is required.", "Validation Error", JOptionPane.WARNING_MESSAGE);
                return;
            }
            // Normalize and validate amount (always $ and two decimals)
            String normalizedAmount = normalizeAmount(amountRaw);
            if (normalizedAmount == null) {
                logger.warn("Validation failed: Amount '{}' is not a valid number.", amountRaw);
                JOptionPane.showMessageDialog(this, "Amount must be a valid number (e.g., 12.34).", "Validation Error", JOptionPane.WARNING_MESSAGE);
                return;
            }
            // Optionally update field so user sees normalized value
            amountField.setText(normalizedAmount);

            try {
                result = new ProjectedTransaction(
                        name,
                        normalizedAmount,
                        categoryField.getText(),
                        (String) criticalityCombo.getSelectedItem(),
                        "", // Transaction Date removed
                        (String) accountCombo.getSelectedItem(),
                        "", // Status removed
                        createdField.getText(),
                        statementPeriod
                );
                logger.info("ProjectedTransaction created: {}", result);
            } catch (Exception ex) {
                logger.error("Failed to create ProjectedTransaction: {}", ex.getMessage(), ex);
                JOptionPane.showMessageDialog(this, "Invalid input: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                result = null;
                return;
            }
            dispose();
        });
        cancelBtn.addActionListener(e -> {
            logger.info("Cancel clicked in ProjectedTransactionForm.");
            result = null;
            dispose();
        });

        btns.add(okBtn);
        btns.add(cancelBtn);
        add(btns, BorderLayout.SOUTH);
    }

    /**
     * Ensures the amount string is always "$" + two decimals.
     * Accepts raw numbers like "225", "225.5", "$225", "$225.50", "225.56".
     * Returns normalized string, or null if not numeric.
     */
    private String normalizeAmount(String userInput) {
        logger.info("normalizeAmount called with userInput='{}'", userInput);
        if (userInput == null || userInput.trim().isEmpty()) {
            logger.warn("normalizeAmount received null or empty input");
            return null;
        }
        String cleaned = userInput.replace("$", "").replace(",", "").trim();
        try {
            double value = Double.parseDouble(cleaned);
            String formatted = String.format("$%.2f", value);
            logger.info("normalizeAmount returning '{}'", formatted);
            return formatted;
        } catch (NumberFormatException e) {
            logger.error("normalizeAmount parse error for '{}': {}", userInput, e.getMessage());
            return null;
        }
    }

    /**
     * Returns the result ProjectedTransaction, or null if canceled or on error.
     * @return ProjectedTransaction or null.
     */
    public ProjectedTransaction getResult() {
        return result;
    }
}