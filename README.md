# WhatsMyBudget

**WhatsMyBudget** is an interactive Java application for personal budget tracking, category breakdowns, and weekly spending analysis. The application helps users monitor spending by category, compare actual and projected expenses, and view detailed weekly breakdowns for each spending category.

## Features

- **Category Breakdown**: Visualize spending per category and subcategory.
- **Projected vs Actual Spending**: Add and remove projected expenses for future planning.
- **Weekly Analysis**: Click any category to see a detailed weekly breakdown for the current month.
- **Joint and Individual Accounts**: Easily split and attribute joint expenses.
- **Beautiful UI**: Uses modern Java Swing components with custom styling.

## Getting Started

1. **Clone this repository:**
    ```sh
    git clone https://github.com/YOUR_USERNAME/whatsMyBudget.git
    ```

2. **Open the project in your favorite Java IDE.**

3. **Build and run the application.**
    - Make sure you have Java 8 or higher installed.

4. **Import your transactions:**
    - Supported formats: CSV with columns such as Name, Amount, Category, Account, Criticality, Transaction Date, Created time.

## Example CSV Format

```
Name,Amount,Category,Account,Criticality,Transaction Date,Created time
Publix,$10.00,Groceries,Joint,Essential,"August 5, 2025","August 22, 2025 11:55 AM"
Amazon,$28.36,music,Josh,NonEssential,"August 4, 2025","August 22, 2025 12:17 PM"
...
```

## Usage

- **Add projected expenses** to forecast future spending.
- **Remove projected expenses** as plans change.
- **Click on a spending category** to open a popup showing weekly spending totals for the current month.

## Contributing

Pull requests and suggestions are welcome! Please open an issue or submit a PR.

## License

MIT License

## Author

Josh Reid  