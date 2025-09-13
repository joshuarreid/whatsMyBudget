package ui;

import org.slf4j.Logger;
import util.AppLogger;

import javax.swing.*;

/**
 * Abstract base class for all individual account view panels (e.g., Josh, Anna, Joint).
 * Provides standard access to essential and non-essential CategorySummaryPanels.
 * Enforces implementation of a TablePanel retrieval method for transaction analytics.
 *
 * All subclasses must call super() in their constructor and initialize required components.
 */
public abstract class IndividualViewPanel extends JPanel {
    protected final Logger logger = AppLogger.getLogger(getClass());

    // Panels showing category summaries for essential and non-essential spend
    private final CategorySummaryPanel essentialPanel;
    private final CategorySummaryPanel nonEssentialPanel;
    private final String ACCOUNT;

    /**
     * Constructs the individual view panel with standard summary panels.
     * Subclasses should use/add their own layout and initialize table panels as needed.
     */
    public IndividualViewPanel(String account) {
        super();
        this.ACCOUNT = account;
        logger.info("Initializing IndividualViewPanel.");

        this.essentialPanel = new CategorySummaryPanel(ACCOUNT,"Essential");
        this.nonEssentialPanel = new CategorySummaryPanel(ACCOUNT,"Non-Essential");

        logger.info("Essential and Non-Essential CategorySummaryPanels created.");
    }

    /**
     * Returns the panel showing essential spending breakdown.
     * @return the essential CategorySummaryPanel
     */
    public CategorySummaryPanel getEssentialPanel() {
        logger.info("getEssentialPanel called.");
        return essentialPanel;
    }

    /**
     * Returns the panel showing non-essential spending breakdown.
     * @return the non-essential CategorySummaryPanel
     */
    public CategorySummaryPanel getNonEssentialPanel() {
        logger.info("getNonEssentialPanel called.");
        return nonEssentialPanel;
    }

}