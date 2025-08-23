public class ProjectedExpense {
    String person;
    String criticality;
    String subcategory;
    double amount;
    boolean isJoint;

    public ProjectedExpense(String person, String criticality, String subcategory, double amount, boolean isJoint) {
        this.person = person;
        this.criticality = criticality;
        this.subcategory = subcategory;
        this.amount = amount;
        this.isJoint = isJoint;
    }
}