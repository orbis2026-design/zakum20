# Cost Tracking Guide

## Overview

The AI orchestration system uses **Claude Sonnet 4.5** for analysis and **GPT-4o Mini** for code generation, with a sustainable $15/month budget and milestone notifications at $5 intervals.

## Budget Configuration

### Monthly Budget: $15.00
- **Milestone Notifications**: $5, $10, $15
- **Notification Method**: Discord webhooks
- **Reset Cycle**: Monthly (automatic)

## Cost Breakdown

### Model Pricing

#### Claude Sonnet 4.5 (`claude-3-5-sonnet-20241022`)
- **Input**: $3.00 per 1M tokens
- **Output**: $15.00 per 1M tokens
- **Typical Usage**: 2,000 input + 1,500 output tokens
- **Cost per Execution**: ~$0.0285

#### GPT-4o Mini (`gpt-4o-mini`)
- **Input**: $0.15 per 1M tokens
- **Output**: $0.60 per 1M tokens
- **Typical Usage**: 2,500 input + 4,000 output tokens
- **Cost per Execution**: ~$0.0245

### Combined Cost per Execution
```
Claude Analysis:  $0.0285
GPT-4o Code Gen:  $0.0245
──────────────────────────
Total:            $0.053
Buffer (rounded): $0.055
```

## Monthly Projections

### At $15/month Budget:
```
Maximum Executions:  273 (15 ÷ 0.055)
Daily Executions:    9.1 (273 ÷ 30)
Execution Frequency: Every 2.6 hours
Monthly PR Output:   273 PRs
Cost per PR:         $0.055
```

### Yearly Projections:
```
Annual Budget:    $180
Annual PRs:       3,276
Quarterly PRs:    819
Quarterly Cost:   $45
```

## Milestone Notifications

### $5 Milestone (1/3 Complete)
- **PRs Generated**: ~91
- **Executions**: ~91
- **Notification**: 🚀 "$5 Milestone Reached! 91 PRs Generated"
- **Status**: Continue execution
- **Progress**: 33% complete

### $10 Milestone (2/3 Complete)
- **PRs Generated**: ~182
- **Executions**: ~182
- **Notification**: ⚡ "$10 Milestone Reached! 182 PRs Generated"
- **Status**: Continue execution
- **Progress**: 67% complete

### $15 Milestone (Month Complete)
- **PRs Generated**: ~273
- **Executions**: ~273
- **Notification**: ✅ "$15 Milestone Reached! 273 PRs Generated"
- **Status**: Continue execution
- **Progress**: 100% complete
- **Display**: Monthly recap with statistics

### Beyond $15
- **Notification**: ⛔ "Monthly Budget Exhausted"
- **Status**: Pause execution until next month
- **Action**: Automatic reset on 1st of month
- **Manual Override**: Available via Discord command

## Budget Tracking System

### Data Structure (`token_budget.json`)
```json
{
  "version": "1.0",
  "current_month": "2026-02",
  "total_spent": 5.23,
  "executions": 95,
  "prs_generated": 95,
  "tokens_used": {
    "claude_input": 190000,
    "claude_output": 142500,
    "openai_input": 237500,
    "openai_output": 380000
  },
  "milestones_reached": [5.00],
  "last_reset": "2026-02-01T00:00:00Z",
  "history": [
    {
      "month": "2026-01",
      "total_spent": 14.82,
      "prs_generated": 269,
      "executions": 270,
      "milestones_reached": [5.00, 10.00, 15.00]
    }
  ]
}
```

### Key Metrics Tracked
1. **Total Spent**: Running total for current month
2. **Executions**: Number of orchestration runs
3. **PRs Generated**: Successful PR submissions
4. **Token Usage**: Per-model token consumption
5. **Milestones**: Thresholds reached this month
6. **History**: Archive of previous months

## Cost Monitoring

### Real-Time Tracking
The system tracks costs in real-time and updates `token_budget.json` after each execution:

```python
# Example cost calculation
claude_cost = (input_tokens * 3.0/1M) + (output_tokens * 15.0/1M)
openai_cost = (input_tokens * 0.15/1M) + (output_tokens * 0.60/1M)
total_cost = claude_cost + openai_cost
```

### Budget Enforcement
Before each execution:
1. Check `total_spent` vs `MONTHLY_BUDGET`
2. Calculate `remaining_budget`
3. Verify sufficient funds for next execution
4. Abort if budget exhausted

### Notification Triggers
Milestones are checked after each execution:
```python
if previous_spent < milestone <= new_spent:
    send_discord_notification(milestone)
    record_milestone(milestone)
```

## Discord Notifications

### Execution Start
```
🚀 AI Orchestration - Execution Started
Execution #: 95
Budget Remaining: $9.77
```

### Execution Complete
```
✅ AI Orchestration - Execution Complete
Execution #: 95
Cost: $0.0532
PR Status: PR #95
Budget Remaining: $9.72
```

### Milestone Reached
```
🚀 $5 Milestone Reached!
91 PRs Generated - 1/3 Complete!

💰 Total Spent: $5.02
📊 PRs Generated: 91
🔄 Executions: 91
💵 Cost per PR: $0.0552
📈 Progress: [███████░░░░░░░░░░░░░] 33.5%
```

### Budget Exhausted
```
⛔ Monthly Budget Exhausted
The $15 monthly budget has been exceeded.
Execution paused until next month.

💰 Total Spent: $15.12
📊 PRs Generated: 275
⏳ Next Reset: Start of next month
```

### Monthly Recap
```
📊 Monthly Recap - AI Orchestration
Summary for 2026-02

💰 Total Spent: $14.89 / $15.00
📊 PRs Generated: 271
🔄 Executions: 271
💵 Avg Cost per PR: $0.0549
🎯 Milestones Reached: 3
```

## Monthly Reset Process

### Automatic Reset (1st of Month)
1. Archive previous month's data to `history`
2. Send monthly recap notification
3. Reset counters to zero
4. Clear milestone notifications
5. Update `current_month` field

### Reset Workflow
```python
# Triggered automatically
if current_month != stored_month:
    archive_old_month()
    send_monthly_recap()
    reset_counters()
    update_month()
```

## Cost Optimization Tips

### Reducing Costs
1. **Reduce Token Usage**: Optimize prompts for conciseness
2. **Batch Operations**: Group similar tasks together
3. **Cache Results**: Reuse analysis when possible
4. **Adjust Models**: Use lighter models when appropriate

### Increasing Output
1. **Optimize Prompts**: Reduce token overhead
2. **Parallel Execution**: Run multiple tasks (respects budget)
3. **Skip Validation**: Reduce unnecessary checks

## Monitoring Dashboard

### Key Metrics to Track
- **Burn Rate**: Dollars spent per day
- **PR Velocity**: PRs generated per day
- **Cost Efficiency**: Cost per PR
- **Token Efficiency**: Tokens per PR
- **Milestone Progress**: Current position vs targets

### Health Indicators
- ✅ **Green**: Under 80% of budget
- ⚠️ **Yellow**: 80-95% of budget
- 🔴 **Red**: 95-100% of budget
- ⛔ **Blocked**: Over 100% of budget

## Troubleshooting

### Budget Not Updating
1. Check file permissions on `token_budget.json`
2. Verify GitHub Actions can commit changes
3. Check workflow logs for errors

### Notifications Not Sending
1. Verify `DISCORD_WEBHOOK_URL` is set
2. Test webhook URL manually
3. Check Discord server permissions

### Costs Higher Than Expected
1. Review actual token usage vs estimates
2. Check if prompts are too verbose
3. Verify model pricing is current
4. Analyze token efficiency metrics

### Execution Blocked Despite Budget
1. Check `token_budget.json` is up to date
2. Verify month rollover occurred
3. Manually reset if needed

## Manual Operations

### Force Reset Budget
```bash
cd automation
python -c "
from orchestrator import BudgetTracker
tracker = BudgetTracker()
tracker.data = tracker._create_new_budget_data()
tracker._save_budget_data()
print('Budget reset complete')
"
```

### Check Current Status
```bash
cd automation
python orchestrator.py
```

### Trigger Manual Execution
```bash
cd automation
python orchestrator.py "Your task description here"
```

## Best Practices

1. **Monitor Daily**: Check spending trends daily
2. **Review Milestones**: Analyze efficiency at each milestone
3. **Archive History**: Keep historical data for analysis
4. **Test Changes**: Validate cost impact of changes
5. **Document Anomalies**: Note unusual spending patterns

## Support

For issues or questions:
1. Check workflow logs in GitHub Actions
2. Review `token_budget.json` for discrepancies
3. Test Discord notifications manually
4. Contact system administrator

---

**Last Updated**: 2026-02-15
**System Version**: 1.0
**Budget Cycle**: Monthly
