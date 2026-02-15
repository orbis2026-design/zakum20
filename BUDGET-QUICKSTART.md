# AI Automation Budget Model - Quick Start

This repository now includes an AI automation system with budget tracking, $5 notifications, and $25 authorization gates.

## 🚀 Quick Start

### 1. Initial Setup

The system starts in a **PAUSED** state. No automatic execution until you authorize it.

```bash
# Install dependencies
pip install -r automation/requirements.txt

# Check initial status
python automation/orchestrator.py
```

### 2. Configure Secrets

Set up GitHub Actions Secrets:
- `ANTHROPIC_API_KEY` - Your Claude API key
- `OPENAI_API_KEY` - Your OpenAI API key  
- `DISCORD_WEBHOOK_URL` - Discord webhook for notifications

### 3. Run the Workflow

Go to **Actions** tab → **AI Automation with Budget Tracking** → **Run workflow**

Choose action:
- **check** - Check current budget status
- **authorize** - Authorize next $25 spending block
- **run** - Execute AI automation (requires budget authorization)

## 📊 Budget Model Overview

### Key Features

- **$5 Notifications**: Discord alert every $5 spent
- **$25 Authorization Gates**: Automatic pause, requires manual re-authorization
- **Cumulative Tracking**: No monthly resets - tracks all spending forever
- **Model Stack**: Claude Sonnet 4.5 (analysis) + GPT-4o Mini (code)
- **Cost per PR**: ~$0.055
- **PRs per gate**: ~454 executions before $25 pause

### Timeline Example

```
$0-$5     → 91 PRs → Discord notification
$5-$10    → 91 PRs → Discord notification
$10-$15   → 91 PRs → Discord notification
$15-$20   → 91 PRs → Discord notification
$20-$25   → 91 PRs → Discord notification + PAUSE ⛔

→ Authorization required to continue

$25-$50   → Same pattern repeats after authorization
```

## 🔐 Authorization Flow

1. **Hit $25 gate** → Workflow pauses, Discord alert sent
2. **User authorizes** via GitHub Actions (select "authorize" action)
3. **Resume execution** by running workflow with "run" action
4. **Repeat** - unlimited authorizations available

## 📁 Key Files

- `cumulative_budget.json` - Budget tracking (committed to repo)
- `automation/config.py` - Model and authorization settings
- `automation/orchestrator.py` - Main orchestration logic
- `automation/discord_notifier.py` - Notification system
- `.github/workflows/ai-automation-budget.yml` - GitHub Actions workflow

## 🧪 Testing

```bash
# Run test suite
python tests/test_budget_model.py

# Run demonstration
python examples/budget_demo.py
```

## 📖 Full Documentation

See [docs/BUDGET-MODEL.md](docs/BUDGET-MODEL.md) for complete documentation including:
- Detailed authorization flow
- Configuration options
- Troubleshooting
- Cost optimization tips
- FAQ

## 🛠️ Local Development

```bash
# Check budget status
python automation/orchestrator.py

# Authorize next $25 block
python automation/orchestrator.py --authorize

# Test Discord notifications
python automation/discord_notifier.py
```

## 📈 Monitoring

Current budget status is always available in `cumulative_budget.json`:

```json
{
  "total_spent": 0.00,
  "total_prs_generated": 0,
  "total_tokens_used": 0,
  "authorization_blocks": [],
  "next_authorization_gate": 25.00,
  "workflow_status": "PAUSED"
}
```

## ⚙️ Configuration

Edit `automation/config.py` to customize:
- Authorization gate amount (default: $25)
- Notification interval (default: $5)
- Model selection
- Pricing

## 🎯 Success Criteria

✅ Workflow disabled/paused initially  
✅ $5 milestone notifications working  
✅ $25 authorization gates enforced  
✅ Cumulative budget tracking (no monthly resets)  
✅ Manual authorization required every $25  
✅ Discord alerts for milestones + gates  
✅ Budget file persists across months  
✅ Unlimited re-authorizations available  
✅ Simple, lean implementation  

## 🔒 Security Notes

- Never commit API keys to the repository
- Use GitHub Secrets for all credentials
- Budget file should be committed (tracks team spending)
- Only repository admins should authorize spending

## 💡 Tips

- Run demo first: `python examples/budget_demo.py`
- Test locally before using in production
- Monitor Discord for spending alerts
- Review budget file regularly
- Authorize in advance to avoid workflow interruptions

## 🐛 Troubleshooting

If workflow won't run:
```bash
python automation/orchestrator.py
```

If at authorization gate, authorize:
```bash
python automation/orchestrator.py --authorize
git add cumulative_budget.json
git commit -m "chore: authorize next $25 block"
git push
```

---

**Ready to use!** The system is now configured and waiting for your first authorization. 🚀
