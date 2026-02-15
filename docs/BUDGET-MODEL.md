# Budget Model Documentation

## Overview

This document describes the simplified budget model for AI automation with cumulative spending tracking, $5 notifications, and $25 authorization gates.

## Architecture

### Components

1. **Budget Tracking System** (`cumulative_budget.json`)
   - Tracks total spending across all time
   - No monthly resets
   - Persistent across all executions

2. **Orchestrator** (`automation/orchestrator.py`)
   - Main coordination script
   - Checks budget before execution
   - Enforces authorization gates
   - Updates spending data

3. **Discord Notifier** (`automation/discord_notifier.py`)
   - Sends milestone notifications every $5
   - Sends authorization gate alerts at $25 intervals
   - Simple, clear message format

4. **GitHub Actions Workflow** (`.github/workflows/ai-automation-budget.yml`)
   - Disabled by default (manual trigger only)
   - Checks budget before running
   - Enforces authorization gates
   - Handles re-authorization

## Budget Model

### Model Stack
- **Claude Sonnet 4.5**: Analysis and high-quality reasoning
- **GPT-4o Mini**: Code generation and fast processing

### Pricing
```
Per Execution Cost: $0.055
- Claude Sonnet 4.5 (analysis): ~$0.025
- GPT-4o Mini (code): ~$0.030

At $25 authorization gate: 454 executions (25 ÷ 0.055)
```

### Notification Intervals

**Every $5 Spent:**
```
💰 $5 Spent | Total: $5.00

📊 Status:
├─ PRs Generated: 91
├─ Tokens Used: 910,000
├─ Cost per PR: $0.055
├─ Next Notification: $10.00
└─ Next Authorization Gate: $25.00
```

**At $25 Authorization Gate:**
```
⛔ AUTHORIZATION GATE REACHED: $25.00 Spent

📊 Summary:
├─ Total Spent: $25.00
├─ PRs Generated: 454
├─ Cost per PR: $0.055
├─ Tokens Used: 4,540,000

⚠️ Action Required:
Re-authorize next $25 block by:
1. React with ✅ in Discord
2. Click "Run workflow" in GitHub Actions
3. Reply "@copilot authorize" in Discord

🔄 Ready to continue when authorized
```

## Authorization Flow

### Initial Setup

1. **Workflow is PAUSED by default**
   - No automatic execution on schedule
   - Must be manually triggered

2. **First authorization required**
   ```bash
   # Check current status
   python automation/orchestrator.py
   
   # Authorize first $25 block
   python automation/orchestrator.py --authorize
   ```

3. **Run via GitHub Actions**
   - Go to Actions tab in GitHub
   - Select "AI Automation with Budget Tracking"
   - Click "Run workflow"
   - Choose action: "run"

### Execution Flow

```
┌─────────────────────────────────────┐
│ User: Click "Run workflow"          │
└──────────────┬──────────────────────┘
               │
               ▼
┌─────────────────────────────────────┐
│ System: Check cumulative_budget.json│
└──────────────┬──────────────────────┘
               │
               ├─ If < $25 spent: Execute normally
               ├─ If = $25 spent: Halt with Discord alert
               └─ If > $25 && authorized: Continue to next block
               │
               ▼
┌─────────────────────────────────────┐
│ Discord: "⛔ $25 reached"           │
└──────────────┬──────────────────────┘
               │
               ▼
┌─────────────────────────────────────┐
│ User: React ✅ or "authorize" action│
└──────────────┬──────────────────────┘
               │
               ▼
┌─────────────────────────────────────┐
│ System: Authorize next $25 block    │
└──────────────┬──────────────────────┘
               │
               ▼
┌─────────────────────────────────────┐
│ User: Click "Run workflow" again    │
└──────────────┬──────────────────────┘
               │
               ▼
┌─────────────────────────────────────┐
│ System: Execute next 454 PRs ($50)  │
└─────────────────────────────────────┘
```

### Re-Authorization Options

#### Option 1: Via GitHub Actions
1. Go to Actions tab
2. Select workflow
3. Click "Run workflow"
4. Choose action: "authorize"
5. Click "Run workflow" again with action: "run"

#### Option 2: Via Command Line (Local)
```bash
# Authorize next block
python automation/orchestrator.py --authorize

# Commit changes
git add cumulative_budget.json
git commit -m "chore: authorize next $25 block"
git push
```

#### Option 3: Via Discord (Future Enhancement)
React with ✅ to authorization gate message

## Budget Tracking

### Cumulative Model

The system tracks spending **cumulatively** with no monthly resets:

```json
{
  "total_spent": 0.00,
  "total_prs_generated": 0,
  "total_tokens_used": 0,
  "authorization_blocks": [],
  "next_authorization_gate": 25.00,
  "workflow_status": "PAUSED",
  "last_updated": "2026-02-15T00:00:00Z"
}
```

### Timeline Example

```
$0-$5:    Generate 91 PRs → Discord notification
$5-$10:   Generate 91 PRs → Discord notification
$10-$15:  Generate 91 PRs → Discord notification
$15-$20:  Generate 91 PRs → Discord notification
$20-$25:  Generate 91 PRs → Discord notification + PAUSE
          ↓
          Requires manual re-authorization
          ↓
$25-$30:  Generate 91 PRs → Discord notification
$30-$35:  Generate 91 PRs → Discord notification
...and so on
```

### Authorization History

Each authorization is logged:

```json
{
  "authorization_blocks": [
    {
      "gate_amount": 25.00,
      "authorized_at": "2026-02-15T10:30:00Z",
      "spent_at_authorization": 25.00
    },
    {
      "gate_amount": 50.00,
      "authorized_at": "2026-02-16T14:20:00Z",
      "spent_at_authorization": 50.00
    }
  ]
}
```

## Configuration

### Environment Variables

Set in GitHub Actions Secrets:

```
ANTHROPIC_API_KEY      # Claude API key
OPENAI_API_KEY         # OpenAI API key
DISCORD_WEBHOOK_URL    # Discord webhook for notifications
```

### Budget Settings

Edit `automation/config.py`:

```python
# Authorization
AUTHORIZATION_GATE = 25.00      # Pause every $25
NOTIFICATION_INTERVAL = 5.00    # Alert every $5

# Models
CLAUDE_MODEL = "claude-3-5-sonnet-20241022"
OPENAI_MODEL = "gpt-4o-mini"

# No monthly reset
MONTHLY_RESET = False
```

## Monitoring

### Check Current Status

```bash
python automation/orchestrator.py
```

Output:
```
============================================================
AI Automation Orchestrator - Budget Tracking System
============================================================

Current budget status:
  Total Spent: $15.30
  PRs Generated: 278
  Next Gate: $25.00
  Status: RUNNING

✅ Authorization OK - can execute
```

### View Budget File

```bash
cat cumulative_budget.json
```

### Check Authorization History

```bash
python -c "import json; print(json.dumps(json.load(open('cumulative_budget.json'))['authorization_blocks'], indent=2))"
```

## Troubleshooting

### Workflow Not Running

**Problem**: Workflow doesn't execute when triggered

**Solution**: Check if authorization gate is reached
```bash
python automation/orchestrator.py
```

If gate is reached, authorize next block:
```bash
python automation/orchestrator.py --authorize
```

### No Discord Notifications

**Problem**: Not receiving notifications

**Solution**: Check webhook configuration
```bash
# Test locally
export DISCORD_WEBHOOK_URL="your_webhook_url"
python automation/discord_notifier.py
```

Verify GitHub Secret is set:
- Go to Settings → Secrets and variables → Actions
- Check that `DISCORD_WEBHOOK_URL` exists

### Budget Not Updating

**Problem**: Budget file not updating after execution

**Solution**: Check file permissions and git configuration
```bash
# Verify file exists and is writable
ls -la cumulative_budget.json

# Check git status
git status cumulative_budget.json
```

## Security Considerations

1. **API Keys**: Never commit API keys to the repository. Always use GitHub Secrets.

2. **Budget File**: The `cumulative_budget.json` file should be committed to track spending across team members.

3. **Authorization**: Only repository admins should authorize spending blocks.

4. **Discord Webhook**: Keep webhook URL secret to prevent spam.

## Cost Optimization

### Reducing Costs

1. **Batch Operations**: Group multiple PRs together when possible
2. **Smaller Models**: Use GPT-4o Mini for simpler tasks
3. **Token Limits**: Set maximum token limits per execution
4. **Caching**: Cache frequently used analysis results

### Expected Costs

```
Per $25 Block:
- 454 PR generations
- ~4.5M tokens
- ~20-25 days of moderate usage (20 PRs/day)

Per Month (30 days):
- ~600 PRs @ 20/day
- ~$33 total cost
- 2 authorization gates required
```

## FAQ

**Q: Can I change the authorization gate amount?**
A: Yes, edit `AUTHORIZATION_GATE` in `automation/config.py`

**Q: How do I reset the budget?**
A: Edit `cumulative_budget.json` and set `total_spent: 0.00`. **Not recommended** as it loses history.

**Q: Can I disable notifications?**
A: Remove the `DISCORD_WEBHOOK_URL` secret, but authorization gates will still enforce pauses.

**Q: How many re-authorizations can I do?**
A: Unlimited. Each authorization adds another $25 to the budget.

**Q: Can the workflow run on a schedule?**
A: Not by default. It's disabled to prevent unintended spending. You must manually trigger it.

**Q: What happens if I hit the gate mid-execution?**
A: The current execution completes, then the system pauses. Next trigger will require authorization.

## Support

For issues or questions:
1. Check this documentation
2. Review `cumulative_budget.json` for current status
3. Run `python automation/orchestrator.py` to diagnose
4. Check GitHub Actions logs for errors
