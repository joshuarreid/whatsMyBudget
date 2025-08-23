import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.List;
import java.util.Map;

public class BreakdownPanel extends JPanel {
    private List<Transaction> lastTransactions;
    private List<ProjectedExpense> projectedExpenses;
    private BudgetCalculator calculator;
    private BudgetBreakdownApp appRef;

    public BreakdownPanel(BudgetBreakdownApp appRef, Color bgColor, Color btnColor, Color btnHoverColor, Color accentColor,
                          List<ProjectedExpense> projectedExpenses, BudgetCalculator calculator) {
        this.appRef = appRef;
        this.projectedExpenses = projectedExpenses;
        this.calculator = calculator;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(bgColor);

        JLabel welcomeLabel = new JLabel("<html><center><h1 style='color:#6478dc;'>Budget Breakdown</h1>" +
                "<p style='font-size:18px;'>Upload your CSV to see an interactive analysis of spending.</p></center></html>", JLabel.CENTER);
        welcomeLabel.setFont(new Font("Segoe UI", Font.BOLD, 24));
        welcomeLabel.setBorder(BorderFactory.createEmptyBorder(50, 0, 18, 0));

        JButton uploadButton = new JButton("Upload Budget CSV");
        uploadButton.setFont(new Font("Segoe UI", Font.BOLD, 18));
        uploadButton.setPreferredSize(new Dimension(230, 45));
        uploadButton.setMaximumSize(new Dimension(230, 45));
        uploadButton.setBackground(btnColor);
        uploadButton.setForeground(Color.WHITE);

        uploadButton.addActionListener(e -> uploadCSV());

        add(welcomeLabel);
        add(Box.createRigidArea(new Dimension(0, 36)));
        add(uploadButton);
    }

    private void uploadCSV() {
        JFileChooser fileChooser = new JFileChooser();
        int option = fileChooser.showOpenDialog(this);

        if (option == JFileChooser.APPROVE_OPTION) {
            File csvFile = fileChooser.getSelectedFile();
            CSVParser parser = new CSVParser();
            try {
                List<Transaction> transactions = parser.parseCSV(csvFile);
                lastTransactions = transactions;
                Map<String, Map<String, Map<String, Double>>> breakdown =
                        calculator.calculateSubcategoryBreakdown(transactions, projectedExpenses);
                appRef.showResults(breakdown);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Error parsing CSV: " + ex.getMessage());
            }
        }
    }
}