public class Transaction {
    String name;
    double amount;
    String category;
    String account;
    String criticality;
    String transactionDate;
    String createdTime;

    public Transaction(String name, double amount, String category, String account,
                       String criticality, String transactionDate, String createdTime) {
        this.name = name;
        this.amount = amount;
        this.category = category;
        this.account = account;
        this.criticality = criticality.trim().replace(" ", "");
        this.transactionDate = transactionDate;
        this.createdTime = createdTime;
    }
}