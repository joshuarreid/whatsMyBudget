// src/BudgetGoalPanel.java
import javax.swing.*;
import java.awt.*;

public class BudgetGoalPanel extends JPanel {
    public BudgetGoalPanel(BudgetBreakdownApp app) {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(new Color(245, 248, 255));
        setBorder(BorderFactory.createEmptyBorder(40, 80, 40, 80));

        JLabel title = new JLabel("Budget Goal Calculator");
        title.setFont(new Font("Segoe UI", Font.BOLD, 28));
        title.setAlignmentX(Component.CENTER_ALIGNMENT);

        JTextField daysField = new JTextField(10);
        JComboBox<String> personBox = new JComboBox<>(new String[]{"Josh", "Anna"});
        JComboBox<String> categoryBox = new JComboBox<>(new String[]{
                "Groceries", "Dining", "Utilities", "Transport", "Shopping", "Other"
        });
        JTextField goalField = new JTextField(10);

        JButton calcBtn = new JButton("Calculate Weekly Budget");
        JLabel resultLabel = new JLabel(" ");

        calcBtn.addActionListener(e -> {
            try {
                int days = Integer.parseInt(daysField.getText().trim());
                double goal = Double.parseDouble(goalField.getText().trim());
                String person = (String) personBox.getSelectedItem();
                String category = (String) categoryBox.getSelectedItem();
                int weeks = Math.max(1, (int)Math.ceil(days / 7.0));
                double perWeek = goal / weeks;
                resultLabel.setText(String.format(
                        "<html>%s's weekly budget for <b>%s</b>: <span style='color:#6478dc;'>$%.2f</span> for %d weeks</html>",
                        person, category, perWeek, weeks
                ));
                app.addProjectedExpenseFromGoal(person, category, goal);
            } catch (Exception ex) {
                resultLabel.setText("Enter valid numbers for days and goal.");
            }
        });

        add(title);
        add(Box.createRigidArea(new Dimension(0, 30)));
        add(new JLabel("Days left in budget cycle:"));
        add(daysField);
        add(Box.createRigidArea(new Dimension(0, 10)));
        add(new JLabel("Person:"));
        add(personBox);
        add(Box.createRigidArea(new Dimension(0, 10)));
        add(new JLabel("Category:"));
        add(categoryBox);
        add(Box.createRigidArea(new Dimension(0, 10)));
        add(new JLabel("Desired spending goal ($):"));
        add(goalField);
        add(Box.createRigidArea(new Dimension(0, 20)));
        add(calcBtn);
        add(Box.createRigidArea(new Dimension(0, 20)));
        add(resultLabel);
    }
}