# API Integration Guide - OpenAI & Anthropic

## Overview

This guide documents the API integration framework for OpenAI and Anthropic (Claude) services in the Zakum automation system. The integration is designed for **future use** to enable AI-powered code generation, task analysis, and documentation generation.

## Current Status

**⚠️ Phase: PLACEHOLDER / RESERVED FOR FUTURE USE**

API keys are validated but not yet actively utilized in workflows. The framework is prepared for:
- Task breakdown and analysis
- Code generation assistance
- Documentation generation
- Code review suggestions

## API Key Configuration

### Required Secrets

Add these secrets in GitHub repository settings (Settings → Secrets and variables → Actions):

1. **OPENAI_API_KEY**
   - Purpose: GPT-4 model access for rapid task generation
   - Cost: ~$0.00015 per request (GPT-4-mini)
   - Usage: Task analysis, code generation, documentation

2. **ANTHROPIC_API_KEY**
   - Purpose: Claude model access for complex reasoning
   - Cost: ~$0.00080 per request (Claude Haiku)
   - Usage: Code review, architecture decisions, optimization

3. **DISCORD_WEBHOOK_URL** (Optional)
   - Purpose: Task notifications
   - Currently Active: ✅ Used for task assignment notifications

### Validation

All workflows include secret validation steps that check:
- Secret accessibility (without revealing values)
- Availability status logging
- Graceful degradation if not configured

## Cost Strategy

### Phase 1: Rapid Development (Days 1-7)
```yaml
model: gpt-4-mini
budget: $20/day
requests_per_hour: 60-80
cost_per_request: $0.00015
use_cases:
  - Task breakdown (large → 10-15 subtasks)
  - Code generation (feature implementation)
  - Documentation (auto-generate API docs)
reasoning: Fast iteration, pattern discovery
```

### Phase 2: Steady Growth (Days 8-30)
```yaml
model: claude-3.5-haiku
budget: $5-10/day
requests_per_hour: 20-30
cost_per_request: $0.00080
use_cases:
  - Code review (quality checks)
  - Architecture (complex feature design)
  - Optimization (performance improvements)
reasoning: Better quality, handles complexity
```

### Phase 3: Operational Mode (Days 30+)
```yaml
strategy: Hybrid
split:
  claude: 70%  # complex/review tasks
  gpt-4-mini: 30%  # simple/generation tasks
budget: $3-5/day
requests_per_hour: 10-15
reasoning: Balance cost and quality
```

## Daily Cost Calculations

### 24/7 Operation Scenarios

**Scenario 1: GPT-4-mini only**
```
- 144 workflow runs/day (every 10 minutes)
- 2-3 API calls per run
- 400 calls/day total
- Cost: 400 × $0.00015 = $0.06/day
- Annual: ~$22/year ✅ VERY CHEAP
```

**Scenario 2: Claude Haiku only**
```
- 144 workflow runs/day
- 2-3 API calls per run
- 400 calls/day total
- Cost: 400 × $0.00080 = $0.32/day
- Annual: ~$117/year ✅ CHEAP
```

**Scenario 3: Hybrid (50/50)**
```
- 200 GPT-mini calls × $0.00015 = $0.03
- 200 Claude calls × $0.00080 = $0.16
- Total: $0.19/day
- Annual: ~$70/year ✅ VERY CHEAP
```

## Integration Points (Future Implementation)

### Manager Workflow Integration

```yaml
# Pseudocode for future implementation
steps:
  - name: AI-Powered Task Analysis
    run: |
      # Query OpenAI/Claude for task breakdown
      TASK_STEPS=$(call_openai_api \
        --model "gpt-4-mini" \
        --prompt "Break this task into implementation steps: $TASK_DESCRIPTION")
      
      # Store AI-generated steps
      echo "$TASK_STEPS" > task-steps.json
```

### Worker Workflow Integration

```yaml
# Pseudocode for future implementation
steps:
  - name: AI-Assisted Code Generation
    run: |
      # Generate code with Claude for complex logic
      CODE_SNIPPET=$(call_anthropic_api \
        --model "claude-3.5-haiku" \
        --prompt "Generate Java code for: $STEP_DESCRIPTION")
      
      # Save generated code
      echo "$CODE_SNIPPET" > generated-code.java
```

### Documentation Workflow Integration

```yaml
# Pseudocode for future implementation
steps:
  - name: Auto-Generate Documentation
    run: |
      # Generate docs from code
      DOCUMENTATION=$(call_openai_api \
        --model "gpt-4-mini" \
        --prompt "Generate API documentation for: $CODE_FILE")
      
      # Update documentation files
      echo "$DOCUMENTATION" > docs/api-reference.md
```

## Request Frequency Analysis

### Current Workflow Schedule

```
Manager Orchestrator: Every 10 minutes (144 times/day)
Worker Executors: On-demand (triggered by manager)
Total Workflows: 10 active workflows
```

### Estimated API Usage

**Conservative Estimate:**
- Manager: 1 API call per run = 144 calls/day
- Workers: 2 API calls per task × 14 tasks/day = 28 calls/day
- Total: ~172 calls/day
- Cost: $0.03 - $0.14/day (depending on model mix)

**Aggressive Estimate:**
- Manager: 2 API calls per run = 288 calls/day
- Workers: 5 API calls per task × 20 tasks/day = 100 calls/day
- Analytics: 1 call/day = 1 call/day
- Total: ~389 calls/day
- Cost: $0.06 - $0.31/day (depending on model mix)

### Rate Limit Protection

Built-in safeguards:
- 30-second delay between workflow cycles
- 20-second delay between task dispatches
- Exponential backoff on API failures
- Circuit breaker pattern (future)

## Budget Monitoring

### Daily Budget Tracking

The system tracks daily spend in `.github/automation/budget-YYYY-MM-DD.json`:

```json
{
  "date": "2026-02-16",
  "spent": 0.12,
  "limit": 25.00,
  "apiCalls": {
    "openai": 150,
    "anthropic": 45,
    "totalCost": 0.12
  },
  "tasks": []
}
```

### Cost Tracking Workflow

The `09-cost-tracking.yml` workflow monitors:
- GitHub Actions minutes used
- Estimated API costs (when active)
- Daily budget compliance
- Automatic pause on budget exhaustion

## Implementation Checklist

When ready to activate AI features:

- [ ] Add OPENAI_API_KEY to repository secrets
- [ ] Add ANTHROPIC_API_KEY to repository secrets
- [ ] Test API connectivity with test workflow
- [ ] Implement API wrapper functions
- [ ] Add error handling and retry logic
- [ ] Implement cost tracking per API call
- [ ] Update budget tracking to include API costs
- [ ] Add circuit breaker pattern
- [ ] Implement response caching
- [ ] Add AI-generated code validation
- [ ] Test with small task subset
- [ ] Monitor costs in production
- [ ] Optimize prompts for cost efficiency

## Security Considerations

### API Key Protection

✅ Current implementation:
- Secrets never logged or displayed
- Validation without revealing values
- No secrets in PR descriptions or comments
- GitHub secrets encryption at rest

### Rate Limiting

✅ Current implementation:
- Workflow delay between cycles
- Task dispatch throttling
- Budget-based pause mechanism
- Failed request tracking

### Future Considerations

When AI features are active:
- Sanitize code before sending to APIs
- Validate AI-generated code before execution
- Implement prompt injection protection
- Add content filtering
- Monitor for anomalous usage patterns

## Testing

### Local Testing (Before Production)

```bash
# Test OpenAI API connectivity
curl https://api.openai.com/v1/models \
  -H "Authorization: Bearer $OPENAI_API_KEY"

# Test Anthropic API connectivity
curl https://api.anthropic.com/v1/messages \
  -H "x-api-key: $ANTHROPIC_API_KEY" \
  -H "anthropic-version: 2023-06-01"
```

### Workflow Testing

Run the standalone label setup workflow to verify GitHub API access:
```bash
gh workflow run 10-setup-labels.yml
```

## References

- [OpenAI API Documentation](https://platform.openai.com/docs)
- [Anthropic Claude API Documentation](https://docs.anthropic.com/claude/reference)
- [GitHub Actions Secrets](https://docs.github.com/en/actions/security-guides/encrypted-secrets)
- [Cost Optimization Best Practices](https://platform.openai.com/docs/guides/production-best-practices)

## Support

For issues with API integration:
1. Check secret validation in workflow logs
2. Verify API key validity in provider console
3. Review budget tracking files
4. Check rate limit headers in API responses
5. Review TROUBLESHOOTING.md for common issues

---

**Last Updated:** 2026-02-16  
**Status:** Framework Ready - Awaiting Activation  
**Next Steps:** Add API keys and implement integration points
