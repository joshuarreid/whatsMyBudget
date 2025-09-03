import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.*;
import java.util.List;

public class BudgetBreakdownApp extends JFrame {
    private CardLayout cardLayout = new CardLayout();
    private JPanel mainPanel = new JPanel(cardLayout);

    // State
    private List<Transaction> lastTransactions = new ArrayList<>();
    private List<ProjectedExpense> projectedExpenses = new ArrayList<>();

    public BudgetBreakdownApp() {
        setTitle("Budget Breakdown");
        setSize(1200, 850);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        Color bgColor = new Color(245, 248, 255);
        Color accentColor = new Color(100, 120, 220);
        Color btnColor = new Color(90, 180, 220);
        Color btnHoverColor = new Color(130, 210, 255);

        JMenuBar menuBar = new JMenuBar();
        menuBar.setBackground(Color.WHITE);
        menuBar.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
        JMenu navMenu = new JMenu("Navigate");
        navMenu.setFont(new Font("Segoe UI", Font.BOLD, 16));
        JMenuItem breakdownItem = new JMenuItem("Spending Breakdown");
        breakdownItem.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        JMenuItem plannerItem = new JMenuItem("Budget Planner");
        plannerItem.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        navMenu.add(breakdownItem);
        navMenu.add(plannerItem);
        menuBar.add(navMenu);
        setJMenuBar(menuBar);
        JMenu fileMenu = new JMenu("File");
        JMenuItem saveCsvItem = new JMenuItem("Save as CSV");
        JMenuItem importNewTxItem = new JMenuItem("Import New Transactions");
        importNewTxItem.addActionListener(e -> {
            JFileChooser fileChooser = new JFileChooser();
            int option = fileChooser.showOpenDialog(this);
            if (option == JFileChooser.APPROVE_OPTION) {
                File csvFile = fileChooser.getSelectedFile();
                importNewTransactions(csvFile);
                Map<String, Map<String, Map<String, Double>>> breakdown = calculateBreakdown(lastTransactions);
                showResults(breakdown);
            }
        });
        fileMenu.add(importNewTxItem);
        saveCsvItem.addActionListener(e -> saveAsCsv());
        fileMenu.add(saveCsvItem);

        menuBar.add(fileMenu);

        JPanel uploadPanel = new JPanel();
        uploadPanel.setLayout(new BoxLayout(uploadPanel, BoxLayout.Y_AXIS));
        uploadPanel.setBackground(bgColor);

        JLabel welcomeLabel = new JLabel(
                "<html><center><h1 style='color:#6478dc;'>Budget Breakdown</h1>" +
                        "<p style='font-size:18px;'>Upload your CSV to see an interactive analysis of spending.</p></center></html>", JLabel.CENTER);
        welcomeLabel.setFont(new Font("Segoe UI", Font.BOLD, 24));
        welcomeLabel.setBorder(BorderFactory.createEmptyBorder(50, 0, 18, 0));

        JButton uploadButton = makeFancyButton("Upload Budget CSV", btnColor, btnHoverColor, accentColor);
        uploadButton.setFont(new Font("Segoe UI", Font.BOLD, 18));
        uploadButton.setPreferredSize(new Dimension(230, 45));
        uploadButton.setMaximumSize(new Dimension(230, 45));
        uploadButton.addActionListener(e -> uploadCSV());

        uploadPanel.add(welcomeLabel);
        uploadPanel.add(Box.createRigidArea(new Dimension(0, 36)));
        uploadPanel.add(uploadButton);

        mainPanel.add(uploadPanel, "breakdown");

        JPanel plannerPanel = new JPanel(new BorderLayout());
        String[] columns = {"Category", "Amount", "Notes"};
        Object[][] data = {};
        DefaultTableModel model = new DefaultTableModel(data, columns);
        JTable table = new JTable(model);

        JButton addRowBtn = new JButton("Add Planned Expense");
        addRowBtn.addActionListener(e -> model.addRow(new Object[]{"", "", ""}));

        plannerPanel.add(new JScrollPane(table), BorderLayout.CENTER);
        plannerPanel.add(addRowBtn, BorderLayout.SOUTH);
        mainPanel.add(plannerPanel, "planner");

        add(mainPanel);

        breakdownItem.addActionListener(e -> {
            if (!lastTransactions.isEmpty()) {
                Map<String, Map<String, Map<String, Double>>> breakdown = calculateBreakdown(lastTransactions);
                showResults(breakdown);
            } else {
                cardLayout.show(mainPanel, "breakdown");
            }
        });
        plannerItem.addActionListener(e -> cardLayout.show(mainPanel, "planner"));

        cardLayout.show(mainPanel, "breakdown");
    }

    public void showResults(Map<String, Map<String, Map<String, Double>>> breakdown) {
        ResultsPanel resultsPanel = new ResultsPanel(
                breakdown,
                projectedExpenses,
                lastTransactions,
                this::addProjectedExpense,
                this::removeProjectedExpense
        );
        mainPanel.add(resultsPanel, "results");
        cardLayout.show(mainPanel, "results");
        revalidate();
        repaint();
    }

    private void addProjectedExpense() {
        JTabbedPane tabbedPane = new JTabbedPane();

        // Manual Entry Tab
        final JPanel manualPanel = new JPanel(new GridLayout(0, 2, 5, 5));
        manualPanel.setBackground(new Color(245, 248, 255));
        final JTextField subcategoryField = new JTextField(10);
        final JComboBox<String> personBox = new JComboBox<>(new String[]{"Josh", "Anna", "Joint"});
        final JComboBox<String> criticalityBox = new JComboBox<>(new String[]{"Essential", "NonEssential"});
        final JTextField amountField = new JTextField(10);

        manualPanel.add(new JLabel("Person:"));
        manualPanel.add(personBox);
        manualPanel.add(new JLabel("Criticality:"));
        manualPanel.add(criticalityBox);
        manualPanel.add(new JLabel("Subcategory:"));
        manualPanel.add(subcategoryField);
        manualPanel.add(new JLabel("Amount:"));
        manualPanel.add(amountField);
        // Java
        final JButton manualAddBtn = new JButton("Add As Projected Expense");
        final JLabel manualResultLabel = new JLabel(" ");
        manualPanel.add(manualAddBtn);
        manualPanel.add(manualResultLabel);

        manualAddBtn.addActionListener(e -> {
            try {
                String person = (String) personBox.getSelectedItem();
                String criticality = (String) criticalityBox.getSelectedItem();
                String subcategory = subcategoryField.getText().trim();
                double amount = Double.parseDouble(amountField.getText().trim());
                if (subcategory.isEmpty()) {
                    manualResultLabel.setText("Please enter a subcategory.");
                    return;
                }
                if ("Joint".equals(person)) {
                    projectedExpenses.add(new ProjectedExpense("Josh", criticality, subcategory, amount / 2.0, true));
                    projectedExpenses.add(new ProjectedExpense("Anna", criticality, subcategory, amount / 2.0, true));
                } else {
                    projectedExpenses.add(new ProjectedExpense(person, criticality, subcategory, amount, false));
                }
                Map<String, Map<String, Map<String, Double>>> breakdown = calculateBreakdown(lastTransactions);
                showResults(breakdown);
                manualResultLabel.setText("Projected expense added.");
            } catch (Exception ex) {
                manualResultLabel.setText("Enter valid values for all fields.");
            }
        });

        tabbedPane.addTab("Manual Entry", manualPanel);

        // Budget Goal Calculator Tab
        final JPanel calcPanel = new JPanel();
        calcPanel.setLayout(new GridBagLayout());
        calcPanel.setBackground(new Color(245, 248, 255));
        final GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        final JTextField daysField = new JTextField(10);
        final JComboBox<String> calcPersonBox = new JComboBox<>(new String[]{"Josh", "Anna", "Joint"});
        final JComboBox<String> calcCriticalityBox = new JComboBox<>(new String[]{"Essential", "NonEssential"});

        // Dynamically populate categories from existing breakdown/projected expenses
        Set<String> allCats = new TreeSet<>();
        allCats.addAll(ResultsPanel.getAllCategoriesForPersonCriticality(
                calculateBreakdown(lastTransactions), projectedExpenses, "Josh", "Essential"));
        allCats.addAll(ResultsPanel.getAllCategoriesForPersonCriticality(
                calculateBreakdown(lastTransactions), projectedExpenses, "Josh", "NonEssential"));
        allCats.addAll(ResultsPanel.getAllCategoriesForPersonCriticality(
                calculateBreakdown(lastTransactions), projectedExpenses, "Anna", "Essential"));
        allCats.addAll(ResultsPanel.getAllCategoriesForPersonCriticality(
                calculateBreakdown(lastTransactions), projectedExpenses, "Anna", "NonEssential"));
        List<String> catList = new ArrayList<>(allCats);
        catList.removeIf(s -> s == null || s.isEmpty());
        catList.add("Other");

        final JComboBox<String> categoryBox = new JComboBox<>(catList.toArray(new String[0]));
        final JTextField otherCategoryField = new JTextField(10);
        otherCategoryField.setVisible(false);

        final JTextField goalField = new JTextField(10);

        gbc.gridx = 0; gbc.gridy = 0;
        calcPanel.add(new JLabel("Days left in budget cycle:"), gbc);
        gbc.gridx = 1; gbc.gridy = 0;
        calcPanel.add(daysField, gbc);

        gbc.gridx = 0; gbc.gridy = 1;
        calcPanel.add(new JLabel("Person:"), gbc);
        gbc.gridx = 1; gbc.gridy = 1;
        calcPanel.add(calcPersonBox, gbc);

        gbc.gridx = 0; gbc.gridy = 2;
        calcPanel.add(new JLabel("Criticality:"), gbc);
        gbc.gridx = 1; gbc.gridy = 2;
        calcPanel.add(calcCriticalityBox, gbc);

        gbc.gridx = 0; gbc.gridy = 3;
        calcPanel.add(new JLabel("Category:"), gbc);
        gbc.gridx = 1; gbc.gridy = 3;
        calcPanel.add(categoryBox, gbc);

        gbc.gridx = 1; gbc.gridy = 4;
        calcPanel.add(otherCategoryField, gbc);

        gbc.gridx = 0; gbc.gridy = 5;
        calcPanel.add(new JLabel("Desired spending goal ($):"), gbc);
        gbc.gridx = 1; gbc.gridy = 5;
        calcPanel.add(goalField, gbc);

        final JButton calcBtn = new JButton("Add As Projected Expense");
        final JLabel resultLabel = new JLabel(" ");
        final JLabel logLabel = new JLabel(" ");

        gbc.gridx = 0; gbc.gridy = 6; gbc.gridwidth = 2;
        calcPanel.add(calcBtn, gbc);

        gbc.gridx = 0; gbc.gridy = 7; gbc.gridwidth = 2;
        calcPanel.add(resultLabel, gbc);

        gbc.gridx = 0; gbc.gridy = 8; gbc.gridwidth = 2;
        calcPanel.add(logLabel, gbc);

        categoryBox.addActionListener(e -> {
            String selected = (String) categoryBox.getSelectedItem();
            otherCategoryField.setVisible("Other".equals(selected));
            calcPanel.revalidate();
            calcPanel.repaint();
        });

        calcBtn.addActionListener(e -> {
            try {
                int days = Integer.parseInt(daysField.getText().trim());
                double goal = Double.parseDouble(goalField.getText().trim());
                String person = (String) calcPersonBox.getSelectedItem();
                String criticality = (String) calcCriticalityBox.getSelectedItem();
                String category = (String) categoryBox.getSelectedItem();
                if ("Other".equals(category)) {
                    category = otherCategoryField.getText().trim();
                    if (category.isEmpty()) {
                        resultLabel.setText("Please enter a category name for 'Other'.");
                        logLabel.setText("");
                        return;
                    }
                }
                int weeks = Math.max(1, (int)Math.ceil(days / 7.0));

                if ("Joint".equals(person)) {
                    double halfGoal = goal / 2.0;
                    StringBuilder logText = new StringBuilder();
                    for (String individual : new String[]{"Josh", "Anna"}) {
                        double actualSpent = 0.0;
                        for (Transaction tx : lastTransactions) {
                            boolean isPerson =
                                    tx.account.trim().equalsIgnoreCase(individual) ||
                                            tx.account.trim().equalsIgnoreCase("joint");
                            if (isPerson &&
                                    tx.category.trim().equalsIgnoreCase(category) &&
                                    tx.criticality.trim().equalsIgnoreCase(criticality)) {
                                if (tx.account.trim().equalsIgnoreCase("joint")) {
                                    actualSpent += tx.amount / 2.0;
                                } else {
                                    actualSpent += tx.amount;
                                }
                            }
                        }
                        double alreadyProjected = 0.0;
                        for (ProjectedExpense pe : projectedExpenses) {
                            if (pe.person.equals(individual) &&
                                    pe.criticality.equals(criticality) &&
                                    pe.subcategory.equals(category)) {
                                alreadyProjected += pe.amount;
                            }
                        }
                        String finalCategory = category;
                        projectedExpenses.removeIf(pe ->
                                pe.person.equals(individual) &&
                                        pe.criticality.equals(criticality) &&
                                        pe.subcategory.equals(finalCategory)
                        );
                        double projectedAmount = Math.max(0.0, halfGoal - actualSpent - alreadyProjected);
                        double perWeek = weeks > 0 ? projectedAmount / weeks : projectedAmount;
                        if (projectedAmount > 0) {
                            projectedExpenses.add(new ProjectedExpense(individual, criticality, category, projectedAmount, false));
                        }
                        logText.append(String.format(
                                "<span style='color:#dc6464;'>$%.2f</span> projected for <b>%s</b> (%s), <span style='color:#6478dc;'>$%.2f</span> per week<br>",
                                projectedAmount, category, individual, perWeek
                        ));
                    }
                    Map<String, Map<String, Map<String, Double>>> breakdown = calculateBreakdown(lastTransactions);
                    showResults(breakdown);
                    logLabel.setText("<html>" + logText.toString() + "</html>");
                    resultLabel.setText(String.format(
                            "<html>Joint goal split:<br>Josh and Anna weekly budget for <b>%s</b> (%s): <span style='color:#6478dc;'>$%.2f each</span> for %d weeks</html>",
                            category, criticality, halfGoal / weeks, weeks
                    ));
                    return;
                }

                // Individual logic (existing)
                double actualSpent = 0.0;
                for (Transaction tx : lastTransactions) {
                    boolean isPerson =
                            tx.account.trim().equalsIgnoreCase(person) ||
                                    (person.equals("Josh") && tx.account.trim().equalsIgnoreCase("joint")) ||
                                    (person.equals("Anna") && tx.account.trim().equalsIgnoreCase("joint"));

                    if (isPerson &&
                            tx.category.trim().equalsIgnoreCase(category) &&
                            tx.criticality.trim().equalsIgnoreCase(criticality)) {
                        if (tx.account.trim().equalsIgnoreCase("joint")) {
                            actualSpent += tx.amount / 2.0;
                        } else {
                            actualSpent += tx.amount;
                        }
                    }
                }
                double alreadyProjected = 0.0;
                for (ProjectedExpense pe : projectedExpenses) {
                    if (pe.person.equals(person) &&
                            pe.criticality.equals(criticality) &&
                            pe.subcategory.equals(category)) {
                        alreadyProjected += pe.amount;
                    }
                }
                String finalCategory1 = category;
                projectedExpenses.removeIf(pe ->
                        pe.person.equals(person) &&
                                pe.criticality.equals(criticality) &&
                                pe.subcategory.equals(finalCategory1)
                );
                double projectedAmount = Math.max(0.0, goal - actualSpent - alreadyProjected);
                double perWeek = weeks > 0 ? projectedAmount / weeks : projectedAmount;

                resultLabel.setText(String.format(
                        "<html>%s's weekly budget for <b>%s</b> (%s): <span style='color:#6478dc;'>$%.2f</span> for %d weeks</html>",
                        person, category, criticality, perWeek, weeks
                ));

                if (projectedAmount > 0) {
                    projectedExpenses.add(new ProjectedExpense(person, criticality, category, projectedAmount, false));
                    Map<String, Map<String, Map<String, Double>>> breakdown = calculateBreakdown(lastTransactions);
                    showResults(breakdown);
                    logLabel.setText(String.format(
                            "<html><span style='color:#dc6464;'>$%.2f</span> total projected for <b>%s</b> (%s), <span style='color:#6478dc;'>$%.2f</span> per week</html>",
                            projectedAmount, category, person, perWeek
                    ));
                } else {
                    logLabel.setText(
                            "<html><span style='color:#228B22;'>No projected expense needed: actuals and projected meet or exceed goal.</span></html>"
                    );
                }
            } catch (Exception ex) {
                resultLabel.setText("Enter valid numbers for days and goal.");
                logLabel.setText("");
            }
        });

        tabbedPane.addTab("Budget Goal Calculator", calcPanel);

        int result = JOptionPane.showConfirmDialog(this, tabbedPane, "Add Projected Spending", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result == JOptionPane.OK_OPTION && tabbedPane.getSelectedIndex() == 0) {
            try {
                String person = (String) personBox.getSelectedItem();
                String criticality = (String) criticalityBox.getSelectedItem();
                String subcategory = subcategoryField.getText().trim();
                double amount = Double.parseDouble(amountField.getText().trim());
                if ("Joint".equals(person)) {
                    projectedExpenses.add(new ProjectedExpense("Josh", criticality, subcategory, amount / 2.0, true));
                    projectedExpenses.add(new ProjectedExpense("Anna", criticality, subcategory, amount / 2.0, true));
                } else {
                    projectedExpenses.add(new ProjectedExpense(person, criticality, subcategory, amount, false));
                }
                Map<String, Map<String, Map<String, Double>>> breakdown = calculateBreakdown(lastTransactions);
                showResults(breakdown);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Enter valid values for all fields.");
            }
        }
    }

    private void removeProjectedExpense() {
        if (projectedExpenses.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No projected spending entries to remove.");
            return;
        }
        DefaultListModel<String> model = new DefaultListModel<>();
        for (ProjectedExpense pe : projectedExpenses) {
            model.addElement(String.format("%s - %s - %s: $%.2f%s",
                    pe.person, pe.criticality, pe.subcategory, pe.amount, pe.isJoint ? " (Joint)" : ""));
        }
        JList<String> projList = new JList<>(model);
        projList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        projList.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        JScrollPane scrollPane = new JScrollPane(projList);

        int result = JOptionPane.showConfirmDialog(this, scrollPane, "Select Projected Spending(s) to Remove", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result == JOptionPane.OK_OPTION) {
            int[] indices = projList.getSelectedIndices();
            Arrays.sort(indices);
            for (int i = indices.length - 1; i >= 0; i--) {
                projectedExpenses.remove(indices[i]);
            }
            Map<String, Map<String, Map<String, Double>>> breakdown = calculateBreakdown(lastTransactions);
            showResults(breakdown);
        }
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

    private void uploadCSV() {
        JFileChooser fileChooser = new JFileChooser();
        int option = fileChooser.showOpenDialog(this);

        if (option == JFileChooser.APPROVE_OPTION) {
            File csvFile = fileChooser.getSelectedFile();
            importCSV(csvFile); // <-- NEW METHOD
            Map<String, Map<String, Map<String, Double>>> breakdown = calculateBreakdown(lastTransactions);
            showResults(breakdown);
        }
    }

    public void addProjectedExpenseFromGoal(String person, String category, double amount) {
        projectedExpenses.add(new ProjectedExpense(person, "NonEssential", category, amount, false));
    }

    private List<Transaction> parseCSV(File csvFile) {
        List<Transaction> transactions = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(csvFile))) {
            String line = br.readLine(); // header
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty() || line.startsWith(",")) continue;
                String[] parts = splitCSVLine(line);
                if (parts.length < 7) continue;
                String name = parts[0].trim();
                double amount = parseAmount(parts[1].trim());
                String category = parts[2].trim();
                String account = parts[3].trim();
                String criticality = parts[4].trim();
                String transactionDate = parts[5].trim();
                String createdTime = parts[6].trim();
                transactions.add(new Transaction(
                        name, amount, category, account, criticality, transactionDate, createdTime
                ));
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Error parsing CSV: " + ex.getMessage());
        }
        return transactions;
    }

    private void importCSV(File csvFile) {
        List<Transaction> importedTransactions = new ArrayList<>();
        List<ProjectedExpense> importedProjected = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(csvFile))) {
            String line = br.readLine(); // header
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty() || line.startsWith(",")) continue;
                String[] parts = splitCSVLine(line);
                if (parts.length < 8) continue; // 8 fields expected
                String name = parts[0].trim();
                double amount = parseAmount(parts[1].trim());
                String category = parts[2].trim();
                String criticality = parts[3].trim();
                String transactionDate = parts[4].trim();
                String account = parts[5].trim();
                String createdTime = parts[6].trim();
                String status = parts[7].trim().toLowerCase();

                if (status.equals("active")) {
                    // Add to projectedExpenses
                    importedProjected.add(new ProjectedExpense(
                            account, // person
                            criticality,
                            category,
                            amount,
                            account.equalsIgnoreCase("joint")
                    ));
                } else {
                    // Add to actuals
                    importedTransactions.add(new Transaction(
                            name,
                            amount,
                            category,
                            account,
                            criticality,
                            transactionDate,
                            createdTime,
                            status
                    ));
                }
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Error parsing CSV: " + ex.getMessage());
        }
        lastTransactions = importedTransactions;
        projectedExpenses = importedProjected;
    }

    private String[] splitCSVLine(String line) {
        List<String> result = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;
        for (char c : line.toCharArray()) {
            if (c == '"') inQuotes = !inQuotes;
            else if (c == ',' && !inQuotes) {
                result.add(sb.toString());
                sb.setLength(0);
            } else sb.append(c);
        }
        result.add(sb.toString());
        return result.toArray(new String[0]);
    }

    private void saveAsCsv() {
        List<Transaction> allTx = new ArrayList<>(lastTransactions);
        // Convert projectedExpenses to Transaction with status "active"
        for (ProjectedExpense pe : projectedExpenses) {
            allTx.add(new Transaction(
                    pe.subcategory, // name
                    pe.amount,
                    pe.subcategory,
                    pe.person,
                    pe.criticality,
                    "", // transactionDate
                    "", // createdTime
                    "active"
            ));
        }
        CSVStateManager.saveTransactionsAsCsv(allTx, this);
    }

    private double parseAmount(String amtStr) {
        try {
            return Double.parseDouble(amtStr.replace("$", "").replace(",", ""));
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    // Only sum actual transactions for actuals; do NOT add projectedExpenses to breakdown here!
    private Map<String, Map<String, Map<String, Double>>> calculateBreakdown(List<Transaction> transactions) {
        Map<String, Map<String, Map<String, Double>>> breakdown = new HashMap<>();
        breakdown.put("Josh", new HashMap<>());
        breakdown.put("Anna", new HashMap<>());
        for (String person : breakdown.keySet()) {
            breakdown.get(person).put("Essential", new HashMap<>());
            breakdown.get(person).put("NonEssential", new HashMap<>());
        }

        for (Transaction tx : transactions) {
            String accountLower = tx.account.trim().toLowerCase();
            if (accountLower.equals("josh")) {
                Map<String, Map<String, Double>> personMap = breakdown.get("Josh");
                personMap.get(tx.criticality).put(
                        tx.category,
                        personMap.get(tx.criticality).getOrDefault(tx.category, 0.0) + tx.amount
                );
            } else if (accountLower.equals("anna")) {
                Map<String, Map<String, Double>> personMap = breakdown.get("Anna");
                personMap.get(tx.criticality).put(
                        tx.category,
                        personMap.get(tx.criticality).getOrDefault(tx.category, 0.0) + tx.amount
                );
            } else if (accountLower.equals("joint")) {
                for (String person : Arrays.asList("Josh", "Anna")) {
                    Map<String, Map<String, Double>> personMap = breakdown.get(person);
                    personMap.get(tx.criticality).put(
                            tx.category,
                            personMap.get(tx.criticality).getOrDefault(tx.category, 0.0) + tx.amount / 2.0
                    );
                }
            }
        }

        // Do NOT add projectedExpenses to breakdown here!

        return breakdown;
    }

    private void importNewTransactions(File csvFile) {
        List<Transaction> newTransactions = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(csvFile))) {
            String line = br.readLine(); // header
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty() || line.startsWith(",")) continue;
                String[] parts = splitCSVLine(line);
                if (parts.length < 8) continue;
                String name = parts[0].trim();
                double amount = parseAmount(parts[1].trim());
                String category = parts[2].trim();
                String account = parts[3].trim();
                String criticality = parts[4].trim();
                String transactionDate = parts[5].trim();
                String createdTime = parts[6].trim();
                String status = parts[7].trim().toLowerCase();

                if (!status.equals("active")) {
                    Transaction tx = new Transaction(
                            name, amount, category, account, criticality, transactionDate, createdTime, status
                    );
                    if (!isDuplicateTransaction(tx, lastTransactions)) {
                        newTransactions.add(tx);
                    }
                }
                // If you want to also check for new projections, add similar logic for projectedExpenses.
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Error parsing CSV: " + ex.getMessage());
        }
        // Add only new transactions to your existing list
        lastTransactions.addAll(newTransactions);
        JOptionPane.showMessageDialog(this, newTransactions.size() + " transactions imported");
    }

    private boolean isDuplicateTransaction(Transaction tx, List<Transaction> existing) {
        for (Transaction t : existing) {
            if (
                    t.name.equals(tx.name) &&
                            t.amount == tx.amount &&
                            t.category.equals(tx.category) &&
                            t.account.equals(tx.account) &&
                            t.transactionDate.equals(tx.transactionDate)
            ) {
                return true;
            }
        }
        return false;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new BudgetBreakdownApp().setVisible(true));
    }
}