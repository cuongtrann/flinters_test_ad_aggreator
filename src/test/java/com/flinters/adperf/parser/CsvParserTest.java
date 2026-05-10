package com.flinters.adperf.parser;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class CsvParserTest {

    private CsvParser parser;

    @BeforeEach
    void setUp() {
        parser = new CsvParser();
    }

    @Test
    void validRow_parsesAllFieldsCorrectly() {
        Optional<ParsedRow> result = parser.parseLine("CMP01,2024-01-01,1000,50,200.0,5", 1);
        assertTrue(result.isPresent());
        ParsedRow row = result.get();
        assertEquals("CMP01", row.campaignId());
        assertEquals(1000, row.impressions());
        assertEquals(50, row.clicks());
        assertEquals(200.0, row.spend(), 1e-9);
        assertEquals(5, row.conversions());
        assertEquals(0, parser.getWarnCount());
    }

    @Test
    void headerLine_returnsEmptyWithoutWarning() {
        Optional<ParsedRow> result = parser.parseLine("campaign_id,date,impressions,clicks,spend,conversions", 1);
        assertTrue(result.isEmpty());
        assertEquals(0, parser.getWarnCount());
    }

    @Test
    void tooFewColumns_returnsEmptyWithWarning() {
        Optional<ParsedRow> result = parser.parseLine("CMP01,2024-01-01,1000", 2);
        assertTrue(result.isEmpty());
        assertEquals(1, parser.getWarnCount());
    }

    @Test
    void tooManyColumns_returnsEmptyWithWarning() {
        Optional<ParsedRow> result = parser.parseLine("CMP01,2024-01-01,1000,50,200.0,5,extra", 3);
        assertTrue(result.isEmpty());
        assertEquals(1, parser.getWarnCount());
    }

    @Test
    void nonNumericImpressions_returnsEmptyWithWarning() {
        Optional<ParsedRow> result = parser.parseLine("CMP01,2024-01-01,abc,50,200.0,5", 4);
        assertTrue(result.isEmpty());
        assertEquals(1, parser.getWarnCount());
    }

    @Test
    void zeroValues_parsesCorrectly() {
        Optional<ParsedRow> result = parser.parseLine("CMP01,2024-01-01,0,0,0.0,0", 5);
        assertTrue(result.isPresent());
        ParsedRow row = result.get();
        assertEquals(0, row.impressions());
        assertEquals(0, row.clicks());
        assertEquals(0.0, row.spend(), 0.0);
        assertEquals(0, row.conversions());
        assertEquals(0, parser.getWarnCount());
    }

    @Test
    void negativeSpend_parsesCorrectly() {
        Optional<ParsedRow> result = parser.parseLine("CMP01,2024-01-01,1000,50,-5.0,3", 6);
        assertTrue(result.isPresent());
        assertEquals(-5.0, result.get().spend(), 1e-9);
    }

    @Test
    void whitespacePadding_trimmedCorrectly() {
        Optional<ParsedRow> result = parser.parseLine(" CMP01 , 2024-01-01 , 1000 , 50 , 200.0 , 5 ", 7);
        assertTrue(result.isPresent());
        assertEquals("CMP01", result.get().campaignId());
        assertEquals(1000, result.get().impressions());
    }

    @Test
    void emptyCampaignId_parsesAsEmptyString() {
        Optional<ParsedRow> result = parser.parseLine(",2024-01-01,1000,50,200.0,5", 8);
        assertTrue(result.isPresent());
        assertEquals("", result.get().campaignId());
    }
}
