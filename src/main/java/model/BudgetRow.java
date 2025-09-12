package model;

import org.slf4j.Logger;
import util.AppLogger;

import java.util.Objects;

public class BudgetRow {
    private static final Logger logger = AppLogger.getLogger(BudgetRow.class);

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
        logger.debug("Created BudgetRow: {}", this);
    }

    public String getName() { return name; }
    public void setName(String name) {
        logger.debug("Setting name from '{}' to '{}'", this.name, name);
        this.name = name;
    }

    public String getAmount() { return amount; }
    public void setAmount(String amount) {
        logger.debug("Setting amount from '{}' to '{}'", this.amount, amount);
        this.amount = amount;
    }

    public String getCategory() { return category; }
    public void setCategory(String category) {
        logger.debug("Setting category from '{}' to '{}'", this.category, category);
        this.category = category;
    }

    public String getCriticality() { return criticality; }
    public void setCriticality(String criticality) {
        logger.debug("Setting criticality from '{}' to '{}'", this.criticality, criticality);
        this.criticality = criticality;
    }

    public String getTransactionDate() { return transactionDate; }
    public void setTransactionDate(String transactionDate) {
        logger.debug("Setting transactionDate from '{}' to '{}'", this.transactionDate, transactionDate);
        this.transactionDate = transactionDate;
    }

    public String getAccount() { return account; }
    public void setAccount(String account) {
        logger.debug("Setting account from '{}' to '{}'", this.account, account);
        this.account = account;
    }

    public String getStatus() { return status; }
    public void setStatus(String status) {
        logger.debug("Setting status from '{}' to '{}'", this.status, status);
        this.status = status;
    }

    public String getCreatedTime() { return createdTime; }
    public void setCreatedTime(String createdTime) {
        logger.debug("Setting createdTime from '{}' to '{}'", this.createdTime, createdTime);
        this.createdTime = createdTime;
    }

    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) {
        logger.debug("Setting paymentMethod from '{}' to '{}'", this.paymentMethod, paymentMethod);
        this.paymentMethod = paymentMethod;
    }

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