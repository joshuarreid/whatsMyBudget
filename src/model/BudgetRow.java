package model;

import java.util.Objects;

public class BudgetRow {
    private String name;
    private String amount;
    private String category;
    private String criticality;
    private String transactionDate;
    private String account;
    private String status;
    private String createdTime;
    private String paymentMethod;

    public BudgetRow() {}

    public BudgetRow(String name, String amount, String category, String criticality,
                     String transactionDate, String account, String status,
                     String createdTime, String paymentMethod) {
        this.name = name;
        this.amount = amount;
        this.category = category;
        this.criticality = criticality;
        this.transactionDate = transactionDate;
        this.account = account;
        this.status = status;
        this.createdTime = createdTime;
        this.paymentMethod = paymentMethod;
    }

    // Getters and setters for all fields

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getAmount() { return amount; }
    public void setAmount(String amount) { this.amount = amount; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getCriticality() { return criticality; }
    public void setCriticality(String criticality) { this.criticality = criticality; }

    public String getTransactionDate() { return transactionDate; }
    public void setTransactionDate(String transactionDate) { this.transactionDate = transactionDate; }

    public String getAccount() { return account; }
    public void setAccount(String account) { this.account = account; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getCreatedTime() { return createdTime; }
    public void setCreatedTime(String createdTime) { this.createdTime = createdTime; }

    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BudgetRow)) return false;
        BudgetRow that = (BudgetRow) o;
        return Objects.equals(name, that.name) &&
                Objects.equals(amount, that.amount) &&
                Objects.equals(category, that.category) &&
                Objects.equals(criticality, that.criticality) &&
                Objects.equals(transactionDate, that.transactionDate) &&
                Objects.equals(account, that.account) &&
                Objects.equals(status, that.status) &&
                Objects.equals(createdTime, that.createdTime) &&
                Objects.equals(paymentMethod, that.paymentMethod);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, amount, category, criticality, transactionDate,
                account, status, createdTime, paymentMethod);
    }

    @Override
    public String toString() {
        return "BudgetRow{" +
                "name='" + name + '\'' +
                ", amount='" + amount + '\'' +
                ", category='" + category + '\'' +
                ", criticality='" + criticality + '\'' +
                ", transactionDate='" + transactionDate + '\'' +
                ", account='" + account + '\'' +
                ", status='" + status + '\'' +
                ", createdTime='" + createdTime + '\'' +
                ", paymentMethod='" + paymentMethod + '\'' +
                '}';
    }
}