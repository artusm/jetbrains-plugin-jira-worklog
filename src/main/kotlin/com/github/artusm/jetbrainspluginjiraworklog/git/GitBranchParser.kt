package com.github.artusm.jetbrainspluginjiraworklog.git

import com.intellij.openapi.components.Service

/**
 * Service for parsing Jira issue keys from Git branch names.
 * 
 * Pattern examples:
 * - PARENT-123/SUBTASK-456-feature-name → Priority 1: SUBTASK-456, Priority 2: PARENT-123
 * - PARENT-123-feature-name → Priority 2 only: PARENT-123
 */
@Service(Service.Level.APP)
class GitBranchParser {
    
    companion object {
        // Regex to match Jira issue keys (PROJECT-123 format)
        private val JIRA_KEY_PATTERN = Regex("[A-Z][A-Z0-9_]+-\\d+")
    }
    
    data class ParseResult(
        /** Subtask key (if present) - Priority 1 */
        val subtaskKey: String? = null,
        
        /** Parent key - Priority 2 */
        val parentKey: String? = null
    ) {
        /** Get the primary key (subtask if available, otherwise parent) */
        fun getPrimaryKey(): String? = subtaskKey ?: parentKey
        
        /** Check if any key was found */
        fun hasAnyKey(): Boolean = subtaskKey != null || parentKey != null
    }
    
    /**
     * Parse a Git branch name and extract Jira issue keys.
     * 
     * @param branchName The Git branch name to parse
     * @return ParseResult containing found keys
     */
    fun parseBranchName(branchName: String?): ParseResult {
        if (branchName.isNullOrBlank()) {
            return ParseResult()
        }
        
        // Find all Jira keys in the branch name
        val matches = JIRA_KEY_PATTERN.findAll(branchName).map { it.value }.toList()
        
        return when (matches.size) {
            0 -> ParseResult() // No keys found
            1 -> ParseResult(parentKey = matches[0]) // One key (parent only)
            else -> {
                // Multiple keys found
                // Priority 1: The second key is usually the subtask
                // Priority 2: The first key is usually the parent
                ParseResult(
                    subtaskKey = matches[1],
                    parentKey = matches[0]
                )
            }
        }
    }
    
    /**
     * Simple helper to get just the primary key from a branch name.
     */
    fun extractPrimaryKey(branchName: String?): String? {
        return parseBranchName(branchName).getPrimaryKey()
    }
}
