import javax.swing.*;
import java.awt.*;

public class BudgetPlannerPanel extends JPanel {
    private JTextField daysLeftField;
    private JTextField essentialBudgetField, nonessentialBudgetField, groceryBudgetField, diningOutBudgetField;
    private JLabel perWeekEssentialLabel, perWeekNonessentialLabel, perWeekGroceryLabel, perWeekDiningOutLabel;

    public BudgetPlannerPanel(Color bgColor, Color btnColor, Color btnHoverColor, Color accentColor) {
        setLayout(new GridBagLayout());
        setBackground(bgColor);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(18, 18, 18, 18);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JLabel titleLabel = new JLabel("Monthly Budget Planner", JLabel.CENTER);
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 28));
        titleLabel.setForeground(accentColor);
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;
        add(titleLabel, gbc);

        gbc.gridwidth = 1;
        Font labelFont = new Font("Segoe UI", Font.PLAIN, 18);

        // Days left input
        JLabel daysLeftLabel = new JLabel("Days left in month:");
        daysLeftLabel.setFont(labelFont);
        gbc.gridx = 0; gbc.gridy = 1;
        add(daysLeftLabel, gbc);

        daysLeftField = new JTextField("30", 8);
        daysLeftField.setFont(labelFont);
        daysLeftField.setBorder(BorderFactory.createLineBorder(accentColor, 1, true));
        gbc.gridx = 1; gbc.gridy = 1;
        add(daysLeftField, gbc);

        // Essential budget
        JLabel essentialBudgetLabel = new JLabel("Essential monthly budget:");
        essentialBudgetLabel.setFont(labelFont);
        gbc.gridx = 0; gbc.gridy = 2;
        add(essentialBudgetLabel, gbc);

        essentialBudgetField = new JTextField("2000", 8);
        essentialBudgetField.setFont(labelFont);
        essentialBudgetField.setBorder(BorderFactory.createLineBorder(accentColor, 1, true));
        gbc.gridx = 1; gbc.gridy = 2;
        add(essentialBudgetField, gbc);

        // Nonessential budget
        JLabel nonessentialBudgetLabel = new JLabel("Nonessential monthly budget:");
        nonessentialBudgetLabel.setFont(labelFont);
        gbc.gridx = 0; gbc.gridy = 3;
        add(nonessentialBudgetLabel, gbc);

        nonessentialBudgetField = new JTextField("500", 8);
        nonessentialBudgetField.setFont(labelFont);
        nonessentialBudgetField.setBorder(BorderFactory.createLineBorder(accentColor, 1, true));
        gbc.gridx = 1; gbc.gridy = 3;
        add(nonessentialBudgetField, gbc);

        // Grocery budget
        JLabel groceryBudgetLabel = new JLabel("Grocery monthly budget:");
        groceryBudgetLabel.setFont(labelFont);
        gbc.gridx = 0; gbc.gridy = 4;
        add(groceryBudgetLabel, gbc);

        groceryBudgetField = new JTextField("400", 8);
        groceryBudgetField.setFont(labelFont);
        groceryBudgetField.setBorder(BorderFactory.createLineBorder(accentColor, 1, true));
        gbc.gridx = 1; gbc.gridy = 4;
        add(groceryBudgetField, gbc);

        // Dining out budget
        JLabel diningOutBudgetLabel = new JLabel("Dining Out monthly budget:");
        diningOutBudgetLabel.setFont(labelFont);
        gbc.gridx = 0; gbc.gridy = 5;
        add(diningOutBudgetLabel, gbc);

        diningOutBudgetField = new JTextField("150", 8);
        diningOutBudgetField.setFont(labelFont);
        diningOutBudgetField.setBorder(BorderFactory.createLineBorder(accentColor, 1, true));
        gbc.gridx = 1; gbc.gridy = 5;
        add(diningOutBudgetField, gbc);

        // Button to calculate
        JButton calcButton = new JButton("Calculate Per Week Budget");
        calcButton.setFont(new Font("Arial", Font.BOLD, 18));
        calcButton.setBackground(btnColor);
        calcButton.setForeground(Color.WHITE);
        gbc.gridx = 0; gbc.gridy = 6; gbc.gridwidth = 2;
        add(calcButton, gbc);

        // Results
        Font resultFont = new Font("Segoe UI", Font.BOLD, 18);
        perWeekEssentialLabel = new JLabel("Essential: ", JLabel.LEFT);
        perWeekEssentialLabel.setFont(resultFont);
        perWeekEssentialLabel.setForeground(new Color(60, 120, 220));
        gbc.gridx = 0; gbc.gridy = 7; gbc.gridwidth = 2;
        add(perWeekEssentialLabel, gbc);

        perWeekNonessentialLabel = new JLabel("Nonessential: ", JLabel.LEFT);
        perWeekNonessentialLabel.setFont(resultFont);
        perWeekNonessentialLabel.setForeground(new Color(60, 120, 220));
        gbc.gridx = 0; gbc.gridy = 8; gbc.gridwidth = 2;
        add(perWeekNonessentialLabel, gbc);

        perWeekGroceryLabel = new JLabel("Grocery: ", JLabel.LEFT);
        perWeekGroceryLabel.setFont(resultFont);
        perWeekGroceryLabel.setForeground(new Color(60, 120, 220));
        gbc.gridx = 0; gbc.gridy = 9; gbc.gridwidth = 2;
        add(perWeekGroceryLabel, gbc);

        perWeekDiningOutLabel = new JLabel("Dining Out: ", JLabel.LEFT);
        perWeekDiningOutLabel.setFont(resultFont);
        perWeekDiningOutLabel.setForeground(new Color(60, 120, 220));
        gbc.gridx = 0; gbc.gridy = 10; gbc.gridwidth = 2;
        add(perWeekDiningOutLabel, gbc);

        calcButton.addActionListener(e -> updateBudgetLabels());
    }

    private void updateBudgetLabels() {
        try {
            int daysLeft = Integer.parseInt(daysLeftField.getText().trim());
            int weeksLeft = Math.max(1, (int)Math.ceil(daysLeft / 7.0));
            double essential = Double.parseDouble(essentialBudgetField.getText().trim());
            double nonessential = Double.parseDouble(nonessentialBudgetField.getText().trim());
            double grocery = Double.parseDouble(groceryBudgetField.getText().trim());
            double dining = Double.parseDouble(diningOutBudgetField.getText().trim());

            perWeekEssentialLabel.setText(String.format("Essential: $%.2f per week (%,d weeks)", essential / weeksLeft, weeksLeft));
            perWeekNonessentialLabel.setText(String.format("Nonessential: $%.2f per week (%,d weeks)", nonessential / weeksLeft, weeksLeft));
            perWeekGroceryLabel.setText(String.format("Grocery: $%.2f per week (%,d weeks)", grocery / weeksLeft, weeksLeft));
            perWeekDiningOutLabel.setText(String.format("Dining Out: $%.2f per week (%,d weeks)", dining / weeksLeft, weeksLeft));
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Please enter valid numbers for all fields.");
        }
    }
}