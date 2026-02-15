# Multi-Model AI Development Automation

Complete GitHub Actions + Python automation system for continuous AI-driven development using Claude and ChatGPT.

## 🎯 Overview

This system enables automated plugin development with:
- **Claude** for analyzing requirements and producing detailed specifications
- **ChatGPT** for generating Java implementation from specs
- **Automated validation** ensuring code compiles and meets standards
- **Discord notifications** for all PR creations and status updates
- **Feedback loop** tracking merged PRs to improve future generations

## 🏗️ Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    GitHub Actions Workflow                   │
│  (Runs hourly or on-demand)                                 │
└─────────────────────┬───────────────────────────────────────┘
                      │
                      v
┌─────────────────────────────────────────────────────────────┐
│                   Python Orchestrator                        │
│                                                              │
│  ┌──────────────┐   ┌──────────────┐   ┌──────────────┐   │
│  │ Parse Docs   │ → │   Claude     │ → │   ChatGPT    │   │
│  │ (Tasks)      │   │ (Analysis)   │   │ (Code Gen)   │   │
│  └──────────────┘   └──────────────┘   └──────────────┘   │
│                                                              │
│  ┌──────────────┐   ┌──────────────┐   ┌──────────────┐   │
│  │  Validator   │ → │  Git/PR      │ → │   Discord    │   │
│  │ (Compile)    │   │ (Create)     │   │ (Notify)     │   │
│  └──────────────┘   └──────────────┘   └──────────────┘   │
└─────────────────────────────────────────────────────────────┘
                      │
                      v
┌─────────────────────────────────────────────────────────────┐
│                   Feedback Collection                        │
│  (Tracks merged PRs for continuous improvement)             │
└─────────────────────────────────────────────────────────────┘
```

## 📁 Project Structure

```
tools/ai-orchestrator/
├── README.md                  # This file
├── requirements.txt           # Python dependencies
├── config.yaml               # Configuration template
├── orchestrator.py           # Main orchestration logic
├── parse_docs.py             # Task parsing from markdown
├── claude_analyzer.py        # Claude API integration
├── chatgpt_coder.py         # ChatGPT API integration
├── validator.py             # Code validation
├── discord_notifier.py      # Discord notifications
└── feedback/                # Stored feedback from merged PRs

.github/workflows/
├── ai-development.yml       # Main workflow (hourly + on-demand)
└── ai-feedback.yml          # Feedback collection on PR merge

docs/
└── PLUGIN-TASKS.md          # Task definitions
```

## 🚀 Setup Instructions

### 1. Prerequisites

- Python 3.9+
- Java 21 (for compilation validation)
- GitHub CLI (`gh`) installed
- API keys for:
  - Anthropic Claude API
  - OpenAI ChatGPT API
  - Discord webhook (optional)

### 2. Install Dependencies

```bash
cd tools/ai-orchestrator
pip install -r requirements.txt
```

### 3. Configure Secrets

Add the following secrets to your GitHub repository:
- `ANTHROPIC_API_KEY` - Your Claude API key
- `OPENAI_API_KEY` - Your OpenAI API key
- `DISCORD_WEBHOOK_URL` - Discord webhook URL (optional)

Go to: **Repository → Settings → Secrets and variables → Actions → New repository secret**

### 4. Configuration

The `config.yaml` file contains all configuration options. Environment variables are automatically substituted:

```yaml
claude:
  api_key: "${ANTHROPIC_API_KEY}"
  model: "claude-3-5-sonnet-20241022"

chatgpt:
  api_key: "${OPENAI_API_KEY}"
  model: "gpt-4-turbo-preview"

discord:
  webhook_url: "${DISCORD_WEBHOOK_URL}"
  enabled: true
```

## 📝 Defining Tasks

Tasks are defined in `docs/PLUGIN-TASKS.md` using a structured format:

```markdown
## Task: [Feature Name]
<!-- TASK_ID: unique_id -->
<!-- TASK_PRIORITY: high|medium|low -->
<!-- TASK_MODULE: zakum-core|orbis-gui|etc -->
<!-- TASK_LANGUAGE: java -->
<!-- TASK_STATUS: pending -->

Detailed description of what needs to be implemented, including:
- Functionality requirements
- Folia/Spigot compatibility notes
- Performance considerations
```

### Task Metadata

- **TASK_ID**: Unique identifier (e.g., `core_001`)
- **TASK_PRIORITY**: `high`, `medium`, or `low` (determines processing order)
- **TASK_MODULE**: Target module name
- **TASK_LANGUAGE**: Programming language (`java`)
- **TASK_STATUS**: `pending`, `in-progress`, `completed`, `blocked`, or `cancelled`

## 🤖 Running the System

### Automatic (Scheduled)

The workflow runs automatically every hour to process pending tasks.

### Manual Trigger

Go to **Actions → AI Development Automation → Run workflow**

Options:
- **max_tasks**: Number of tasks to process (default: 1)
- **task_id**: Specific task to process (optional)
- **dry_run**: Test mode without creating PRs (default: false)

### Local Testing

```bash
cd tools/ai-orchestrator

# Process one pending task (dry run)
python orchestrator.py --project-root ../.. --dry-run

# Process specific task
python orchestrator.py --project-root ../.. --task-id core_001

# Process multiple tasks
python orchestrator.py --project-root ../.. --max-tasks 3
```

## 🔄 Workflow Process

1. **Parse Documentation** - Extract pending tasks from `PLUGIN-TASKS.md`
2. **Analyze with Claude** - Generate detailed technical specification
3. **Generate with ChatGPT** - Create Java implementation
4. **Create Branch** - Create new Git branch (`ai-gen/{task_id}`)
5. **Apply Changes** - Write generated files to project
6. **Validate** - Compile code and check standards
7. **Create PR** - Push branch and create pull request
8. **Notify Discord** - Send detailed notification
9. **Update Status** - Mark task as `in-progress`

## 📊 Validation

The validator checks:

### ✅ Compilation
- Runs Gradle compilation for the module
- Reports errors and warnings

### ✅ Folia Compatibility
- Detects incompatible scheduler usage
- Identifies thread-safety issues
- Checks for problematic patterns

### ✅ Code Quality
- Checks for proper logging usage
- Detects empty catch blocks
- Identifies potential issues

### ✅ Plugin Descriptor
- Validates `plugin.yml` structure
- Ensures required fields present
- Checks API version compatibility

## 🔁 Feedback System

When a PR is merged:

1. **Feedback Collection** workflow triggers
2. PR data is stored in `feedback/` directory
3. Task status updated to `completed` in documentation
4. Discord notification sent
5. Data used to improve future generations

## 📢 Discord Notifications

Notifications are sent for:
- **PR Creation** - When new PR is opened
- **Validation Results** - Compilation and compatibility status
- **PR Merge** - When PR is successfully merged
- **Errors** - If processing fails
- **Summary** - After batch processing

### Example Notification

```
🤖 New AI-Generated PR: Implement Player Data Caching Layer

Task ID: core_001
Module: zakum-core
Branch: ai-gen/core_001
Priority: 🔴 HIGH
Files Changed: 5
Compilation: ✅ success
```

## 🛠️ Components

### parse_docs.py
Extracts tasks from markdown documentation.

**Features:**
- Parses task metadata from HTML comments
- Filters by status and priority
- Updates task status programmatically

### claude_analyzer.py
Uses Claude API to analyze requirements.

**Output:**
- Detailed technical specification
- Implementation notes
- Test strategy
- Risk assessment

### chatgpt_coder.py
Uses ChatGPT API to generate code.

**Output:**
- Complete Java files with paths
- Build configuration changes
- Plugin descriptor updates
- Configuration file changes

### validator.py
Validates generated code.

**Checks:**
- Compilation success
- Folia/Spigot compatibility
- Code quality metrics
- Plugin descriptor validity

### discord_notifier.py
Sends Discord webhook notifications.

**Notification Types:**
- PR creation
- Validation results
- PR merge
- Error alerts
- Processing summary

### orchestrator.py
Main controller coordinating all components.

**Features:**
- Multi-stage processing pipeline
- Error handling and recovery
- Git branch management
- PR creation automation
- Feedback storage

## 🔐 Security

- API keys stored as GitHub secrets
- No credentials in code or configuration
- Environment variable substitution
- Automated secret rotation recommended

## 📈 Success Metrics

Track system performance:
- **Processing Rate**: Tasks completed per day
- **Success Rate**: PRs merged vs. created
- **Validation Pass Rate**: Clean compilations
- **Feedback Quality**: Code review comments

## 🐛 Troubleshooting

### Compilation Errors
Check validation output in PR description. Common issues:
- Missing imports
- API incompatibilities
- Type mismatches

### Folia Compatibility Issues
Review Folia compatibility report. Address:
- Scheduler usage (use regionized schedulers)
- Thread-safety (avoid shared mutable state)
- Static collections (make thread-local)

### Discord Notifications Not Sent
Verify:
- `DISCORD_WEBHOOK_URL` secret is set
- Webhook URL is valid
- Discord server webhook settings

### PR Creation Failed
Check:
- GitHub token permissions (contents: write, pull-requests: write)
- Branch doesn't already exist
- GitHub CLI (`gh`) is authenticated

## 🔮 Future Enhancements

Potential improvements:
- Multi-language support (Kotlin, Groovy)
- Integration with CI/CD for automated testing
- Code review feedback incorporation
- Learning from review comments
- Automatic dependency management
- Performance benchmarking
- Security vulnerability scanning

## 📚 References

- [Claude API Documentation](https://docs.anthropic.com/)
- [OpenAI API Documentation](https://platform.openai.com/docs)
- [GitHub Actions Documentation](https://docs.github.com/en/actions)
- [Discord Webhooks Guide](https://discord.com/developers/docs/resources/webhook)
- [PaperSpigot API](https://papermc.io/javadocs/)
- [Folia Documentation](https://docs.papermc.io/folia)

## 📄 License

Part of the Zakum Suite project.

## 🤝 Contributing

This is an automated system. To improve:
1. Update task definitions in `PLUGIN-TASKS.md`
2. Enhance validation rules in `validator.py`
3. Improve prompts in `claude_analyzer.py` and `chatgpt_coder.py`
4. Adjust configuration in `config.yaml`

---

**Note**: This system requires careful monitoring initially. Review generated PRs thoroughly before merging. The feedback loop improves quality over time.
