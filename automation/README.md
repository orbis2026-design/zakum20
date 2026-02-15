# AI Automation System

This directory contains the AI automation system with budget tracking, Discord notifications, and authorization gates.

## Quick Start

### Prerequisites

- Python 3.11+
- pip

### Installation

```bash
cd automation
pip install -r requirements.txt
```

### Configuration

Set environment variables:
```bash
export DISCORD_WEBHOOK_URL="your_discord_webhook_url"
export ANTHROPIC_API_KEY="your_anthropic_api_key"
export OPENAI_API_KEY="your_openai_api_key"
```

### Usage

#### Check Budget Status
```bash
python orchestrator.py
```

#### Authorize Next $25 Block
```bash
python orchestrator.py --authorize
```

#### Test Discord Notifications
```bash
python discord_notifier.py
```

## Files

- **config.py**: Configuration settings (models, pricing, gates)
- **orchestrator.py**: Main orchestration and budget tracking
- **discord_notifier.py**: Discord notification system
- **requirements.txt**: Python dependencies

## Documentation

See [docs/BUDGET-MODEL.md](../docs/BUDGET-MODEL.md) for complete documentation.

## Architecture

```
┌─────────────────────┐
│  GitHub Actions     │
│  Workflow           │
└──────────┬──────────┘
           │
           ▼
┌─────────────────────┐
│  orchestrator.py    │
│  - Check budget     │
│  - Enforce gates    │
│  - Update spending  │
└──────────┬──────────┘
           │
           ├─────────────────┐
           │                 │
           ▼                 ▼
┌──────────────────┐  ┌──────────────────┐
│ discord_notifier │  │ cumulative_budget│
│ - $5 milestones  │  │ - Track spending │
│ - $25 gates      │  │ - Auth history   │
└──────────────────┘  └──────────────────┘
```

## Budget Model

- **Notification Interval**: $5
- **Authorization Gate**: $25
- **No Monthly Resets**: Cumulative tracking only
- **Models**: Claude Sonnet 4.5 + GPT-4o Mini
- **Cost per PR**: ~$0.055

## Authorization Flow

1. Workflow starts PAUSED
2. User authorizes first $25 block
3. System executes up to gate
4. Hits $25 → Pauses + Discord alert
5. User re-authorizes next block
6. Repeat indefinitely

## Support

For questions or issues, see [docs/BUDGET-MODEL.md](../docs/BUDGET-MODEL.md) or check the main repository documentation.
