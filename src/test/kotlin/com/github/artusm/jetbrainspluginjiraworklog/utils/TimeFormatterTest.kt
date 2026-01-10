package com.github.artusm.jetbrainspluginjiraworklog.utils

import org.junit.Assert.*
import org.junit.Test
class TimeFormatterTest {
    
    @Test
    fun `test formatDisplay for seconds`() {
        assertEquals("0s", TimeFormatter.formatDisplay(0))
        assertEquals("30s", TimeFormatter.formatDisplay(30 * 1000))
        assertEquals("59s", TimeFormatter.formatDisplay(59 * 1000))
    }
    
    @Test
    fun `test formatDisplay for minutes`() {
        assertEquals("1 min", TimeFormatter.formatDisplay(60 * 1000))
        assertEquals("5 min", TimeFormatter.formatDisplay(5 * 60 * 1000))
        assertEquals("59 min", TimeFormatter.formatDisplay(59 * 60 * 1000))
    }
    
    @Test
    fun `test formatDisplay for hours and minutes`() {
        assertEquals("1 hr", TimeFormatter.formatDisplay(3600 * 1000))
        assertEquals("1 hr 30 min", TimeFormatter.formatDisplay((3600 + 1800) * 1000))
        assertEquals("2 hrs 15 min", TimeFormatter.formatDisplay((2 * 3600 + 15 * 60) * 1000))
        assertEquals("9 hrs 13 min", TimeFormatter.formatDisplay((9 * 3600 + 13 * 60) * 1000))
    }
    
    @Test
    fun `test formatJira for Jira API`() {
        assertEquals("1m", TimeFormatter.formatJira(60 * 1000))
        assertEquals("30m", TimeFormatter.formatJira(30 * 60 * 1000))
        assertEquals("1h", TimeFormatter.formatJira(3600 * 1000))
        assertEquals("1h 30m", TimeFormatter.formatJira((3600 + 1800) * 1000))
        assertEquals("2h 15m", TimeFormatter.formatJira((2 * 3600 + 15 * 60) * 1000))
        assertEquals("9h 13m", TimeFormatter.formatJira((9 * 3600 + 13 * 60) * 1000))
    }
    
    @Test
    fun `test formatJira minimum 1 minute`() {
        // Less than 1 minute should be rounded to 1m
        assertEquals("1m", TimeFormatter.formatJira(30 * 1000))
        assertEquals("1m", TimeFormatter.formatJira(0))
    }
    
    @Test
    fun `test formatDetailed with leading zeros`() {
        assertEquals("00:00:00", TimeFormatter.formatDetailed(0))
        assertEquals("00:01:30", TimeFormatter.formatDetailed(90 * 1000))
        assertEquals("01:00:00", TimeFormatter.formatDetailed(3600 * 1000))
        assertEquals("09:13:42", TimeFormatter.formatDetailed((9 * 3600 + 13 * 60 + 42) * 1000))
        assertEquals("23:59:59", TimeFormatter.formatDetailed((23 * 3600 + 59 * 60 + 59) * 1000))
    }
    
    @Test
    fun `test parseJira hours only`() {
        assertEquals(3600 * 1000L, TimeFormatter.parseJira("1h"))
        assertEquals(2 * 3600 * 1000L, TimeFormatter.parseJira("2h"))
    }
    
    @Test
    fun `test parseJira minutes only`() {
        assertEquals(60 * 1000L, TimeFormatter.parseJira("1m"))
        assertEquals(30 * 60 * 1000L, TimeFormatter.parseJira("30m"))
    }
    
    @Test
    fun `test parseJira hours and minutes`() {
        assertEquals((3600 + 1800) * 1000L, TimeFormatter.parseJira("1h 30m"))
        assertEquals((2 * 3600 + 15 * 60) * 1000L, TimeFormatter.parseJira("2h 15m"))
    }
    
    @Test
    fun `test parseJira with seconds`() {
        assertEquals(90 * 1000L, TimeFormatter.parseJira("1m 30s"))
        assertEquals((3600 + 60 + 30) * 1000L, TimeFormatter.parseJira("1h 1m 30s"))
    }
    
    @Test
    fun `test parseJira empty string`() {
        assertEquals(0L, TimeFormatter.parseJira(""))
        assertEquals(0L, TimeFormatter.parseJira("invalid"))
    }
}
