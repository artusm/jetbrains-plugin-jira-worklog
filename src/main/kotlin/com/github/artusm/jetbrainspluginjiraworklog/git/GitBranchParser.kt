package com.github.artusm.jetbrainspluginjiraworklog.git

import com.intellij.openapi.components.Service

/**
 * Service for parsing Jira issue keys from Git branch names.
 * Priority: Matches subtask key (second match) then parent key (first match).
 */
@Service(Service.Level.APP)
class GitBranchParser {
    
    companion object {
        private val JIRA_KEY_PATTERN = Regex("[A-Z][A-Z0-9_]+-\\d+")
    }
    
    data class ParseResult(
        val subtaskKey: String? = null,
        val parentKey: String? = null
    ) {
        fun getPrimaryKey(): String? = subtaskKey ?: parentKey
    }
    
    fun parseBranchName(branchName: String?): ParseResult {
        if (branchName.isNullOrBlank()) return ParseResult()
        
        val matches = JIRA_KEY_PATTERN.findAll(branchName).map { it.value }.toList()
        
        return when (matches.size) {
            0 -> ParseResult()
            1 -> ParseResult(parentKey = matches[0])
            else -> ParseResult(subtaskKey = matches[1], parentKey = matches[0])
        }
    }
    
    fun extractPrimaryKey(branchName: String?): String? {
        return parseBranchName(branchName).getPrimaryKey()
    }
}
