# AI Agent Context - Jira Worklog Timer Plugin

## Project Overview

**Plugin Name:** Jira Worklog Timer  
**Platform:** JetBrains IntelliJ Platform  
**Language:** Kotlin  
**Purpose:** Track time in IDE status bar and submit worklogs to Jira

## Architecture Summary

### Core Components

1. **Timer Service** (`JiraWorklogTimerService.kt`)
   - Project-scoped service managing timer state
   - Background ticker updates every second
   - Three states: RUNNING, IDLE, STOPPED
   - Synchronized time tracking in milliseconds

2. **Status Bar Widget** (`JiraWorklogWidget.kt`)
   - Custom status bar widget with color-coded backgrounds
   - Hover state shows three clickable sections: Start/Stop, Commit, Settings
   - Adapted from reference plugin `TimeTrackerWidget.java`

3. **Jira Integration** (`JiraApiClient.kt`)
   - **CRITICAL:** Strictly uses Jira REST API v2 only
   - Authentication via Personal Access Token (Bearer)
   - Endpoints: search issues, get issue with subtasks, submit worklog

4. **Git Integration** (`GitBranchParser.kt`)
   - Parses branch names for Jira issue keys
   - Priority logic: `PARENT-123/SUBTASK-456` → prefers `SUBTASK-456`
   - Regex pattern: `[A-Z][A-Z0-9_]+-\\d+`

5. **Commit Popup** (`CommitWorklogPopupContent.kt`)
   - **CRITICAL:** Uses inline popup (Box), NOT DialogWrapper
   - Shown via `JBPopupFactory.createComponentPopupBuilder()`
   - Positioned above widget, aligned right

## File Organization

```
src/main/kotlin/com/github/artusm/jetbrainspluginjiraworklog/
├── config/
│   ├── JiraSettings.kt              # App-level settings with secure PAT storage
│   └── JiraWorklogConfigurable.kt   # Settings UI in IDE preferences
├── git/
│   ├── BranchChangeListener.kt      # Auto-pause on branch change
│   └── GitBranchParser.kt           # Extract Jira keys from branch names
├── jira/
│   ├── JiraApiClient.kt             # REST API v2 client
│   └── JiraModels.kt                # Data models with kotlinx.serialization
├── model/
│   └── TimeTrackingStatus.kt        # Enum: RUNNING, IDLE, STOPPED
├── onboarding/
│   └── OnboardingDialog.kt          # First-run setup dialog
├── services/
│   ├── JiraWorklogPersistentState.kt # Project-level timer persistence
│   └── JiraWorklogTimerService.kt    # Core timer service
├── startup/
│   └── OnboardingStartupActivity.kt  # Startup check for credentials
├── ui/
│   ├── CommitWorklogPopupContent.kt  # Inline commit popup (extends Box)
│   ├── JiraWorklogWidget.kt          # Status bar widget
│   └── JiraWorklogWidgetFactory.kt   # Widget factory
└── utils/
    └── TimeFormatter.kt              # Time formatting utilities
```

## Critical Design Decisions

### Threading Model
- **EDT Requirements:** DialogWrapper, UI components must run on EDT
- **Background Operations:** Jira API calls, Git operations run in background threads
- **UI Updates:** Use `SwingUtilities.invokeLater` or `ApplicationManager.invokeLater`
- **Timer Tick:** Runs on `AppExecutorUtil.getAppScheduledExecutorService()`

### Data Persistence
- **Project-level:** Timer state stored in workspace `.idea/` files via `JiraWorklogPersistentState`
- **Application-level:** Settings stored in IDE config via `JiraSettings`
- **Secure Storage:** PAT stored using `PasswordSafe` with `CredentialAttributes`

### UI Patterns
- **Popup over Dialog:** Use `JBPopupFactory` for lightweight inline popups
- **Widget Painting:** Override `paintComponent()` for custom rendering
- **Color Coding:** Green (running), Yellow (idle), Red (stopped)

## Dependencies

### Build Dependencies
- `kotlinx.serialization` plugin (version 1.9.21)
- `kotlinx-serialization-json:1.6.0`
- `Git4Idea` (bundled plugin)

### Plugin Dependencies
```xml
<depends>com.intellij.modules.platform</depends>
<depends>Git4Idea</depends>
```

## Common Modification Patterns

### Adding a New Setting
1. Add field to `JiraSettings.State` data class
2. Add getter/setter methods in `JiraSettings`
3. Add UI component in `JiraWorklogConfigurable.createComponent()`
4. Update `isModified()`, `apply()`, `reset()` methods

### Adding a New Jira API Endpoint
1. Add data models in `JiraModels.kt` with `@Serializable`
2. Add method in `JiraApiClient.kt` returning `Result<T>`
3. Use `executeGet()` or `executePost()` helper methods
4. Call from background thread, update UI on EDT

### Modifying Widget Appearance
1. Edit `paintComponent()` in `JiraWorklogWidget.kt`
2. Use `JBUI` for scaling (e.g., `JBUI.scale(2)`)
3. Update color constants if needed
4. Test with different themes (light/dark)

## Testing Strategy

### Unit Tests
- `GitBranchParserTest.kt` - Branch parsing logic
- `TimeFormatterTest.kt` - Time formatting utilities
- Use JUnit 4 (project dependency)

### Manual Testing Checklist
1. Install plugin from `build/distributions/*.zip`
2. Test onboarding flow on first run
3. Verify timer start/stop/idle functionality
4. Test Git branch parsing with various patterns
5. Test Jira integration (requires valid instance)
6. Test commit popup positioning and submission
7. Test auto-pause on branch change
8. Test settings persistence across IDE restarts

## Build Commands

```bash
# Build plugin
./gradlew buildPlugin

# Run tests
./gradlew test

# Run IDE with plugin for development
./gradlew runIde

# Install location after build
build/distributions/jetbrains-plugin-jira-worklog-0.0.1.zip
```

## Known Limitations

1. **Window focus auto-pause:** Not yet implemented
2. **Project switch auto-pause:** Not yet implemented
3. **Offline mode:** No queuing of worklogs when Jira unavailable
4. **Error recovery:** Limited retry logic for API failures

## Reference Plugin

**Location:** `reference-plugin-dont-change-it/`  
**DO NOT MODIFY:** This is the reference implementation

Key reference files:
- `TimeTrackerWidget.java` - Widget UI patterns
- `TimeTrackerService.java` - Timer service patterns
- `TimeTrackerPopupContent.java` - Inline popup patterns

## Code Style Guidelines

1. **Kotlin preferred** for new files
2. **Service pattern:** Use `@Service` with `service<T>()` or `project.service<T>()`
3. **Null safety:** Use Kotlin's null-safe operators
4. **Background tasks:** Always check `!project.isDisposed` before EDT updates
5. **Resource cleanup:** Implement `dispose()` when needed

## Common Issues & Solutions

### Issue: Plugin fails to load
- Check `plugin.xml` for syntax errors
- Verify all dependencies are declared
- Check IntelliJ Platform version compatibility

### Issue: Threading violations
- Ensure DialogWrapper created on EDT
- Use `ApplicationManager.invokeLater` for UI updates
- Check stack trace for `ThreadingAssertions` errors

### Issue: Jira API errors
- Verify PAT has correct permissions
- Check Jira URL format (no trailing slash)
- Ensure using REST API v2 endpoints only

### Issue: Settings not persisting
- Verify `@State` annotation parameters
- Check storage file location in `.idea/` or IDE config
- Ensure `loadState()` and `getState()` implemented correctly

## Future Enhancement Ideas

1. Worklog history viewer
2. Time reports per issue/project
3. Bulk worklog submission
4. Jira issue search with filters
5. Custom time rounding rules
6. Multi-project time tracking
7. Offline mode with queue
8. Notification system improvements

## Additional Resources

- [IntelliJ Platform SDK](https://plugins.jetbrains.com/docs/intellij/)
- [Jira REST API v2 Docs](https://developer.atlassian.com/cloud/jira/platform/rest/v2/)
- [Git4Idea Plugin Docs](https://github.com/JetBrains/intellij-community/tree/master/plugins/git4idea)
- [Threading Guide](https://jb.gg/ij-platform-threading)
