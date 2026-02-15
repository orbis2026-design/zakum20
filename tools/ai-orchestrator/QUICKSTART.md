# Quick Start Guide

This guide helps you get the AI Development Automation system up and running.

## Prerequisites

Before starting, ensure you have:

1. **API Keys**:
   - Anthropic Claude API key (get from https://console.anthropic.com/)
   - OpenAI API key (get from https://platform.openai.com/api-keys)
   - Discord webhook URL (optional, for notifications)

2. **GitHub Repository Access**:
   - Admin access to the repository
   - Ability to add repository secrets
   - Permissions to create workflows

3. **Development Environment** (for local testing):
   - Python 3.9 or higher
   - Java 21
   - GitHub CLI (`gh`) tool

## Setup Steps

### 1. Configure Repository Secrets

Add these secrets to your GitHub repository:

1. Go to **Repository → Settings → Secrets and variables → Actions**
2. Click **New repository secret**
3. Add each of the following:

| Secret Name | Description | Example |
|------------|-------------|---------|
| `ANTHROPIC_API_KEY` | Your Claude API key | `sk-ant-...` |
| `OPENAI_API_KEY` | Your OpenAI API key | `sk-...` |
| `DISCORD_WEBHOOK_URL` | Discord webhook for notifications | `https://discord.com/api/webhooks/...` |

### 2. Enable GitHub Actions

1. Go to **Repository → Actions**
2. If prompted, enable GitHub Actions for the repository
3. Verify that the following workflows appear:
   - "AI Development Automation"
   - "AI Feedback Collection"

### 3. Test the System

#### Option A: Manual Trigger (Recommended for First Run)

1. Go to **Actions → AI Development Automation**
2. Click **Run workflow**
3. Set parameters:
   - **max_tasks**: `1`
   - **task_id**: (leave empty)
   - **dry_run**: `true`
4. Click **Run workflow**
5. Monitor the workflow execution

#### Option B: Local Testing

```bash
# Navigate to orchestrator directory
cd tools/ai-orchestrator

# Install dependencies
pip install -r requirements.txt

# Set environment variables
export ANTHROPIC_API_KEY="your-claude-key"
export OPENAI_API_KEY="your-openai-key"
export DISCORD_WEBHOOK_URL="your-webhook-url"

# Run in dry-run mode (no PRs created)
python orchestrator.py --project-root ../.. --dry-run --max-tasks 1
```

### 4. Monitor First Run

After triggering the workflow:

1. Check workflow logs in GitHub Actions
2. Look for Discord notification (if enabled)
3. Verify no errors occurred
4. Review any PRs created

### 5. Review Generated PR

If a PR was created:

1. Review the generated code
2. Check compilation status
3. Review Folia compatibility notes
4. Check validation results
5. Merge if satisfactory

## Usage

### Adding New Tasks

1. Edit `docs/PLUGIN-TASKS.md`
2. Add a new task using the template:

```markdown
## Task: Your Feature Name
<!-- TASK_ID: module_NNN -->
<!-- TASK_PRIORITY: high|medium|low -->
<!-- TASK_MODULE: target-module-name -->
<!-- TASK_LANGUAGE: java -->
<!-- TASK_STATUS: pending -->

Detailed description...
```

3. Commit and push to main branch
4. Wait for next scheduled run or trigger manually

### Manual Workflow Trigger

For immediate processing:

1. Go to **Actions → AI Development Automation**
2. Click **Run workflow**
3. Configure options:
   - **max_tasks**: Number of tasks to process (default: 1)
   - **task_id**: Specific task ID to process (optional)
   - **dry_run**: Set to `false` for actual PR creation
4. Click **Run workflow**

### Processing Specific Task

To process a specific task by ID:

1. Go to **Actions → AI Development Automation**
2. Click **Run workflow**
3. Enter task ID in **task_id** field (e.g., `core_001`)
4. Click **Run workflow**

### Monitoring Progress

#### GitHub Actions
- Go to **Actions** tab
- Click on a workflow run to view logs
- Check each step for success/failure

#### Discord Notifications
If enabled, you'll receive notifications for:
- New PR created
- Validation results
- PR merged
- Processing errors
- Run summary

#### Feedback Data
Merged PRs store feedback in:
- `tools/ai-orchestrator/feedback/`
- JSON files named by task ID
- Used to improve future generations

## Scheduled Runs

The system automatically runs every hour to:
1. Check for new pending tasks
2. Process up to 1 task per run
3. Create PRs for completed tasks
4. Send notifications

To change schedule:
1. Edit `.github/workflows/ai-development.yml`
2. Modify the cron expression:
```yaml
schedule:
  - cron: '0 * * * *'  # Every hour
  # Examples:
  # - cron: '0 */2 * * *'  # Every 2 hours
  # - cron: '0 9 * * *'    # Daily at 9 AM
  # - cron: '0 9 * * 1-5'  # Weekdays at 9 AM
```

## Troubleshooting

### Workflow Fails Immediately

**Check:**
- All required secrets are set
- Secrets have correct values
- API keys are valid

**Solution:**
- Verify secrets in repository settings
- Test API keys locally
- Check API rate limits

### Compilation Errors

**Check:**
- Generated code in PR
- Validation results in PR description
- Workflow logs for detailed errors

**Solution:**
- Review compilation errors
- Update task description with more context
- Adjust prompts in analyzer/coder scripts

### No PR Created

**Check:**
- Task status (might already be in-progress)
- Validation passed
- Git push succeeded

**Solution:**
- Check workflow logs
- Verify GitHub token permissions
- Ensure branch doesn't already exist

### Discord Notifications Not Received

**Check:**
- `DISCORD_WEBHOOK_URL` secret is set
- Webhook URL is valid
- Discord server settings

**Solution:**
- Test webhook URL manually
- Regenerate webhook if expired
- Check Discord server permissions

### Folia Compatibility Issues

**Check:**
- Validation report in PR
- Specific compatibility warnings

**Solution:**
- Update task description with Folia requirements
- Review generated code
- Manually fix and update PR

## Best Practices

### Task Definitions

1. **Be Specific**: Provide detailed requirements
2. **Include Examples**: Show expected behavior
3. **Document Constraints**: Mention performance, threading, etc.
4. **Reference Existing Code**: Point to similar implementations

### Priority Management

- **High**: Critical features, bug fixes
- **Medium**: Nice-to-have features, improvements
- **Low**: Optional enhancements, experiments

### Review Process

1. **Automated Checks**: Review validation results
2. **Code Review**: Check generated code quality
3. **Testing**: Test functionality manually
4. **Integration**: Ensure compatibility with existing code
5. **Merge**: Merge if satisfactory

### Maintenance

1. **Monitor Feedback**: Review stored feedback data
2. **Update Prompts**: Improve analyzer/coder prompts based on results
3. **Adjust Configuration**: Tune settings in `config.yaml`
4. **Clean Up**: Archive completed tasks periodically

## Advanced Configuration

### Customizing Prompts

Edit these files to improve AI output:
- `claude_analyzer.py`: Modify system prompt and analysis prompt
- `chatgpt_coder.py`: Modify system prompt and generation prompt

### Adjusting Validation

Edit `validator.py` to:
- Add custom validation rules
- Modify Folia compatibility checks
- Adjust code quality metrics

### Changing Models

Edit `config.yaml`:
```yaml
claude:
  model: "claude-3-5-sonnet-20241022"  # Change model

chatgpt:
  model: "gpt-4-turbo-preview"  # Change model
```

Available models:
- Claude: `claude-3-5-sonnet-20241022`, `claude-3-opus-20240229`
- ChatGPT: `gpt-4-turbo-preview`, `gpt-4`, `gpt-3.5-turbo`

## Support

For issues or questions:
1. Check workflow logs in GitHub Actions
2. Review system documentation in `tools/ai-orchestrator/SYSTEM-README.md`
3. Examine generated PR descriptions for details
4. Test components individually using local scripts

## Next Steps

Once the system is working:

1. **Add More Tasks**: Define additional features in `PLUGIN-TASKS.md`
2. **Monitor Quality**: Review generated PRs regularly
3. **Tune Prompts**: Improve AI output based on feedback
4. **Scale Up**: Increase `max_tasks` for batch processing
5. **Iterate**: Learn from merged PRs to improve future generations

---

**Happy Automating! 🤖**
