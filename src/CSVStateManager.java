import javax.swing.*;
import java.io.*;
import java.util.List;

public class CSVStateManager {
    public static void saveTransactionsAsCsv(List<Transaction> transactions, JFrame parentFrame) {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Save Transactions as CSV");
        int userSelection = fileChooser.showSaveDialog(parentFrame);

        if (userSelection == JFileChooser.APPROVE_OPTION) {
            File fileToSave = fileChooser.getSelectedFile();
            if (!fileToSave.getName().toLowerCase().endsWith(".csv")) {
                fileToSave = new File(fileToSave.getAbsolutePath() + ".csv");
            }
            if (fileToSave.exists()) {
                int overwrite = JOptionPane.showConfirmDialog(parentFrame,
                        "File exists. Overwrite?", "Confirm Overwrite", JOptionPane.YES_NO_OPTION);
                if (overwrite != JOptionPane.YES_OPTION) return;
            }
            try (PrintWriter writer = new PrintWriter(new FileWriter(fileToSave))) {
                writer.println("Name,Amount,Category,Account,Criticality,TransactionDate,CreatedTime,Status");
                for (Transaction tx : transactions) {
                    // DEBUG LOG: Print transaction fields to console
                    System.out.printf(
                            "Exporting Tx: name='%s', amount=%.2f, category='%s', account='%s', criticality='%s', transactionDate='%s', createdTime='%s', status='%s'%n",
                            tx.name,
                            tx.amount,
                            tx.category,
                            tx.account,
                            tx.criticality,
                            tx.transactionDate,
                            tx.createdTime,
                            (tx.status != null ? tx.status : "")
                    );

                    writer.printf("%s,%.2f,%s,%s,%s,%s,%s,%s%n",
                            escapeCsv(tx.name),
                            tx.amount,
                            escapeCsv(tx.category),
                            escapeCsv(tx.account),
                            escapeCsv(tx.criticality),
                            escapeCsv(tx.transactionDate),
                            escapeCsv(tx.createdTime),
                            escapeCsv(tx.status != null ? tx.status : ""));
                }
                JOptionPane.showMessageDialog(parentFrame, "Transactions saved to " + fileToSave.getAbsolutePath());
            } catch (IOException e) {
                JOptionPane.showMessageDialog(parentFrame, "Error saving CSV: " + e.getMessage(),
                        "Save Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private static String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            value = value.replace("\"", "\"\"");
            value = "\"" + value + "\"";
        }
        return value;
    }
}