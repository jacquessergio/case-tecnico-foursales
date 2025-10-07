package com.foursales.ecommerce.util;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DateParseUtils utility class
 */
class DateParseUtilsTest {

    @Test
    void testParseISODateTime() {
        LocalDateTime result = DateParseUtils.parseFlexibleDate("2025-10-01T00:00:00", true);
        assertNotNull(result);
        assertEquals(LocalDateTime.of(2025, 10, 1, 0, 0, 0), result);
    }

    @Test
    void testParseSimpleDateAsStartDate() {
        LocalDateTime result = DateParseUtils.parseStartDate("2025-10-01");
        assertNotNull(result);
        assertEquals(LocalDateTime.of(2025, 10, 1, 0, 0, 0), result);
    }

    @Test
    void testParseSimpleDateAsEndDate() {
        LocalDateTime result = DateParseUtils.parseEndDate("2025-10-31");
        assertNotNull(result);
        assertEquals(LocalDateTime.of(2025, 10, 31, 23, 59, 59, 999999999), result);
    }

    @Test
    void testParseNullDate() {
        LocalDateTime result = DateParseUtils.parseFlexibleDate(null, true);
        assertNull(result);
    }

    @Test
    void testParseEmptyDate() {
        LocalDateTime result = DateParseUtils.parseFlexibleDate("", true);
        assertNull(result);
    }

    @Test
    void testParseInvalidDate() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            DateParseUtils.parseFlexibleDate("invalid-date", true);
        });

        assertTrue(exception.getMessage().contains("Invalid date format"));
        assertTrue(exception.getMessage().contains("invalid-date"));
    }

    @Test
    void testParseDateWithWhitespace() {
        LocalDateTime result = DateParseUtils.parseFlexibleDate("  2025-10-01  ", true);
        assertNotNull(result);
        assertEquals(LocalDateTime.of(2025, 10, 1, 0, 0, 0), result);
    }

    @Test
    void testConvenienceMethodParseStartDate() {
        LocalDateTime result = DateParseUtils.parseStartDate("2025-10-15");
        assertNotNull(result);
        assertEquals(LocalDateTime.of(2025, 10, 15, 0, 0, 0), result);
    }

    @Test
    void testConvenienceMethodParseEndDate() {
        LocalDateTime result = DateParseUtils.parseEndDate("2025-10-15");
        assertNotNull(result);
        assertEquals(LocalDateTime.of(2025, 10, 15, 23, 59, 59, 999999999), result);
    }

    @Test
    void testStartDateWithDateTime() {
        LocalDateTime result = DateParseUtils.parseStartDate("2025-10-01T14:30:00");
        assertNotNull(result);
        assertEquals(LocalDateTime.of(2025, 10, 1, 14, 30, 0), result);
    }

    @Test
    void testEndDateWithDateTime() {
        LocalDateTime result = DateParseUtils.parseEndDate("2025-10-31T18:45:30");
        assertNotNull(result);
        assertEquals(LocalDateTime.of(2025, 10, 31, 18, 45, 30), result);
    }

    @Test
    void testParseFlexibleDateStartOfDay() {
        LocalDateTime result = DateParseUtils.parseFlexibleDate("2025-12-25", true);
        assertNotNull(result);
        assertEquals(0, result.getHour());
        assertEquals(0, result.getMinute());
        assertEquals(0, result.getSecond());
    }

    @Test
    void testParseFlexibleDateEndOfDay() {
        LocalDateTime result = DateParseUtils.parseFlexibleDate("2025-12-25", false);
        assertNotNull(result);
        assertEquals(23, result.getHour());
        assertEquals(59, result.getMinute());
        assertEquals(59, result.getSecond());
        assertEquals(999999999, result.getNano());
    }
}
