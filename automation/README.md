# AI Orchestration System

Production-ready multi-model AI automation system for sustainable code generation.

## Quick Start

### Setup
```bash
cd automation
pip install -r requirements.txt
```

### Environment Variables
```bash
export DISCORD_WEBHOOK_URL="https://discord.com/api/webhooks/..."
export ANTHROPIC_API_KEY="sk-ant-..."
export OPENAI_API_KEY="sk-..."
```

### Check Budget Status
```bash
python orchestrator.py
```

### Run Manual Execution
```bash
python orchestrator.py "Your task description here"
```

## System Components

### 1. Configuration (`config.py`)
- Model pricing configuration
- Budget limits and thresholds
- Token usage estimates
- Cost calculations

### 2. Orchestrator (`orchestrator.py`)
- Budget tracking and enforcement
- Model execution coordination
- Cost calculation and logging
- Milestone detection

### 3. Discord Notifier (`discord_notifier.py`)
- Execution notifications
- Milestone alerts with progress bars
- Budget status updates
- Error reporting

### 4. Budget Tracker (`../token_budget.json`)
- Real-time spending data
- Token usage statistics
- Execution history
- Milestone records

## Budget Configuration

### Monthly Budget: $15.00
- Milestone notifications at: $5, $10, $15
- Estimated cost per execution: $0.055
- Maximum monthly executions: 273
- Expected monthly PRs: 273

### Model Stack
- **Analysis**: Claude Sonnet 4.5 (`claude-3-5-sonnet-20241022`)
- **Code Generation**: GPT-4o Mini (`gpt-4o-mini`)

## Usage Examples

### Check Budget Status
```bash
$ python orchestrator.py
============================================================
AI Orchestrator - Sonnet 4.5 + GPT-4o Mini
Monthly Budget: $15.0
Estimated cost per execution: $0.0530
============================================================

Current Status:
  Month: 2026-02
  Spent: $5.02
  Remaining: $9.98
  Executions: 91
  PRs: 91
  Milestones: [5.0]

Budget available. Remaining: $9.98
```

### Execute Task
```bash
$ python orchestrator.py "Add user authentication to API"
Starting execution: Budget available. Remaining: $9.98
[CLAUDE] Analyzing task: Add user authentication to API...
[GPT-4o-Mini] Generating code from specification...
Execution complete. Cost: $0.0532
Total spent: $5.07 / $15.00
Remaining: $9.93
✅ Execution successful!
```

### Test Discord Notifications
```bash
$ python discord_notifier.py
Testing Discord notifications...
[Sends test milestone notification]
```

## Cost Tracking

The system automatically tracks:
- Total spent per month
- Number of executions
- PRs generated
- Token usage (input/output per model)
- Milestone achievements
- Historical data

### Token Budget File Structure
```json
{
  "version": "1.0",
  "current_month": "2026-02",
  "total_spent": 5.02,
  "executions": 91,
  "prs_generated": 91,
  "tokens_used": {
    "claude_input": 182000,
    "claude_output": 136500,
    "openai_input": 227500,
    "openai_output": 364000
  },
  "milestones_reached": [5.00],
  "last_reset": "2026-02-01T00:00:00Z",
  "history": []
}
```

## Milestone Notifications

### $5 Milestone
- ~91 PRs generated
- 33% of budget used
- Discord alert with progress bar
- Execution continues

### $10 Milestone
- ~182 PRs generated
- 67% of budget used
- Discord alert with progress bar
- Execution continues

### $15 Milestone
- ~273 PRs generated
- 100% of budget used
- Discord alert with monthly recap
- Execution continues for overflow

### Budget Exceeded
- Execution paused
- Discord alert sent
- Automatic reset on 1st of month

## GitHub Actions Integration

The system integrates with GitHub Actions for automated execution:

### Workflow: `.github/workflows/ai-orchestration.yml`
- Runs every 2.5-3 hours
- Checks budget before execution
- Executes AI orchestration
- Commits budget updates
- Creates PRs automatically

### Secrets Required
- `DISCORD_WEBHOOK_URL`: Discord webhook for notifications
- `ANTHROPIC_API_KEY`: Claude API key
- `OPENAI_API_KEY`: OpenAI API key
- `GITHUB_TOKEN`: Automatic (for PR creation)

## Documentation

Comprehensive documentation available in `docs/`:
- **COST-TRACKING.md**: Detailed cost tracking guide
- **SONNET-MINI-GUIDE.md**: Model selection and optimization

## Troubleshooting

### Budget Not Updating
Check file permissions and ensure GitHub Actions can commit changes.

### Notifications Not Sending
Verify `DISCORD_WEBHOOK_URL` environment variable is set correctly.

### Execution Blocked
Run `python orchestrator.py` to check budget status and remaining funds.

### Costs Higher Than Expected
Review `token_budget.json` for actual token usage and compare with estimates.

## Monthly Reset

Budget automatically resets on the 1st of each month:
1. Archives previous month data
2. Sends monthly recap notification
3. Resets counters to zero
4. Updates current month

## Support

For issues:
1. Check workflow logs in GitHub Actions
2. Review `token_budget.json` for discrepancies
3. Test Discord notifications manually
4. Check environment variables

## Version

- **System Version**: 1.0
- **Last Updated**: 2026-02-15
- **Budget Cycle**: Monthly
- **Status**: Production Ready

---

**Cost per PR**: $0.055  
**Monthly Output**: 273 PRs  
**Annual Output**: 3,276 PRs  
**Sustainability**: Indefinite at $15/month
