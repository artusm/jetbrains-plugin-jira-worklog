package com.github.artusm.jetbrainspluginjiraworklog.config

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.generateServiceName
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.components.*

/**
 * Application-level settings for Jira integration and auto-pause configuration.
 */
@Service(Service.Level.APP)
@State(
    name = "JiraSettings",
    storages = [Storage("jiraWorklogSettings.xml")]
)
class JiraSettings : PersistentStateComponent<JiraSettings.State> {
    
    private var state = State()
    
    data class State(
        /** Jira instance URL (e.g., https://company.atlassian.net) */
        var jiraUrl: String = "",
        
        /** Auto-pause when IDE window loses focus */
        var pauseOnFocusLoss: Boolean = true,
        
        /** Auto-pause when switching Git branches */
        var pauseOnBranchChange: Boolean = true,
        
        /** Auto-pause when switching to different project */
        var pauseOnProjectSwitch: Boolean = true
    )
    
    companion object {
        private const val CREDENTIAL_KEY = "JiraPersonalAccessToken"
        
        fun getInstance(): JiraSettings = service()
    }
    
    override fun getState(): State = state
    
    override fun loadState(state: State) {
        this.state = state
    }
    
    // Jira URL
    fun getJiraUrl(): String = state.jiraUrl
    
    fun setJiraUrl(url: String) {
        state.jiraUrl = url.trim().trimEnd('/')
    }
    
    // Personal Access Token (stored securely)
    fun getPersonalAccessToken(): String? {
        val credentialAttributes = createCredentialAttributes(CREDENTIAL_KEY)
        return PasswordSafe.instance.getPassword(credentialAttributes)
    }
    
    fun setPersonalAccessToken(token: String?) {
        val credentialAttributes = createCredentialAttributes(CREDENTIAL_KEY)
        if (token.isNullOrBlank()) {
            PasswordSafe.instance.setPassword(credentialAttributes, null)
        } else {
            PasswordSafe.instance.setPassword(credentialAttributes, token)
        }
    }
    
    // Auto-pause settings
    fun isPauseOnFocusLoss(): Boolean = state.pauseOnFocusLoss
    
    fun setPauseOnFocusLoss(enabled: Boolean) {
        state.pauseOnFocusLoss = enabled
    }
    
    fun isPauseOnBranchChange(): Boolean = state.pauseOnBranchChange
    
    fun setPauseOnBranchChange(enabled: Boolean) {
        state.pauseOnBranchChange = enabled
    }
    
    fun isPauseOnProjectSwitch(): Boolean = state.pauseOnProjectSwitch
    
    fun setPauseOnProjectSwitch(enabled: Boolean) {
        state.pauseOnProjectSwitch = enabled
    }
    
    // Check if credentials are configured
    fun hasCredentials(): Boolean {
        return state.jiraUrl.isNotBlank() && !getPersonalAccessToken().isNullOrBlank()
    }
    
    private fun createCredentialAttributes(key: String): CredentialAttributes {
        return CredentialAttributes(
            generateServiceName("JiraWorklogTimer", key)
        )
    }
}
