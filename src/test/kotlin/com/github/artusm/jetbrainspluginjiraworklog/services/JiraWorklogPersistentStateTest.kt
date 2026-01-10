package com.github.artusm.jetbrainspluginjiraworklog.services

import com.intellij.util.xmlb.XmlSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class JiraWorklogPersistentStateTest {

    @Test
    fun testSerializationRoundTrip() {
        val originalState = JiraWorklogPersistentState.State()
        originalState.branchToIssueMap["feature/login"] = "JIRA-123"
        originalState.branchToIssueMap["bugfix/crash"] = "JIRA-456"
        // Test detached HEAD key format
        originalState.branchToIssueMap["detached:abc1234"] = "JIRA-789"

        // Serialize
        val element = XmlSerializer.serialize(originalState)
        
        // Deserialize
        val deserializedState = XmlSerializer.deserialize(element, JiraWorklogPersistentState.State::class.java)

        assertNotNull(deserializedState)
        assertEquals(3, deserializedState.branchToIssueMap.size)
        assertEquals("JIRA-123", deserializedState.branchToIssueMap["feature/login"])
        assertEquals("JIRA-456", deserializedState.branchToIssueMap["bugfix/crash"])
        assertEquals("JIRA-789", deserializedState.branchToIssueMap["detached:abc1234"])
        
        // Ensure map is mutable after deserialization (XmlSerializer usually returns HashMap or similar)
        deserializedState.branchToIssueMap["new-branch"] = "JIRA-999"
        assertEquals("JIRA-999", deserializedState.branchToIssueMap["new-branch"])
    }
}
