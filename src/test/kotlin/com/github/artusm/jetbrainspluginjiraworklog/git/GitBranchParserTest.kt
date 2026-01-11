package com.github.artusm.jetbrainspluginjiraworklog.git

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for GitBranchParser.
 */
class GitBranchParserTest {
    
    private val parser = GitBranchParser()
    
    @Test
    fun `test parse branch with parent and subtask`() {
        val result = parser.parseBranchName("PARENT-123/SUBTASK-456-feature-name")
        
        assertEquals("SUBTASK-456", result.subtaskKey)
        assertEquals("PARENT-123", result.parentKey)
        assertEquals("SUBTASK-456", result.getPrimaryKey()) // Subtask has priority
        assertTrue(result.hasAnyKey())
    }
    
    @Test
    fun `test parse branch with parent only`() {
        val result = parser.parseBranchName("PARENT-123-feature-name")
        
        assertNull(result.subtaskKey)
        assertEquals("PARENT-123", result.parentKey)
        assertEquals("PARENT-123", result.getPrimaryKey())
        assertTrue(result.hasAnyKey())
    }
    
    @Test
    fun `test parse branch with no Jira key`() {
        val result = parser.parseBranchName("feature/my-feature")
        
        assertNull(result.subtaskKey)
        assertNull(result.parentKey)
        assertNull(result.getPrimaryKey())
        assertFalse(result.hasAnyKey())
    }
    
    @Test
    fun `test parse null branch`() {
        val result = parser.parseBranchName(null)
        
        assertNull(result.subtaskKey)
        assertNull(result.parentKey)
        assertFalse(result.hasAnyKey())
    }
    
    @Test
    fun `test parse empty branch`() {
        val result = parser.parseBranchName("")
        
        assertNull(result.subtaskKey)
        assertNull(result.parentKey)
        assertFalse(result.hasAnyKey())
    }
    
    @Test
    fun `test extractPrimaryKey helper`() {
        assertEquals("SUBTASK-456", parser.extractPrimaryKey("PARENT-123/SUBTASK-456-feature"))
        assertEquals("PARENT-123", parser.extractPrimaryKey("PARENT-123-feature"))
        assertNull(parser.extractPrimaryKey("no-jira-key"))
        assertNull(parser.extractPrimaryKey(null))
    }
    
    @Test
    fun `test parse with multiple keys prefers last as subtask`() {
        // Updated behavior: last key is subtask/primary
        val result = parser.parseBranchName("ABC-111/DEF-222/GHI-333")
        
        assertEquals("GHI-333", result.subtaskKey)
        assertEquals("ABC-111", result.parentKey)
        assertEquals("GHI-333", result.getPrimaryKey())
    }

    @Test
    fun `test parse with many keys prefers last as subtask`() {
        val result = parser.parseBranchName("ABC-1/DEF-2/GHI-3/JKL-4")

        assertEquals("JKL-4", result.subtaskKey)
        assertEquals("ABC-1", result.parentKey)
        assertEquals("JKL-4", result.getPrimaryKey())
    }
}
