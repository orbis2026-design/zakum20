"""
Configuration for AI automation system with budget tracking.
"""
import os

# Models
CLAUDE_MODEL = "claude-3-5-sonnet-20241022"
OPENAI_MODEL = "gpt-4o-mini"

# Authorization
AUTHORIZATION_GATE = 25.00  # Pause every $25
NOTIFICATION_INTERVAL = 5.00  # Alert every $5

# Pricing (per 1M tokens)
CLAUDE_INPUT_COST = 3.0 / 1_000_000
CLAUDE_OUTPUT_COST = 15.0 / 1_000_000
OPENAI_INPUT_COST = 0.15 / 1_000_000
OPENAI_OUTPUT_COST = 0.60 / 1_000_000

# No monthly reset
MONTHLY_RESET = False

# Budget tracking file (relative to repository root)
_SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
_REPO_ROOT = os.path.dirname(_SCRIPT_DIR)
BUDGET_FILE = os.path.join(_REPO_ROOT, "cumulative_budget.json")

# Discord webhook URL (set via environment variable)
DISCORD_WEBHOOK_URL_ENV = "DISCORD_WEBHOOK_URL"
