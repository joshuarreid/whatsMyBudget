package model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProjectedRow {
    private static final Logger logger = LoggerFactory.getLogger(ProjectedRow.class);

    private String name;
    private String amount;
    private String category;
    private String criticality;
    private String projectedDate; // Anticipated future payment date
    private String account;
    private String status;
    private String createdTime;
    private String paymentMethod;
    private String notes; // Optional notes for this projected expense

    public ProjectedRow() {
        logger.debug("ProjectedRow instantiated with default constructor");
    }

    public ProjectedRow(String name, String amount, String category, String criticality,
                        String projectedDate, String account, String status,
                        String createdTime, String paymentMethod, String notes) {
        this.name = name;
        this.amount = amount;
        this.category = category;
        this.criticality = criticality;
        this.projectedDate = projectedDate;
        this.account = account;
        this.status = status;
        this.createdTime = createdTime;
        this.paymentMethod = paymentMethod;
        this.notes = notes;
        logger.info("ProjectedRow created: {}", this);
    }

    public String getName() { return name; }
    public void setName(String name) {
        logger.debug("Setting name: {}", name);
        this.name = name;
    }

    public String getAmount() { return amount; }
    public void setAmount(String amount) {
        logger.debug("Setting amount: {}", amount);
        this.amount = amount;
    }

    public String getCategory() { return category; }
    public void setCategory(String category) {
        logger.debug("Setting category: {}", category);
        this.category = category;
    }

    public String getCriticality() { return criticality; }
    public void setCriticality(String criticality) {
        logger.debug("Setting criticality: {}", criticality);
        this.criticality = criticality;
    }

    public String getProjectedDate() { return projectedDate; }
    public void setProjectedDate(String projectedDate) {
        logger.debug("Setting projectedDate: {}", projectedDate);
        this.projectedDate = projectedDate;
    }

    public String getAccount() { return account; }
    public void setAccount(String account) {
        logger.debug("Setting account: {}", account);
        this.account = account;
    }

    public String getStatus() { return status; }
    public void setStatus(String status) {
        logger.debug("Setting status: {}", status);
        this.status = status;
    }

    public String getCreatedTime() { return createdTime; }
    public void setCreatedTime(String createdTime) {
        logger.debug("Setting createdTime: {}", createdTime);
        this.createdTime = createdTime;
    }

    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) {
        logger.debug("Setting paymentMethod: {}", paymentMethod);
        this.paymentMethod = paymentMethod;
    }

    public String getNotes() { return notes; }
    public void setNotes(String notes) {
        logger.debug("Setting notes: {}", notes);
        this.notes = notes;
    }

    @Override
    public String toString() {
        return "ProjectedRow{" +
                "name='" + name + '\'' +
                ", amount='" + amount + '\'' +
                ", category='" + category + '\'' +
                ", criticality='" + criticality + '\'' +
                ", projectedDate='" + projectedDate + '\'' +
                ", account='" + account + '\'' +
                ", status='" + status + '\'' +
                ", createdTime='" + createdTime + '\'' +
                ", paymentMethod='" + paymentMethod + '\'' +
                ", notes='" + notes + '\'' +
                '}';
    }
}