"""
Main orchestration script for AI automation with budget tracking.
"""
import json
import os
from datetime import datetime, timezone
from typing import Dict, Optional
import config
import discord_notifier


def load_budget() -> Dict:
    """Load the cumulative budget data."""
    budget_file = config.BUDGET_FILE
    
    if not os.path.exists(budget_file):
        # Initialize default budget
        return {
            "total_spent": 0.00,
            "total_prs_generated": 0,
            "total_tokens_used": 0,
            "authorization_blocks": [],
            "next_authorization_gate": config.AUTHORIZATION_GATE,
            "workflow_status": "PAUSED",
            "last_updated": datetime.now(timezone.utc).isoformat().replace('+00:00', 'Z')
        }
    
    with open(budget_file, 'r') as f:
        return json.load(f)


def save_budget(budget_data: Dict) -> None:
    """Save the cumulative budget data."""
    budget_data["last_updated"] = datetime.now(timezone.utc).isoformat().replace('+00:00', 'Z')
    
    with open(config.BUDGET_FILE, 'w') as f:
        json.dump(budget_data, f, indent=2)


def check_authorization_gate(budget_data: Dict) -> bool:
    """Check if we've hit an authorization gate."""
    total_spent = budget_data.get("total_spent", 0)
    next_gate = budget_data.get("next_authorization_gate", config.AUTHORIZATION_GATE)
    
    return total_spent >= next_gate


def check_milestone(budget_data: Dict, previous_spent: float) -> bool:
    """Check if we've crossed a $5 milestone."""
    total_spent = budget_data.get("total_spent", 0)
    
    # Calculate current and previous milestone
    current_milestone = int(total_spent // config.NOTIFICATION_INTERVAL)
    previous_milestone = int(previous_spent // config.NOTIFICATION_INTERVAL)
    
    return current_milestone > previous_milestone


def calculate_execution_cost(claude_input_tokens: int, claude_output_tokens: int,
                            openai_input_tokens: int, openai_output_tokens: int) -> float:
    """Calculate the cost of an execution."""
    claude_cost = (
        claude_input_tokens * config.CLAUDE_INPUT_COST +
        claude_output_tokens * config.CLAUDE_OUTPUT_COST
    )
    
    openai_cost = (
        openai_input_tokens * config.OPENAI_INPUT_COST +
        openai_output_tokens * config.OPENAI_OUTPUT_COST
    )
    
    return claude_cost + openai_cost


def update_budget(budget_data: Dict, cost: float, tokens_used: int, prs_generated: int = 1) -> Dict:
    """Update the budget with a new execution."""
    previous_spent = budget_data.get("total_spent", 0)
    
    budget_data["total_spent"] = round(previous_spent + cost, 2)
    budget_data["total_prs_generated"] = budget_data.get("total_prs_generated", 0) + prs_generated
    budget_data["total_tokens_used"] = budget_data.get("total_tokens_used", 0) + tokens_used
    
    return budget_data, previous_spent


def authorize_next_block(budget_data: Dict) -> Dict:
    """Authorize the next $25 spending block."""
    current_gate = budget_data.get("next_authorization_gate", config.AUTHORIZATION_GATE)
    
    # Add authorization block to history
    auth_block = {
        "gate_amount": current_gate,
        "authorized_at": datetime.now(timezone.utc).isoformat().replace('+00:00', 'Z'),
        "spent_at_authorization": budget_data.get("total_spent", 0)
    }
    
    if "authorization_blocks" not in budget_data:
        budget_data["authorization_blocks"] = []
    
    budget_data["authorization_blocks"].append(auth_block)
    
    # Set next gate
    budget_data["next_authorization_gate"] = current_gate + config.AUTHORIZATION_GATE
    budget_data["workflow_status"] = "RUNNING"
    
    return budget_data


def can_execute(budget_data: Dict) -> tuple[bool, str]:
    """Check if we can execute (haven't hit authorization gate)."""
    if check_authorization_gate(budget_data):
        return False, "Authorization gate reached. Re-authorization required."
    
    return True, "OK"


def main():
    """Main execution flow."""
    print("="*60)
    print("AI Automation Orchestrator - Budget Tracking System")
    print("="*60)
    
    # Load current budget
    budget_data = load_budget()
    print(f"\nCurrent budget status:")
    print(f"  Total Spent: ${budget_data.get('total_spent', 0):.2f}")
    print(f"  PRs Generated: {budget_data.get('total_prs_generated', 0)}")
    print(f"  Next Gate: ${budget_data.get('next_authorization_gate', 25.00):.2f}")
    print(f"  Status: {budget_data.get('workflow_status', 'PAUSED')}")
    
    # Check if we can execute
    can_run, message = can_execute(budget_data)
    
    if not can_run:
        print(f"\n❌ Cannot execute: {message}")
        print("\nTo continue, authorize the next block:")
        print("  python automation/orchestrator.py --authorize")
        
        # Send authorization gate notification
        discord_notifier.notify_authorization_gate(budget_data)
        return 1
    
    print("\n✅ Authorization OK - can execute")
    return 0


def authorize_command():
    """Handle authorization command."""
    print("="*60)
    print("Authorization Request")
    print("="*60)
    
    budget_data = load_budget()
    
    if not check_authorization_gate(budget_data):
        print(f"\n⚠️  Not at authorization gate yet.")
        print(f"   Current: ${budget_data.get('total_spent', 0):.2f}")
        print(f"   Next gate: ${budget_data.get('next_authorization_gate', 25.00):.2f}")
        return 0
    
    print(f"\n✅ Authorizing next ${config.AUTHORIZATION_GATE:.2f} block...")
    
    budget_data = authorize_next_block(budget_data)
    save_budget(budget_data)
    
    print(f"   New gate: ${budget_data['next_authorization_gate']:.2f}")
    print(f"   Status: {budget_data['workflow_status']}")
    print("\n✅ Authorization complete. You can now run the workflow again.")
    
    return 0


if __name__ == "__main__":
    import sys
    
    if len(sys.argv) > 1 and sys.argv[1] == "--authorize":
        exit(authorize_command())
    else:
        exit(main())
