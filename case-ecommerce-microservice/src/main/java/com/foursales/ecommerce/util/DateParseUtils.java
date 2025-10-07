package com.foursales.ecommerce.util;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

@Slf4j
@UtilityClass
public class DateParseUtils {

    public static LocalDateTime parseFlexibleDate(String dateStr, boolean isStartDate) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            log.debug("Received null or empty date string, returning null");
            return null;
        }

        dateStr = dateStr.trim();
        log.debug("Parsing date string: '{}' (isStartDate={})", dateStr, isStartDate);

        try {
            LocalDateTime result = LocalDateTime.parse(dateStr, DateTimeFormatter.ISO_DATE_TIME);
            log.debug("Successfully parsed as ISO DateTime: {}", result);
            return result;

        } catch (DateTimeParseException e1) {
            log.debug("Failed to parse as ISO DateTime, trying DATE format: {}", e1.getMessage());

            try {
                LocalDate date = LocalDate.parse(dateStr, DateTimeFormatter.ISO_DATE);

                LocalDateTime result = isStartDate
                        ? date.atStartOfDay()
                        : date.atTime(LocalTime.MAX);

                log.debug("Successfully parsed as DATE and converted to: {}", result);
                return result;

            } catch (DateTimeParseException e2) {
                log.error(
                        "Failed to parse date string '{}'. Supported formats: ISO DateTime (2025-10-01T00:00:00) or DATE (2025-10-01)",
                        dateStr);

                throw new IllegalArgumentException(
                        String.format(
                                "Invalid date format. Expected ISO DateTime (2025-10-01T00:00:00) or DATE (2025-10-01). Received: '%s'",
                                dateStr),
                        e2);
            }
        }
    }

    public static LocalDateTime parseStartDate(String dateStr) {
        return parseFlexibleDate(dateStr, true);
    }

    public static LocalDateTime parseEndDate(String dateStr) {
        return parseFlexibleDate(dateStr, false);
    }
}
