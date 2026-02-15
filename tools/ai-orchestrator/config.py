"""
Configuration management for the AI orchestration system.
Handles API keys, budget tracking, and rate limiting.
"""

import os
import json
from datetime import datetime, timezone
from pathlib import Path

# Base paths
BASE_DIR = Path(__file__).parent
REPO_ROOT = BASE_DIR.parent.parent
BUDGET_FILE = REPO_ROOT / "token_budget.json"
COMPLETED_TASKS_FILE = BASE_DIR / "completed_tasks.json"
MERGED_PRS_FILE = BASE_DIR / "merged_prs.json"
LOGS_DIR = BASE_DIR / "logs"
GENERATED_DIR = BASE_DIR / "generated"

# Ensure directories exist
LOGS_DIR.mkdir(exist_ok=True)
GENERATED_DIR.mkdir(exist_ok=True)

# API Configuration
ANTHROPIC_API_KEY = os.environ.get("ANTHROPIC_API_KEY", "")
OPENAI_API_KEY = os.environ.get("OPENAI_API_KEY", "")
GITHUB_TOKEN = os.environ.get("GITHUB_TOKEN", "")
DISCORD_WEBHOOK_URL = os.environ.get("DISCORD_WEBHOOK_URL", "")

# API Cost Configuration (per million tokens)
CLAUDE_COST = {
    "input": 3.00,   # $3.00 per million input tokens
    "output": 15.00  # $15.00 per million output tokens
}

GPT4_COST = {
    "input": 2.50,   # $2.50 per million input tokens
    "output": 10.00  # $10.00 per million output tokens
}

# Task Configuration
TASK_FILE = REPO_ROOT / "docs" / "PLUGIN-TASKS.md"

# Budget thresholds for warnings (percentage)
BUDGET_WARNING_LEVELS = {
    "yellow": 0.80,  # 80%
    "orange": 0.90,  # 90%
    "red": 0.95      # 95%
}


class BudgetManager:
    """Manages budget tracking and enforcement."""
    
    def __init__(self):
        self.budget_file = BUDGET_FILE
        self.budget = self._load_budget()
    
    def _load_budget(self):
        """Load budget from JSON file."""
        if self.budget_file.exists():
            with open(self.budget_file, 'r') as f:
                return json.load(f)
        else:
            # Return default budget if file doesn't exist
            return {
                "upfront_budget": 30.00,
                "daily_budget": 0.50,
                "spent_upfront": 0.00,
                "spent_today": 0.00,
                "total_spent": 0.00,
                "tokens_used": 0,
                "prs_generated": 0,
                "phase": "initial_sprint",
                "sprint_start_date": datetime.now(timezone.utc).isoformat(),
                "last_reset_date": datetime.now(timezone.utc).isoformat(),
                "tasks_completed": 0
            }
    
    def save_budget(self):
        """Save budget to JSON file."""
        with open(self.budget_file, 'w') as f:
            json.dump(self.budget, f, indent=2)
    
    def check_phase_transition(self):
        """Check if we should transition from sprint to ongoing phase."""
        if self.budget["phase"] != "initial_sprint":
            return
        
        start_date = datetime.fromisoformat(self.budget["sprint_start_date"])
        days_elapsed = (datetime.now(timezone.utc) - start_date).days
        
        if days_elapsed >= 10:
            self.budget["phase"] = "ongoing"
            self.budget["spent_upfront"] = self.budget["total_spent"]
            self.budget["spent_today"] = 0.00
            self.budget["last_reset_date"] = datetime.now(timezone.utc).isoformat()
            self.save_budget()
            return True
        return False
    
    def check_daily_reset(self):
        """Reset daily spending if it's a new day."""
        if self.budget["phase"] != "ongoing":
            return
        
        last_reset = datetime.fromisoformat(self.budget["last_reset_date"])
        now = datetime.now(timezone.utc)
        
        # If it's a new day, reset daily spending
        if now.date() > last_reset.date():
            self.budget["spent_today"] = 0.00
            self.budget["last_reset_date"] = now.isoformat()
            self.save_budget()
            return True
        return False
    
    def can_execute_task(self):
        """Check if we have budget to execute another task."""
        self.check_phase_transition()
        self.check_daily_reset()
        
        if self.budget["phase"] == "initial_sprint":
            return self.budget["spent_upfront"] < self.budget["upfront_budget"]
        else:
            return self.budget["spent_today"] < self.budget["daily_budget"]
    
    def get_remaining_budget(self):
        """Get remaining budget amount."""
        if self.budget["phase"] == "initial_sprint":
            return self.budget["upfront_budget"] - self.budget["spent_upfront"]
        else:
            return self.budget["daily_budget"] - self.budget["spent_today"]
    
    def record_spending(self, amount, tokens_used):
        """Record spending and token usage."""
        self.budget["total_spent"] += amount
        self.budget["tokens_used"] += tokens_used
        
        if self.budget["phase"] == "initial_sprint":
            self.budget["spent_upfront"] += amount
        else:
            self.budget["spent_today"] += amount
        
        self.save_budget()
    
    def record_pr_generated(self):
        """Record that a PR was generated."""
        self.budget["prs_generated"] += 1
        self.save_budget()
    
    def record_task_completed(self):
        """Record that a task was completed."""
        self.budget["tasks_completed"] += 1
        self.save_budget()
    
    def get_warning_level(self):
        """Get budget warning level based on spending."""
        if self.budget["phase"] == "initial_sprint":
            pct = self.budget["spent_upfront"] / self.budget["upfront_budget"]
        else:
            pct = self.budget["spent_today"] / self.budget["daily_budget"]
        
        if pct >= BUDGET_WARNING_LEVELS["red"]:
            return "red"
        elif pct >= BUDGET_WARNING_LEVELS["orange"]:
            return "orange"
        elif pct >= BUDGET_WARNING_LEVELS["yellow"]:
            return "yellow"
        return "green"
    
    def calculate_cost(self, api_type, input_tokens, output_tokens):
        """Calculate cost for API usage."""
        if api_type == "claude":
            cost = CLAUDE_COST
        elif api_type == "gpt4":
            cost = GPT4_COST
        else:
            raise ValueError(f"Unknown API type: {api_type}")
        
        input_cost = (input_tokens / 1_000_000) * cost["input"]
        output_cost = (output_tokens / 1_000_000) * cost["output"]
        return input_cost + output_cost


def get_config():
    """Get configuration dictionary."""
    return {
        "anthropic_api_key": ANTHROPIC_API_KEY,
        "openai_api_key": OPENAI_API_KEY,
        "github_token": GITHUB_TOKEN,
        "discord_webhook_url": DISCORD_WEBHOOK_URL,
        "task_file": str(TASK_FILE),
        "logs_dir": str(LOGS_DIR),
        "generated_dir": str(GENERATED_DIR),
        "completed_tasks_file": str(COMPLETED_TASKS_FILE),
        "merged_prs_file": str(MERGED_PRS_FILE),
    }


def validate_config():
    """Validate that required configuration is present."""
    errors = []
    
    if not ANTHROPIC_API_KEY:
        errors.append("ANTHROPIC_API_KEY environment variable not set")
    
    if not OPENAI_API_KEY:
        errors.append("OPENAI_API_KEY environment variable not set")
    
    if not GITHUB_TOKEN:
        errors.append("GITHUB_TOKEN environment variable not set")
    
    if not TASK_FILE.exists():
        errors.append(f"Task file not found: {TASK_FILE}")
    
    return errors


if __name__ == "__main__":
    # Test configuration
    errors = validate_config()
    if errors:
        print("Configuration errors:")
        for error in errors:
            print(f"  - {error}")
    else:
        print("Configuration valid!")
    
    # Test budget manager
    budget_mgr = BudgetManager()
    print(f"\nBudget Status:")
    print(f"  Phase: {budget_mgr.budget['phase']}")
    print(f"  Can execute: {budget_mgr.can_execute_task()}")
    print(f"  Remaining: ${budget_mgr.get_remaining_budget():.2f}")
    print(f"  Warning level: {budget_mgr.get_warning_level()}")
