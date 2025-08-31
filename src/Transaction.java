public class Transaction {
    String name;
    double amount;
    String category;
    String account;
    String criticality;
    String transactionDate;
    String createdTime;
    String status; // "active", "imported", etc.

    public Transaction(String name, double amount, String category, String account,
                       String criticality, String transactionDate, String createdTime) {
        this(name, amount, category, account, criticality, transactionDate, createdTime, "imported");
    }

    public Transaction(String name, double amount, String category, String account,
                       String criticality, String transactionDate, String createdTime, String status) {
        this.name = name;
        this.amount = amount;
        this.category = category;
        this.account = account;
        this.criticality = criticality.trim().replace(" ", "");
        this.transactionDate = transactionDate;
        this.createdTime = createdTime;
        this.status = status;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getAccount() {
        return account;
    }

    public void setAccount(String account) {
        this.account = account;
    }

    public String getCriticality() {
        return criticality;
    }

    public void setCriticality(String criticality) {
        this.criticality = criticality;
    }

    public String getTransactionDate() {
        return transactionDate;
    }

    public void setTransactionDate(String transactionDate) {
        this.transactionDate = transactionDate;
    }

    public String getCreatedTime() {
        return createdTime;
    }

    public void setCreatedTime(String createdTime) {
        this.createdTime = createdTime;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}