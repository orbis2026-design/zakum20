"""
AI Orchestration System for Zakum20

Multi-model AI automation with budget tracking and milestone notifications.
Models: Claude Sonnet 4.5 (analysis) + GPT-4o Mini (code generation)
Budget: $15/month with $5 milestone notifications
"""

__version__ = "1.0.0"
__author__ = "Zakum AI Team"

from .config import (
    CLAUDE_MODEL,
    OPENAI_MODEL,
    MONTHLY_BUDGET,
    NOTIFICATION_THRESHOLDS,
    ESTIMATED_COST_PER_EXECUTION
)

from .orchestrator import AIOrchestrator, BudgetTracker
from .discord_notifier import DiscordNotifier, send_quick_notification

__all__ = [
    "AIOrchestrator",
    "BudgetTracker",
    "DiscordNotifier",
    "send_quick_notification",
    "CLAUDE_MODEL",
    "OPENAI_MODEL",
    "MONTHLY_BUDGET",
    "NOTIFICATION_THRESHOLDS",
    "ESTIMATED_COST_PER_EXECUTION",
]
