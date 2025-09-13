package ui;

import org.slf4j.Logger;
import util.AppLogger;

import javax.swing.*;
import java.awt.*;
import java.time.Month;
import java.time.Year;
import java.util.function.BiConsumer;

/**
 * Panel for selecting a statement period using Month and Year controls.
 * Includes an "Apply to All" button to assign the period to all transactions.
 */
public class PeriodSelectorPanel extends JPanel {
    private static final Logger logger = AppLogger.getLogger(PeriodSelectorPanel.class);

    private final JComboBox<Month> monthComboBox;
    private final JSpinner yearSpinner;
    private final JButton applyButton;

    /**
     * Constructs the period selector panel.
     * @param onApplyPeriod BiConsumer callback (Month, year) for when "Apply to All" is pressed
     */
    public PeriodSelectorPanel(BiConsumer<Month, Integer> onApplyPeriod) {
        logger.info("Initializing PeriodSelectorPanel");
        setLayout(new FlowLayout(FlowLayout.LEFT));
        add(new JLabel("Statement Period:"));

        monthComboBox = new JComboBox<>(Month.values());
        add(monthComboBox);

        int currentYear = Year.now().getValue();
        SpinnerNumberModel yearModel = new SpinnerNumberModel(currentYear, 2000, 2100, 1);
        yearSpinner = new JSpinner(yearModel);
        add(yearSpinner);

        applyButton = new JButton("Apply to All");
        add(applyButton);

        applyButton.addActionListener(e -> {
            Month selectedMonth = (Month) monthComboBox.getSelectedItem();
            Integer selectedYear = (Integer) yearSpinner.getValue();
            logger.info("Apply to All clicked: month={}, year={}", selectedMonth, selectedYear);
            if (onApplyPeriod != null) {
                onApplyPeriod.accept(selectedMonth, selectedYear);
            }
        });
    }

    /**
     * Gets the selected month.
     */
    public Month getSelectedMonth() {
        return (Month) monthComboBox.getSelectedItem();
    }

    /**
     * Gets the selected year.
     */
    public int getSelectedYear() {
        return (Integer) yearSpinner.getValue();
    }
}