"""
Configuration for AI orchestration system with cost tracking.
Models: Claude Sonnet 4.5 + GPT-4o Mini
Budget: $15/month with $5 milestone notifications
"""

# Model Configuration
CLAUDE_MODEL = "claude-3-5-sonnet-20241022"  # Sonnet 4.5
OPENAI_MODEL = "gpt-4o-mini"                  # GPT-4o Mini

# Claude Pricing (per token)
CLAUDE_INPUT_COST = 3.0 / 1_000_000   # $3 per 1M input tokens
CLAUDE_OUTPUT_COST = 15.0 / 1_000_000 # $15 per 1M output tokens

# OpenAI Pricing (per token)
OPENAI_INPUT_COST = 0.15 / 1_000_000  # $0.15 per 1M input tokens
OPENAI_OUTPUT_COST = 0.60 / 1_000_000 # $0.60 per 1M output tokens

# Budget Configuration
MONTHLY_BUDGET = 15.00  # Total monthly budget in USD
NOTIFICATION_THRESHOLDS = [5.00, 10.00, 15.00]  # Milestone notification points

# Estimated token usage per execution
CLAUDE_ESTIMATED_INPUT = 2000   # Tokens for analysis input
CLAUDE_ESTIMATED_OUTPUT = 1500  # Tokens for analysis output
OPENAI_ESTIMATED_INPUT = 2500   # Tokens for code generation input
OPENAI_ESTIMATED_OUTPUT = 4000  # Tokens for code generation output

# Calculate estimated cost per execution
ESTIMATED_COST_PER_EXECUTION = (
    (CLAUDE_ESTIMATED_INPUT * CLAUDE_INPUT_COST) +
    (CLAUDE_ESTIMATED_OUTPUT * CLAUDE_OUTPUT_COST) +
    (OPENAI_ESTIMATED_INPUT * OPENAI_INPUT_COST) +
    (OPENAI_ESTIMATED_OUTPUT * OPENAI_OUTPUT_COST)
)

# Execution limits
MAX_MONTHLY_EXECUTIONS = int(MONTHLY_BUDGET / ESTIMATED_COST_PER_EXECUTION)
EXECUTION_INTERVAL_HOURS = 2.6  # Every 2.6 hours for optimal budget usage

# Budget tracking file (in parent directory)
import os
BUDGET_FILE = os.path.join(os.path.dirname(os.path.dirname(__file__)), "token_budget.json")

# Discord webhook (to be set via environment variable)
DISCORD_WEBHOOK_URL = None  # Set via DISCORD_WEBHOOK_URL env var
