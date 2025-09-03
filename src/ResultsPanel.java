import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;

public class ResultsPanel extends JPanel {
    private final List<Transaction> lastTransactions;

    public ResultsPanel(
            Map<String, Map<String, Map<String, Double>>> breakdown,
            List<ProjectedExpense> projectedExpenses,
            List<Transaction> lastTransactions,
            Runnable onAddProjected,
            Runnable onRemoveProjected
    ) {
        this.lastTransactions = lastTransactions; // Needed for popup breakdown
        setLayout(new BorderLayout());
        setBackground(new Color(245, 248, 255));

        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topPanel.setBackground(new Color(245, 248, 255));
        JButton addProjectedButton = makeFancyButton("Add Projected Spending", new Color(220, 60, 60), new Color(255, 130, 130), new Color(100, 120, 220));
        addProjectedButton.setFont(new Font("Arial", Font.BOLD, 15));
        addProjectedButton.addActionListener(e -> onAddProjected.run());
        topPanel.add(addProjectedButton);

        JButton removeProjectedButton = makeFancyButton("Remove Projected Spending", new Color(100, 120, 220), new Color(140, 170, 255), new Color(100, 120, 220));
        removeProjectedButton.setFont(new Font("Arial", Font.BOLD, 15));
        removeProjectedButton.addActionListener(e -> onRemoveProjected.run());
        topPanel.add(removeProjectedButton);

        add(topPanel, BorderLayout.NORTH);

        JPanel joshBox = buildPersonPanel(breakdown, projectedExpenses, "Josh");
        JPanel annaBox = buildPersonPanel(breakdown, projectedExpenses, "Anna");

        JSplitPane resultPanel = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, joshBox, annaBox);
        resultPanel.setResizeWeight(0.5);
        resultPanel.setContinuousLayout(true);
        resultPanel.setOneTouchExpandable(true);

        add(resultPanel, BorderLayout.CENTER);
    }

    private JButton makeFancyButton(String text, Color baseColor, Color hoverColor, Color borderColor) {
        JButton btn = new JButton(text);
        btn.setBackground(baseColor);
        btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false);
        btn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(borderColor, 2, true),
                BorderFactory.createEmptyBorder(8, 20, 8, 20)
        ));
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.setOpaque(true);
        btn.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent e) { btn.setBackground(hoverColor); }
            public void mouseExited(java.awt.event.MouseEvent e) { btn.setBackground(baseColor); }
        });
        return btn;
    }

    private JPanel wrapTable(JTable table, String title) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(new Color(250, 253, 255));
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(220, 220, 230), 1, true),
                BorderFactory.createEmptyBorder(6, 6, 6, 6)
        ));
        JLabel label = new JLabel(title, JLabel.CENTER);
        label.setFont(new Font("Segoe UI", Font.BOLD, 15));
        label.setForeground(new Color(100, 120, 220));
        label.setBorder(BorderFactory.createEmptyBorder(7, 0, 7, 0));
        panel.add(label, BorderLayout.NORTH);

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        panel.add(scrollPane, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildPersonPanel(Map<String, Map<String, Map<String, Double>>> breakdown, List<ProjectedExpense> projectedExpenses, String person) {
        JPanel personBox = new JPanel(new BorderLayout());
        personBox.setBackground(new Color(250, 253, 255));
        personBox.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(200, 200, 220), 2, true),
                BorderFactory.createEmptyBorder(8, 8, 8, 8)
        ));
        JPanel titlePanel = new JPanel(new BorderLayout());
        titlePanel.setPreferredSize(new Dimension(1, 38));
        titlePanel.setBackground(new Color(250, 253, 255));
        JLabel personLabel = new JLabel(person + "'s Spending", JLabel.CENTER);
        personLabel.setFont(new Font("Segoe UI", Font.BOLD, 18));
        personLabel.setForeground(new Color(100, 120, 220));
        titlePanel.add(personLabel, BorderLayout.CENTER);
        personBox.add(titlePanel, BorderLayout.NORTH);

        JTable essentialTable = createStyledTable(buildVerticalCategoryModel(breakdown, projectedExpenses, person, "Essential"), person, "Essential");
        JTable nonEssentialTable = createStyledTable(buildVerticalCategoryModel(breakdown, projectedExpenses, person, "NonEssential"), person, "NonEssential");

        JPanel essPanel = wrapTable(essentialTable, "Essential");
        JPanel nonEssPanel = wrapTable(nonEssentialTable, "Nonessential");

        JPanel content = new JPanel(new GridLayout(2, 1, 0, 12));
        content.setBackground(new Color(250, 253, 255));
        content.add(essPanel);
        content.add(nonEssPanel);

        personBox.add(content, BorderLayout.CENTER);

        return personBox;
    }

    // --- Key logic: show actuals and projected as separate rows ---
    private DefaultTableModel buildVerticalCategoryModel(
            Map<String, Map<String, Map<String, Double>>> breakdown,
            List<ProjectedExpense> projectedExpenses,
            String person,
            String criticality
    ) {
        DefaultTableModel model = new DefaultTableModel();
        model.addColumn("Subcategory");
        model.addColumn("Amount");
        model.addColumn("Projected"); // hidden: true/false

        Map<String, Double> actuals = breakdown.get(person).get(criticality);

        // Collect all categories present in actuals OR projected
        Set<String> allCategories = new TreeSet<>();
        allCategories.addAll(actuals.keySet());
        for (ProjectedExpense pe : projectedExpenses) {
            if (pe.person.equals(person) && pe.criticality.equals(criticality)) {
                allCategories.add(pe.subcategory);
            }
        }

        double actualTotal = 0.0;
        double projectedTotal = 0.0;

        for (String cat : allCategories) {
            if (actuals.containsKey(cat)) {
                double amt = actuals.get(cat);
                model.addRow(new Object[]{cat, String.format("$%.2f", amt), false});
                actualTotal += amt;
            }
            double projectedAmount = 0.0;
            for (ProjectedExpense pe : projectedExpenses) {
                if (pe.person.equals(person) && pe.criticality.equals(criticality) && pe.subcategory.equals(cat)) {
                    projectedAmount += pe.amount;
                }
            }
            if (projectedAmount > 0) {
                model.addRow(new Object[]{cat, String.format("$%.2f", projectedAmount), true});
                projectedTotal += projectedAmount;
            }
        }

        if (!allCategories.isEmpty()) {
            model.addRow(new Object[]{"", "", false});
            model.addRow(new Object[]{"Total", String.format("$%.2f", actualTotal + projectedTotal), false});
        }

        return model;
    }

    private JTable createStyledTable(DefaultTableModel model, String person, String criticality) {
        JTable table = new JTable(model) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };
        table.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        table.setRowHeight(28);
        table.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 16));
        table.getTableHeader().setBackground(new Color(220, 230, 246));
        table.getTableHeader().setBorder(BorderFactory.createLineBorder(new Color(140, 160, 230), 1, true));
        table.setGridColor(new Color(220, 220, 220));

        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(JLabel.CENTER);

        for (int i = 1; i < table.getColumnCount() - 1; i++) {
            table.getColumnModel().getColumn(i).setCellRenderer(centerRenderer);
        }

        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable tbl, Object value, boolean isSelected,
                                                           boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(tbl, value, isSelected, hasFocus, row, column);
                boolean isProjected = Boolean.TRUE.equals(tbl.getValueAt(row, 2));
                if (isProjected) {
                    c.setBackground(new Color(255, 210, 210));
                } else {
                    c.setBackground(row % 2 == 0 ? new Color(255, 255, 255) : new Color(235, 240, 255));
                }
                setHorizontalAlignment(column == 0 ? JLabel.LEFT : JLabel.CENTER);
                if (isSelected) c.setBackground(new Color(180, 205, 255));
                return c;
            }
        });

        table.getColumnModel().getColumn(2).setMinWidth(0);
        table.getColumnModel().getColumn(2).setMaxWidth(0);
        table.getColumnModel().getColumn(2).setWidth(0);

        // --- Mouse listener: show weekly breakdown popup ---
        table.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                int row = table.rowAtPoint(e.getPoint());
                if (row < 0) return;
                String subcategory = model.getValueAt(row, 0).toString();
                // Only click on non-empty, non-total rows
                if (subcategory == null || subcategory.isEmpty() || subcategory.equals("Total")) return;
                showWeeklyBreakdown(person, subcategory);
            }
        });

        return table;
    }

    private void showWeeklyBreakdown(String person, String category) {
        // Filter transactions for this person and category, current month
        System.out.println("[DEBUG] showWeeklyBreakdown called for person: " + person + ", category: " + category);
        List<Transaction> filtered = new ArrayList<>();
        Calendar now = Calendar.getInstance();
        int year = now.get(Calendar.YEAR);
        int month = now.get(Calendar.MONTH); // 0-based

        for (Transaction tx : lastTransactions) {
            String txPerson = tx.account.trim().equalsIgnoreCase("joint") ? person : tx.account.trim();
            System.out.println("[DEBUG] Checking transaction: " + tx.category + " | " + txPerson + " | " + tx.transactionDate);
            if (!txPerson.equalsIgnoreCase(person)) {
                System.out.println("[DEBUG] Skipped: person mismatch (" + txPerson + ")");
                continue;
            }
            //if (!tx.category.equalsIgnoreCase(category)) continue;
            if (!tx.category.trim().equalsIgnoreCase(category.trim())) {
                System.out.println("[DEBUG] Skipped: category mismatch (" + tx.category + ")");
                continue;
            }

            System.out.println("[DEBUG] Included transaction: " + tx.category + " | " + txPerson + " | " + tx.transactionDate);
            filtered.add(tx);
        }

        // --- Custom week grouping: Week 1 (1–7), Week 2 (8–14), Week 3 (15–21), Week 4 (22–end) ---
        String[] weekLabels = {"Week 1 (1–7)", "Week 2 (8–14)", "Week 3 (15–21)", "Week 4 (22–29)", "Week 5 (29-end)"};
        Map<String, Double> weekTotals = new LinkedHashMap<>();
        for (String label : weekLabels) weekTotals.put(label, 0.0);

        for (Transaction tx : filtered) {
            Calendar txDate = getTxDate(tx.transactionDate); // <-- uses Transaction Date now
            int day = txDate.get(Calendar.DAY_OF_MONTH);
            String weekLabel;
            if (day <= 7) weekLabel = weekLabels[0];
            else if (day <= 14) weekLabel = weekLabels[1];
            else if (day <= 21) weekLabel = weekLabels[2];
            else if (day <= 29) weekLabel = weekLabels[3];
            else weekLabel = weekLabels[4];
            double amt = tx.account.trim().equalsIgnoreCase("joint") ? tx.amount / 2.0 : tx.amount;
            weekTotals.put(weekLabel, weekTotals.get(weekLabel) + amt);
        }

        // Build table for dialog
        String[] cols = {"Week", "Spent"};
        Object[][] data = new Object[weekTotals.size()][2];
        int i = 0;
        for (Map.Entry<String, Double> entry : weekTotals.entrySet()) {
            data[i][0] = entry.getKey();
            data[i][1] = String.format("$%.2f", entry.getValue());
            i++;
        }
        JTable weekTable = new JTable(data, cols);
        weekTable.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        weekTable.setRowHeight(28);

        JScrollPane scroll = new JScrollPane(weekTable);
        JDialog dialog = new JDialog((Frame) SwingUtilities.getWindowAncestor(this),
                person + " - " + category + " Weekly Breakdown", true);
        dialog.add(scroll);
        dialog.setSize(340, 220);
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    // Helper to parse date string ("August 3, 2025")
    private Calendar getTxDate(String dateStr) {
        Calendar cal = Calendar.getInstance();
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("MMMM d, yyyy", Locale.ENGLISH); // <-- format for Transaction Date
            cal.setTime(sdf.parse(dateStr));
        } catch (Exception e) {
            cal.setTime(new Date());
        }
        return cal;
    }

    // Utility to get all categories present for the person and criticality
    public static Set<String> getAllCategoriesForPersonCriticality(
            Map<String, Map<String, Map<String, Double>>> breakdown,
            List<ProjectedExpense> projectedExpenses,
            String person,
            String criticality
    ) {
        Set<String> allCategories = new TreeSet<>();
        Map<String, Double> actuals = breakdown.get(person).get(criticality);
        if (actuals != null) allCategories.addAll(actuals.keySet());
        for (ProjectedExpense pe : projectedExpenses) {
            if (pe.person.equals(person) && pe.criticality.equals(criticality)) {
                allCategories.add(pe.subcategory);
            }
        }
        return allCategories;
    }
}