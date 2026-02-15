"""
Discord notification system for budget milestones and authorization gates.
"""
import os
import requests
from datetime import datetime
from typing import Dict, Optional
import config


def send_discord_notification(message: str, webhook_url: Optional[str] = None) -> bool:
    """Send a notification to Discord."""
    if webhook_url is None:
        webhook_url = os.getenv(config.DISCORD_WEBHOOK_URL_ENV)
    
    if not webhook_url:
        print("Warning: Discord webhook URL not configured")
        return False
    
    try:
        response = requests.post(
            webhook_url,
            json={"content": message},
            headers={"Content-Type": "application/json"}
        )
        response.raise_for_status()
        return True
    except Exception as e:
        print(f"Error sending Discord notification: {e}")
        return False


def format_milestone_notification(budget_data: Dict) -> str:
    """Format a $5 milestone notification."""
    total_spent = budget_data.get("total_spent", 0)
    prs_generated = budget_data.get("total_prs_generated", 0)
    tokens_used = budget_data.get("total_tokens_used", 0)
    next_gate = budget_data.get("next_authorization_gate", 25.00)
    
    # Calculate next milestone
    current_milestone = int(total_spent // config.NOTIFICATION_INTERVAL) * config.NOTIFICATION_INTERVAL
    next_milestone = current_milestone + config.NOTIFICATION_INTERVAL
    
    message = f"""💰 ${int(current_milestone)} Spent | Total: ${total_spent:.2f}

📊 Status:
├─ PRs Generated: {prs_generated}
├─ Tokens Used: {tokens_used:,}
├─ Cost per PR: $0.055
├─ Next Notification: ${next_milestone:.2f}
└─ Next Authorization Gate: ${next_gate:.2f}"""
    
    return message


def format_authorization_gate_notification(budget_data: Dict) -> str:
    """Format an authorization gate notification."""
    total_spent = budget_data.get("total_spent", 0)
    prs_generated = budget_data.get("total_prs_generated", 0)
    tokens_used = budget_data.get("total_tokens_used", 0)
    
    message = f"""⛔ AUTHORIZATION GATE REACHED: ${total_spent:.2f} Spent

📊 Summary:
├─ Total Spent: ${total_spent:.2f}
├─ PRs Generated: {prs_generated}
├─ Cost per PR: $0.055
├─ Tokens Used: {tokens_used:,}

⚠️ Action Required:
Re-authorize next ${config.AUTHORIZATION_GATE:.2f} block by:
1. React with ✅ in Discord
2. Click "Run workflow" in GitHub Actions
3. Reply "@copilot authorize" in Discord

🔄 Ready to continue when authorized"""
    
    return message


def notify_milestone(budget_data: Dict) -> bool:
    """Send a milestone notification."""
    message = format_milestone_notification(budget_data)
    return send_discord_notification(message)


def notify_authorization_gate(budget_data: Dict) -> bool:
    """Send an authorization gate notification."""
    message = format_authorization_gate_notification(budget_data)
    return send_discord_notification(message)


if __name__ == "__main__":
    # Test notifications
    test_data = {
        "total_spent": 5.00,
        "total_prs_generated": 91,
        "total_tokens_used": 910000,
        "next_authorization_gate": 25.00
    }
    
    print("Testing milestone notification:")
    print(format_milestone_notification(test_data))
    print("\n" + "="*50 + "\n")
    
    test_data["total_spent"] = 25.00
    test_data["total_prs_generated"] = 454
    test_data["total_tokens_used"] = 4540000
    
    print("Testing authorization gate notification:")
    print(format_authorization_gate_notification(test_data))
