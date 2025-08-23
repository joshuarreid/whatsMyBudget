import java.io.*;
import java.util.*;

public class CSVParser {
    public List<Transaction> parseCSV(File csvFile) throws IOException {
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
        }
        return transactions;
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

    private double parseAmount(String amtStr) {
        try {
            return Double.parseDouble(amtStr.replace("$", "").replace(",", ""));
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
}