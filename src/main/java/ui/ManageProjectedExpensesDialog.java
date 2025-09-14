package ui;

import model.ProjectedTransaction;
import org.slf4j.Logger;
import util.AppLogger;
import service.CSVStateService;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.Locale;

/**
 * Dialog for managing projected expenses by statement period.
 * Allows viewing, adding, editing, and deleting projected transactions.
 */
public class ManageProjectedExpensesDialog extends JDialog {
    private static final Logger logger = AppLogger.getLogger(ManageProjectedExpensesDialog.class);

    private final CSVStateService csvStateService;
    private final Runnable refreshCallback;

    private JComboBox<String> periodCombo;
    private GenericTablePanel tablePanel;
    private List<ProjectedTransaction> currentTransactions;

    // Month constants for statement period selection
    private static final String[] MONTHS = {
            "JANUARY", "FEBRUARY", "MARCH", "APRIL", "MAY", "JUNE",
            "JULY", "AUGUST", "SEPTEMBER", "OCTOBER", "NOVEMBER", "DECEMBER"
    };

    /**
     * Constructs the dialog for managing projected expenses.
     * @param parent Parent window
     * @param csvStateService Reference to the service for projected expense operations
     * @param refreshCallback Callback to refresh UI after changes
     */
    public ManageProjectedExpensesDialog(Window parent, CSVStateService csvStateService, Runnable refreshCallback) {
        super(parent, "Manage Projected Expenses", ModalityType.APPLICATION_MODAL);
        logger.info("Initializing ManageProjectedExpensesDialog.");
        this.csvStateService = csvStateService;
        this.refreshCallback = refreshCallback;
        setSize(850, 500);
        setLocationRelativeTo(parent);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        buildUI();
        logger.info("ManageProjectedExpensesDialog initialized.");
    }

    private void buildUI() {
        logger.info("Building ManageProjectedExpensesDialog UI.");
        setLayout(new BorderLayout());

        // Top panel: period selector
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topPanel.add(new JLabel("Statement Period:"));
        periodCombo = new JComboBox<>();
        reloadPeriods();
        periodCombo.addActionListener(e -> loadPeriod());
        topPanel.add(periodCombo);

        JButton addPeriodBtn = new JButton("Add Period");
        addPeriodBtn.addActionListener(e -> addPeriod());
        topPanel.add(addPeriodBtn);

        add(topPanel, BorderLayout.NORTH);

        // Table panel
        String[] columns = new String[] {
                "Name", "Amount", "Category", "Criticality", "Transaction Date", "Account", "Status", "Created Time", "Payment Method"
        };
        tablePanel = new GenericTablePanel(columns, tx -> new Object[] {
                tx.getName(), tx.getAmount(), tx.getCategory(), tx.getCriticality(),
                tx.getTransactionDate(), tx.getAccount(), tx.getStatus(), tx.getCreatedTime(), tx.getPaymentMethod()
        });

        add(tablePanel, BorderLayout.CENTER);

        // Bottom buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton addBtn = new JButton("Add");
        JButton editBtn = new JButton("Edit");
        JButton deleteBtn = new JButton("Delete");
        JButton closeBtn = new JButton("Close");

        addBtn.addActionListener(e -> addProjected());
        editBtn.addActionListener(e -> editProjected());
        deleteBtn.addActionListener(e -> deleteProjected());
        closeBtn.addActionListener(e -> {
            logger.info("ManageProjectedExpensesDialog closed by user.");
            dispose();
        });

        buttonPanel.add(addBtn);
        buttonPanel.add(editBtn);
        buttonPanel.add(deleteBtn);
        buttonPanel.add(closeBtn);

        add(buttonPanel, BorderLayout.SOUTH);

        loadPeriod();
    }

    private void reloadPeriods() {
        logger.info("Reloading statement periods for projected expenses.");
        try {
            List<ProjectedTransaction> all = csvStateService.getAllProjectedTransactions();
            Set<String> periods = new TreeSet<>(all.stream()
                    .map(ProjectedTransaction::getStatementPeriod)
                    .filter(p -> p != null && !p.isBlank())
                    .collect(Collectors.toSet()));
            periodCombo.removeAllItems();
            for (String period : periods) {
                periodCombo.addItem(period);
            }
            logger.info("Found {} statement periods with projected expenses.", periods.size());
        } catch (Exception ex) {
            logger.error("Failed to reload periods: {}", ex.getMessage(), ex);
            JOptionPane.showMessageDialog(this, "Failed to load statement periods: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void loadPeriod() {
        String period = (String) periodCombo.getSelectedItem();
        logger.info("Loading projected expenses for statement period '{}'.", period);
        if (period == null) {
            tablePanel.setTransactions(List.of());
            currentTransactions = List.of();
            return;
        }
        try {
            List<ProjectedTransaction> periodTxs = csvStateService.getProjectedTransactionsForPeriod(period);
            tablePanel.setTransactions(periodTxs);
            currentTransactions = periodTxs;
            logger.info("Displayed {} projected transactions for period '{}'.", periodTxs.size(), period);
        } catch (Exception ex) {
            logger.error("Failed to load projected transactions for '{}': {}", period, ex.getMessage(), ex);
            JOptionPane.showMessageDialog(this, "Failed to load projected expenses: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            tablePanel.setTransactions(List.of());
            currentTransactions = List.of();
        }
    }

    private void addPeriod() {
        logger.info("User requested to add a new statement period.");
        JPanel panel = new JPanel(new GridLayout(2, 2, 6, 6));
        JComboBox<String> monthBox = new JComboBox<>(MONTHS);
        JTextField yearField = new JTextField();

        panel.add(new JLabel("Month:"));
        panel.add(monthBox);
        panel.add(new JLabel("Year:"));
        panel.add(yearField);

        int result = JOptionPane.showConfirmDialog(this, panel, "Add Statement Period", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result == JOptionPane.OK_OPTION) {
            String month = (String) monthBox.getSelectedItem();
            String year = yearField.getText().trim();
            if (month != null && !year.isEmpty() && year.matches("\\d{4}")) {
                String period = formatStatementPeriod(month, year);
                periodCombo.addItem(period);
                periodCombo.setSelectedItem(period);
                logger.info("Added new statement period '{}'.", period);
            } else {
                logger.warn("Invalid statement period input. Month: '{}', Year: '{}'", month, year);
                JOptionPane.showMessageDialog(this, "Please enter a valid year (e.g., 2025).", "Invalid Input", JOptionPane.WARNING_MESSAGE);
            }
        } else {
            logger.info("Add period canceled or dialog closed.");
        }
    }

    private static String formatStatementPeriod(String month, String year) {
        String formatted = month == null ? "" : month.trim().toUpperCase(Locale.ENGLISH);
        // Remove spaces and any non-alpha characters
        formatted = formatted.replaceAll("[^A-Z]", "");
        String period = formatted + year;
        logger.info("Formatted statement period: '{}'", period);
        return period;
    }

    private void addProjected() {
        logger.info("User requested to add projected expense.");
        String period = (String) periodCombo.getSelectedItem();
        if (period == null) {
            JOptionPane.showMessageDialog(this, "Please select a statement period before adding a projected expense.", "No Period Selected", JOptionPane.WARNING_MESSAGE);
            return;
        }
        ProjectedTransactionForm form = new ProjectedTransactionForm(this, null, period);
        form.setVisible(true);
        ProjectedTransaction tx = form.getResult();
        if (tx != null) {
            try {
                csvStateService.addProjectedTransaction(tx);
                logger.info("Added projected transaction: {}", tx);
                loadPeriod();
                refreshCallback.run();
            } catch (Exception ex) {
                logger.error("Failed to add projected transaction: {}", ex.getMessage(), ex);
                JOptionPane.showMessageDialog(this, "Failed to add projected expense: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        } else {
            logger.info("Add projected expense canceled or no data entered.");
        }
    }

    private void editProjected() {
        logger.info("User requested to edit projected expense.");
        int row = tablePanel.getTable().getSelectedRow();
        if (row < 0 || row >= currentTransactions.size()) {
            JOptionPane.showMessageDialog(this, "Please select a projected expense to edit.", "No Selection", JOptionPane.WARNING_MESSAGE);
            return;
        }
        ProjectedTransaction orig = currentTransactions.get(row);
        ProjectedTransactionForm form = new ProjectedTransactionForm(this, orig, orig.getStatementPeriod());
        form.setVisible(true);
        ProjectedTransaction tx = form.getResult();
        if (tx != null) {
            try {
                csvStateService.updateProjectedTransaction(orig, tx);
                logger.info("Edited projected transaction: {} -> {}", orig, tx);
                loadPeriod();
                refreshCallback.run();
            } catch (Exception ex) {
                logger.error("Failed to edit projected transaction: {}", ex.getMessage(), ex);
                JOptionPane.showMessageDialog(this, "Failed to edit projected expense: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        } else {
            logger.info("Edit projected expense canceled or no changes made.");
        }
    }

    private void deleteProjected() {
        logger.info("User requested to delete projected expense.");
        int row = tablePanel.getTable().getSelectedRow();
        if (row < 0 || row >= currentTransactions.size()) {
            JOptionPane.showMessageDialog(this, "Please select a projected expense to delete.", "No Selection", JOptionPane.WARNING_MESSAGE);
            return;
        }
        ProjectedTransaction tx = currentTransactions.get(row);
        int confirm = JOptionPane.showConfirmDialog(this, "Delete selected projected expense?", "Confirm Delete", JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION) {
            try {
                csvStateService.deleteProjectedTransaction(tx);
                logger.info("Deleted projected transaction: {}", tx);
                loadPeriod();
                refreshCallback.run();
            } catch (Exception ex) {
                logger.error("Failed to delete projected transaction: {}", ex.getMessage(), ex);
                JOptionPane.showMessageDialog(this, "Failed to delete projected expense: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        } else {
            logger.info("Delete projected expense canceled.");
        }
    }
}