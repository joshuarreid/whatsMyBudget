import java.util.*;

public class BudgetCalculator {
    public Map<String, Map<String, Map<String, Double>>> calculateSubcategoryBreakdown(
            List<Transaction> transactions, List<ProjectedExpense> projectedExpenses) {
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

        // Add projected expenses
        if (projectedExpenses != null) {
            for (ProjectedExpense pe : projectedExpenses) {
                Map<String, Map<String, Double>> personMap = breakdown.get(pe.person);
                personMap.get(pe.criticality).put(
                        pe.subcategory,
                        personMap.get(pe.criticality).getOrDefault(pe.subcategory, 0.0) + pe.amount
                );
            }
        }

        return breakdown;
    }
}