package ui;

import model.ProjectedTransaction;
import org.slf4j.Logger;
import util.AppLogger;

import javax.swing.*;
import java.awt.*;

/**
 * Modal form dialog for creating or editing a ProjectedTransaction.
 */
public class ProjectedTransactionForm extends JDialog {
    private static final Logger logger = AppLogger.getLogger(ProjectedTransactionForm.class);

    private JTextField nameField, amountField, categoryField, criticalityField, dateField, accountField, statusField, createdField;
    private final String statementPeriod;
    private ProjectedTransaction result = null;

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
        setSize(450, 330);
        setLocationRelativeTo(parent);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        buildForm(tx);
    }

    /**
     * Builds the form fields and layout.
     * @param tx The ProjectedTransaction to edit, or null to add.
     */
    private void buildForm(ProjectedTransaction tx) {
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
        criticalityField = new JTextField(tx == null ? "" : tx.getCriticality());
        fields.add(criticalityField);

        fields.add(new JLabel("Transaction Date:"));
        dateField = new JTextField(tx == null ? "" : tx.getTransactionDate());
        fields.add(dateField);

        fields.add(new JLabel("Account:"));
        accountField = new JTextField(tx == null ? "" : tx.getAccount());
        fields.add(accountField);

        fields.add(new JLabel("Status:"));
        statusField = new JTextField(tx == null ? "" : tx.getStatus());
        fields.add(statusField);

        fields.add(new JLabel("Created Time:"));
        createdField = new JTextField(tx == null ? "" : tx.getCreatedTime());
        fields.add(createdField);

        add(fields, BorderLayout.CENTER);

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton okBtn = new JButton("OK");
        JButton cancelBtn = new JButton("Cancel");

        okBtn.addActionListener(e -> {
            logger.info("OK clicked in ProjectedTransactionForm.");
            try {
                result = new ProjectedTransaction(
                        nameField.getText(),
                        amountField.getText(),
                        categoryField.getText(),
                        criticalityField.getText(),
                        dateField.getText(),
                        accountField.getText(),
                        statusField.getText(),
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
     * Returns the result ProjectedTransaction, or null if canceled or on error.
     * @return ProjectedTransaction or null.
     */
    public ProjectedTransaction getResult() {
        return result;
    }
}