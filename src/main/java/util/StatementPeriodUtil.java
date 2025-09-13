package util;

import org.slf4j.Logger;
import util.AppLogger;

import java.time.Month;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;

/**
 * Utility class for robust handling of statement periods in "MMMM yyyy" format.
 */
public class StatementPeriodUtil {
    private static final Logger logger = AppLogger.getLogger(StatementPeriodUtil.class);
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH);

    /**
     * Builds a statement period string (e.g., "August 2025") from month and year.
     * @param month Java Month enum (not null)
     * @param year  four-digit year (e.g., 2025)
     * @return "MMMM yyyy" formatted period
     */
    public static String buildStatementPeriod(Month month, int year) {
        logger.info("buildStatementPeriod called with month={}, year={}", month, year);
        if (month == null) {
            logger.error("Month cannot be null");
            throw new IllegalArgumentException("Month cannot be null");
        }
        if (year < 1900 || year > 3000) {
            logger.error("Year out of valid range: {}", year);
            throw new IllegalArgumentException("Year out of valid range");
        }
        YearMonth ym = YearMonth.of(year, month);
        String period = ym.format(FORMATTER);
        logger.info("Built statement period: {}", period);
        return period;
    }

    /**
     * Validates a statement period string (must be "MMMM yyyy").
     * @param period The statement period string to validate.
     * @return true if valid, false otherwise
     */
    public static boolean isValidStatementPeriod(String period) {
        logger.info("isValidStatementPeriod called with period={}", period);
        if (period == null || period.isBlank()) {
            logger.warn("Period is null or blank");
            return false;
        }
        try {
            YearMonth.parse(period, FORMATTER);
            logger.info("Period '{}' is valid", period);
            return true;
        } catch (DateTimeParseException e) {
            logger.error("Invalid statement period '{}': {}", period, e.getMessage());
            return false;
        }
    }

    /**
     * Parses month and year from a valid statement period string.
     * @param period "MMMM yyyy" formatted string
     * @return YearMonth object, or null if invalid
     */
    public static YearMonth parseStatementPeriod(String period) {
        logger.info("parseStatementPeriod called with period={}", period);
        if (!isValidStatementPeriod(period)) {
            logger.warn("Cannot parse invalid statement period: {}", period);
            return null;
        }
        try {
            YearMonth ym = YearMonth.parse(period, FORMATTER);
            logger.info("Parsed statement period to YearMonth: {}", ym);
            return ym;
        } catch (DateTimeParseException e) {
            logger.error("Failed to parse period '{}': {}", period, e.getMessage());
            return null;
        }
    }
}