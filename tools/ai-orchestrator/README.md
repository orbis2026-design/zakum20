# AI Orchestrator

This directory contains the Python orchestration system for automated AI-driven development.

## Components

- **config.py**: Configuration management, API keys, budget tracking
- **parse_docs.py**: Extract tasks from PLUGIN-TASKS.md
- **claude_analyzer.py**: Generate specifications using Claude Sonnet 4.5
- **chatgpt_coder.py**: Generate code using GPT-4.1
- **validator.py**: Build validation and compatibility checks
- **orchestrator.py**: Main controller coordinating all components
- **discord_notifier.py**: Discord webhook notifications
- **feedback_tracker.py**: Track successful patterns and learn

## Setup

1. Install dependencies:
```bash
pip install -r requirements.txt
```

2. Set environment variables:
```bash
export ANTHROPIC_API_KEY=your_key_here
export OPENAI_API_KEY=your_key_here
export GITHUB_TOKEN=your_token_here
export DISCORD_WEBHOOK_URL=your_webhook_here  # Optional
```

## Usage

### Run Orchestrator
```bash
python orchestrator.py
```

### Dry Run (No API Calls)
```bash
python orchestrator.py --dry-run
```

### Show Statistics
```bash
python orchestrator.py --stats
```

### Test Components Individually
```bash
python parse_docs.py
python config.py
python discord_notifier.py
```

## Directory Structure

```
ai-orchestrator/
├── *.py                    # Python scripts
├── requirements.txt        # Dependencies
├── completed_tasks.json    # Tracking completed tasks
├── merged_prs.json        # Feedback from merged PRs
├── generated/             # Generated specs and code
│   ├── spec_*.md
│   └── implementation_*.java
└── logs/                  # Execution logs
    └── orchestrator_*.log
```

## Workflow

1. **Parse** - Extract next task from docs/PLUGIN-TASKS.md
2. **Analyze** - Claude generates technical specification
3. **Code** - GPT-4 generates implementation
4. **Validate** - Run build and compatibility checks
5. **Notify** - Send status to Discord
6. **Learn** - Track successful patterns

## Budget Management

Budget is tracked in `../../token_budget.json`:
- Initial Sprint: $30 for 10 days
- Ongoing: $0.50/day
- Automatic phase transition
- Hard spending limits enforced

## Logs

Logs are written to `logs/orchestrator_YYYYMMDD.log` with:
- Timestamp for each step
- Token usage and costs
- Success/failure status
- Error details

## Integration

This system is designed to run via GitHub Actions on a schedule:
- Initial: Every 30 minutes (10 days)
- Ongoing: Every 3.5 hours

See `.github/workflows/multi-model-development.yml` for automation.
