# Jira Worklog Timer Plugin

[![Build](https://github.com/artusm/jetbrains-plugin-jira-worklog/workflows/Build/badge.svg)](https://github.com/artusm/jetbrains-plugin-jira-worklog/actions/workflows/build.yml)
[![Version](https://img.shields.io/jetbrains/plugin/v/MARKETPLACE_ID.svg)](https://plugins.jetbrains.com/plugin/MARKETPLACE_ID)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/MARKETPLACE_ID.svg)](https://plugins.jetbrains.com/plugin/MARKETPLACE_ID)

> A JetBrains IDE plugin that integrates a worklog timer into the status bar, allowing developers to track time and submit worklogs directly to Jira tasks derived from the current Git branch.

<!-- Plugin description -->
Track time directly in your IDE status bar and submit worklogs to Jira with one click. Automatically detects Jira issue keys from your Git branch names and provides an inline popup for quick time submission. Features color-coded timer states, auto-pause on branch changes, and secure credential storage.
<!-- Plugin description end -->

## Features

### 🕒 Timer Management
- **Status bar widget** with real-time timer display
- **Color-coded states**: Green (running), Yellow (idle), Red (stopped)
- **Persistent state** - timer survives IDE restarts
- **Hover controls** - Start/Stop, Commit, Settings icons appear on hover

### 🎯 Jira Integration
- **Jira REST API v2** integration with Personal Access Token authentication
- **Automatic issue detection** from Git branch names (e.g., `PARENT-123/SUBTASK-456-feature`)
- **Inline commit popup** for submitting worklogs
- **Issue search** - browse and select from your assigned Jira tasks
- **Subtask support** - automatically loads subtasks when relevant

### ⚡ Quick Time Adjustments
Fast action buttons in commit popup:
- `+1h` / `-1h` - Add or subtract an hour
- `+30m` - Add 30 minutes
- `×2` / `÷2` - Double or halve current time

### 🔄 Git Integration
- **Branch parsing** - Extracts Jira issue keys from branch names
- **Auto-pause** - Optionally pause timer on branch switch
- **Priority logic** - Prefers subtask keys over parent keys

### ⚙️ Configuration
- **Settings page** in IDE Preferences
- **Secure credential storage** using IntelliJ's PasswordSafe
- **Auto-pause toggles**:
  - Pause on Git branch change
  - Pause on window focus loss
  - Pause on project switch
  - Pause on system sleep

### 🚀 Onboarding
- **First-run setup** dialog for Jira credentials
- **Connection test** validates credentials before saving
- **Helper links** to generate Personal Access Tokens

## Installation

### From JetBrains Marketplace (Coming Soon)
1. Open Settings/Preferences → Plugins
2. Search for "Jira Worklog Timer"
3. Click Install

### Manual Installation
1. Download the latest release from [Releases](https://github.com/artusm/jetbrains-plugin-jira-worklog/releases)
2. Open Settings/Preferences → Plugins
3. Click ⚙️ → Install Plugin from Disk
4. Select the downloaded ZIP file

### Build from Source
```bash
git clone https://github.com/artusm/jetbrains-plugin-jira-worklog.git
cd jetbrains-plugin-jira-worklog
./gradlew buildPlugin
# Plugin will be in build/distributions/
```

## Setup

1. **Generate a Jira Personal Access Token**
   - Visit [Atlassian Account Security](https://id.atlassian.com/manage-profile/security/api-tokens)
   - Create a new API token
   - Copy the token (you won't see it again!)

2. **Configure the Plugin**
   - On first run, enter your Jira instance URL (e.g., `https://company.atlassian.net`)
   - Paste your Personal Access Token
   - Click OK to test connection

3. **Start Tracking**
   - Click the timer in the status bar to start/stop
   - Hover to see quick actions
   - Click the commit icon to submit time to Jira

## Usage

### Starting the Timer
- Click the widget in the status bar, or
- Click the start icon when hovering

### Committing Time to Jira
1. Hover over the widget
2. Click the middle section (commit icon)
3. Select the Jira issue (auto-populated from your Git branch)
4. Adjust time using quick buttons if needed
5. Add an optional comment
6. Click "Submit Worklog"

### Git Branch Naming
For automatic issue detection, name your branches like:
- `PROJ-123-feature-name` → detects `PROJ-123`
- `PARENT-100/TASK-200-bugfix` → detects `TASK-200` (priority)

## Development

### Prerequisites
- JDK 17 or later
- IntelliJ IDEA (recommended)

### Project Structure
```
src/main/kotlin/
├── config/          # Settings and configuration
├── git/             # Git branch integration
├── jira/            # Jira REST API client
├── model/           # Data models
├── onboarding/      # First-run setup
├── services/        # Core timer service
├── startup/         # Startup activities
├── ui/              # UI components (widget, popup)
└── utils/           # Utility functions
```

### Build Commands
```bash
# Build plugin
./gradlew buildPlugin

# Run tests
./gradlew test

# Run IDE with plugin for development
./gradlew runIde

# Generate documentation
./gradlew buildSearchableOptions
```

### Testing
```bash
# Run unit tests
./gradlew test --tests "*GitBranchParserTest" --tests "*TimeFormatterTest"

# Manual testing checklist in agents.md
```

### For AI/LLM Development
- See **[agents.md](agents.md)** for comprehensive architecture documentation
- Includes component overview, threading model, common patterns, and troubleshooting

## Architecture

Key components:
- **Timer Service** - Background ticker with persistent state
- **Status Bar Widget** - Custom UI with hover interactions
- **Jira API Client** - REST API v2 integration
- **Git Integration** - Branch parsing and auto-pause
- **Inline Popup** - Lightweight commit UI

For detailed architecture, see [agents.md](agents.md)

## Contributing

Contributions are welcome! Please:
1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests if applicable
5. Submit a pull request

## Roadmap

### Planned Features
- [ ] Worklog history viewer
- [ ] Time reports per issue/project
- [ ] Offline mode with queue
- [ ] Bulk worklog submission
- [ ] Custom time rounding rules

### Completed
- [x] Core timer functionality
- [x] Status bar widget with color coding
- [x] Jira REST API v2 integration
- [x] Git branch parsing
- [x] Inline commit popup
- [x] Settings configuration
- [x] Onboarding flow
- [x] Auto-pause on branch change
- [x] Auto-pause on window focus loss
- [x] Auto-pause on project switch
- [x] Auto-pause on system sleep

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Acknowledgments

- Inspired by [Darkyen's Time Tracker](https://github.com/Darkyenus/DarkyenusTimeTracker) plugin
- Uses JetBrains IntelliJ Platform SDK
- Integrates with Jira Cloud REST API v2

## Support

- **Issues:** [GitHub Issues](https://github.com/artusm/jetbrains-plugin-jira-worklog/issues)
- **Discussions:** [GitHub Discussions](https://github.com/artusm/jetbrains-plugin-jira-worklog/discussions)

---

Made with ❤️ for developers who track time in Jira

[template]: https://github.com/JetBrains/intellij-platform-plugin-template
[docs:plugin-description]: https://plugins.jetbrains.com/docs/intellij/plugin-user-experience.html#plugin-description-and-presentation
