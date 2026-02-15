# AI Development System Guide

## Overview

This document describes the automated AI-driven development system for the Zakum plugin suite. The system uses a multi-model approach combining Claude Sonnet 4.5 for analysis and GPT-4.1 for code generation.

## System Architecture

### Budget Model
- **Upfront Investment**: $30 (initial 10-day sprint)
- **Ongoing Cost**: $0.50/day (~$15/month)
- **Initial Sprint**: Every 30 minutes for 10 days (461 PRs)
- **Ongoing Mode**: Every 3.5 hours (8-9 PRs/day)

### Workflow

```
1. Parse docs/PLUGIN-TASKS.md → Extract next task
2. Claude Analysis → Generate technical specification
3. GPT-4 Coding → Generate implementation
4. Validation → Build & test code
5. PR Creation → Submit to GitHub
6. Discord Notification → Alert team
7. Feedback Loop → Learn from merged PRs
```

## Components

### 1. Task Definition (`docs/PLUGIN-TASKS.md`)
Tasks are defined using markdown with special comment markers:

```markdown
## Task: Feature Name
<!-- TASK_ID: unique_id_001 -->
<!-- TASK_PRIORITY: high|medium|low -->
<!-- TASK_MODULE: zakum-core -->
<!-- TASK_LANGUAGE: java -->

### Requirements
...task details...
```

### 2. Python Orchestrator (`tools/ai-orchestrator/`)

#### Core Scripts
- **parse_docs.py**: Extracts tasks from markdown
- **claude_analyzer.py**: Generates specifications using Claude
- **chatgpt_coder.py**: Generates code using GPT-4.1
- **validator.py**: Runs build validation
- **orchestrator.py**: Controls the workflow
- **discord_notifier.py**: Sends status updates
- **feedback_tracker.py**: Tracks successful patterns
- **config.py**: Manages configuration

### 3. GitHub Actions Workflow

The workflow runs automatically on schedule:

**Initial Sprint (Days 1-10)**:
- Frequency: Every 30 minutes
- Budget: $30 total
- Expected: ~461 PRs

**Ongoing Mode (After Day 10)**:
- Frequency: Every 3.5 hours
- Budget: $0.50/day
- Expected: 8-9 PRs/day

### 4. Cost Management

The system tracks spending in `token_budget.json`:
- Monitors API token usage
- Enforces budget limits
- Automatically transitions phases
- Alerts when approaching limits

## Configuration

### Required Environment Variables

```bash
# API Keys
ANTHROPIC_API_KEY=your_claude_api_key
OPENAI_API_KEY=your_openai_api_key

# GitHub (automatically provided in Actions)
GITHUB_TOKEN=${{ secrets.GITHUB_TOKEN }}

# Discord (optional)
DISCORD_WEBHOOK_URL=your_webhook_url
```

### Budget Configuration

Edit `token_budget.json` to adjust:
- Budget limits
- Phase transition dates
- Spending thresholds

## Adding New Tasks

1. Open `docs/PLUGIN-TASKS.md`
2. Add a new task section with metadata:
   ```markdown
   ## Task: Your Task Name
   <!-- TASK_ID: unique_id -->
   <!-- TASK_PRIORITY: high|medium|low -->
   <!-- TASK_MODULE: module-name -->
   <!-- TASK_LANGUAGE: java -->
   ```
3. Describe requirements clearly
4. Save the file
5. The system will automatically pick up the task

## Monitoring

### Check System Status
```bash
# View budget status
cat token_budget.json

# View completed tasks
cat tools/ai-orchestrator/completed_tasks.json

# View recent logs
tail -f tools/ai-orchestrator/logs/orchestrator.log
```

### Discord Notifications

Each PR generation sends a Discord message with:
- Task name and ID
- Specification summary
- Code changes summary
- Build status
- GitHub PR link

## Feedback Loop

The system learns from merged PRs:
1. Tracks which PRs get merged
2. Analyzes successful patterns
3. Stores patterns in `merged_prs.json`
4. Uses patterns to improve future generations

## Troubleshooting

### System Not Running
- Check GitHub Actions status
- Verify environment variables are set
- Check budget hasn't been exceeded

### PRs Failing Build
- Review validator.py logs
- Check Java 21 compatibility
- Verify Gradle build configuration

### Budget Exceeded
- System automatically stops generating PRs
- Increase budget in `token_budget.json`
- Or wait for next day (ongoing mode)

## Best Practices

1. **Write Clear Tasks**: Be specific in requirements
2. **Set Priorities**: Use high/medium/low appropriately
3. **Monitor Budget**: Check spending regularly
4. **Review PRs**: Human review is still important
5. **Update Patterns**: Feed back successful patterns

## Maintenance

### Daily
- Check Discord notifications
- Review generated PRs
- Merge successful PRs

### Weekly
- Review budget spending
- Update task priorities
- Add new tasks as needed

### Monthly
- Analyze PR success rate
- Adjust prompts if needed
- Review and optimize costs

## Expected Results

### Year 1 Projection
- **Initial Sprint**: 461 PRs (10 days)
- **Ongoing**: 2,737 PRs (365 - 10 days)
- **Total**: 3,198 PRs
- **Cost**: $212 ($30 + $182.50)

### Success Metrics
- PRs generated per day
- Build success rate
- Merge rate
- Cost per successful PR
- Task completion rate

## Support

For issues or questions:
1. Check this documentation
2. Review logs in `tools/ai-orchestrator/logs/`
3. Check GitHub Actions workflow runs
4. Review Discord notifications
