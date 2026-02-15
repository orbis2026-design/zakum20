"""
Discord notification system for milestone alerts and execution updates.
Supports standard notifications and milestone progress tracking.
"""

import os
import json
from datetime import datetime
from typing import Optional, Dict, Any
import requests

from config import DISCORD_WEBHOOK_URL, NOTIFICATION_THRESHOLDS, MONTHLY_BUDGET


class DiscordNotifier:
    """Handles Discord notifications for AI orchestration system."""
    
    def __init__(self, webhook_url: Optional[str] = None):
        """Initialize Discord notifier with webhook URL."""
        self.webhook_url = webhook_url or os.getenv('DISCORD_WEBHOOK_URL') or DISCORD_WEBHOOK_URL
        if not self.webhook_url:
            print("Warning: No Discord webhook URL configured. Notifications disabled.")
    
    def send_notification(self, content: str, embed: Optional[Dict[str, Any]] = None) -> bool:
        """Send a notification to Discord."""
        if not self.webhook_url:
            print(f"[DISCORD] {content}")
            return False
        
        payload = {"content": content}
        if embed:
            payload["embeds"] = [embed]
        
        try:
            response = requests.post(
                self.webhook_url,
                json=payload,
                headers={"Content-Type": "application/json"},
                timeout=10
            )
            response.raise_for_status()
            return True
        except Exception as e:
            print(f"Failed to send Discord notification: {e}")
            return False
    
    def send_execution_start(self, execution_number: int, budget_remaining: float) -> bool:
        """Send notification when execution starts."""
        embed = {
            "title": "🚀 AI Orchestration - Execution Started",
            "color": 0x00FF00,  # Green
            "fields": [
                {
                    "name": "Execution #",
                    "value": str(execution_number),
                    "inline": True
                },
                {
                    "name": "Budget Remaining",
                    "value": f"${budget_remaining:.2f}",
                    "inline": True
                }
            ],
            "timestamp": datetime.now().isoformat()
        }
        return self.send_notification("", embed)
    
    def send_execution_complete(
        self, 
        execution_number: int, 
        cost: float, 
        pr_number: Optional[int] = None,
        budget_remaining: float = 0.0
    ) -> bool:
        """Send notification when execution completes."""
        pr_info = f"PR #{pr_number}" if pr_number else "No PR created"
        
        embed = {
            "title": "✅ AI Orchestration - Execution Complete",
            "color": 0x0099FF,  # Blue
            "fields": [
                {
                    "name": "Execution #",
                    "value": str(execution_number),
                    "inline": True
                },
                {
                    "name": "Cost",
                    "value": f"${cost:.4f}",
                    "inline": True
                },
                {
                    "name": "PR Status",
                    "value": pr_info,
                    "inline": True
                },
                {
                    "name": "Budget Remaining",
                    "value": f"${budget_remaining:.2f}",
                    "inline": True
                }
            ],
            "timestamp": datetime.now().isoformat()
        }
        return self.send_notification("", embed)
    
    def send_milestone_notification(
        self,
        milestone: float,
        total_spent: float,
        prs_generated: int,
        executions: int
    ) -> bool:
        """Send notification when a budget milestone is reached."""
        # Determine milestone emoji and message
        milestone_info = self._get_milestone_info(milestone, prs_generated)
        
        # Calculate progress percentage
        progress_percent = (total_spent / MONTHLY_BUDGET) * 100
        progress_bar = self._create_progress_bar(progress_percent)
        
        # Determine color based on milestone
        if milestone == 5.00:
            color = 0x00FF00  # Green
        elif milestone == 10.00:
            color = 0xFFAA00  # Orange
        else:  # 15.00
            color = 0xFF0000  # Red
        
        embed = {
            "title": f"{milestone_info['emoji']} ${milestone:.0f} Milestone Reached!",
            "description": milestone_info['message'],
            "color": color,
            "fields": [
                {
                    "name": "💰 Total Spent",
                    "value": f"${total_spent:.2f}",
                    "inline": True
                },
                {
                    "name": "📊 PRs Generated",
                    "value": str(prs_generated),
                    "inline": True
                },
                {
                    "name": "🔄 Executions",
                    "value": str(executions),
                    "inline": True
                },
                {
                    "name": "💵 Cost per PR",
                    "value": f"${total_spent / max(prs_generated, 1):.4f}",
                    "inline": True
                },
                {
                    "name": "📈 Progress",
                    "value": f"{progress_bar} {progress_percent:.1f}%",
                    "inline": False
                }
            ],
            "timestamp": datetime.now().isoformat()
        }
        
        return self.send_notification("", embed)
    
    def send_budget_exceeded(self, total_spent: float, prs_generated: int) -> bool:
        """Send notification when monthly budget is exceeded."""
        embed = {
            "title": "⛔ Monthly Budget Exhausted",
            "description": "The $15 monthly budget has been exceeded. Execution paused until next month.",
            "color": 0xFF0000,  # Red
            "fields": [
                {
                    "name": "💰 Total Spent",
                    "value": f"${total_spent:.2f}",
                    "inline": True
                },
                {
                    "name": "📊 PRs Generated",
                    "value": str(prs_generated),
                    "inline": True
                },
                {
                    "name": "⏳ Next Reset",
                    "value": "Start of next month",
                    "inline": False
                }
            ],
            "timestamp": datetime.now().isoformat()
        }
        return self.send_notification("@here Budget Limit Reached!", embed)
    
    def send_monthly_recap(self, budget_data: Dict[str, Any]) -> bool:
        """Send monthly recap with statistics."""
        total_spent = budget_data.get('total_spent', 0.0)
        prs_generated = budget_data.get('prs_generated', 0)
        executions = budget_data.get('executions', 0)
        
        avg_cost_per_pr = total_spent / max(prs_generated, 1)
        
        embed = {
            "title": "📊 Monthly Recap - AI Orchestration",
            "description": f"Summary for {budget_data.get('current_month', 'Unknown')}",
            "color": 0x9B59B6,  # Purple
            "fields": [
                {
                    "name": "💰 Total Spent",
                    "value": f"${total_spent:.2f} / ${MONTHLY_BUDGET:.2f}",
                    "inline": True
                },
                {
                    "name": "📊 PRs Generated",
                    "value": str(prs_generated),
                    "inline": True
                },
                {
                    "name": "🔄 Executions",
                    "value": str(executions),
                    "inline": True
                },
                {
                    "name": "💵 Avg Cost per PR",
                    "value": f"${avg_cost_per_pr:.4f}",
                    "inline": True
                },
                {
                    "name": "🎯 Milestones Reached",
                    "value": str(len(budget_data.get('milestones_reached', []))),
                    "inline": True
                }
            ],
            "timestamp": datetime.now().isoformat()
        }
        return self.send_notification("", embed)
    
    def send_error(self, error_message: str, execution_number: Optional[int] = None) -> bool:
        """Send error notification."""
        embed = {
            "title": "❌ Execution Error",
            "description": error_message,
            "color": 0xFF0000,  # Red
            "fields": []
        }
        
        if execution_number:
            embed["fields"].append({
                "name": "Execution #",
                "value": str(execution_number),
                "inline": True
            })
        
        embed["timestamp"] = datetime.now().isoformat()
        
        return self.send_notification("", embed)
    
    def _get_milestone_info(self, milestone: float, prs_generated: int) -> Dict[str, str]:
        """Get emoji and message for milestone."""
        if milestone == 5.00:
            return {
                "emoji": "🚀",
                "message": f"{prs_generated} PRs Generated - 1/3 Complete!"
            }
        elif milestone == 10.00:
            return {
                "emoji": "⚡",
                "message": f"{prs_generated} PRs Generated - 2/3 Complete!"
            }
        else:  # 15.00
            return {
                "emoji": "✅",
                "message": f"{prs_generated} PRs Generated - Month Complete!"
            }
    
    def _create_progress_bar(self, percent: float, length: int = 20) -> str:
        """Create a text-based progress bar."""
        filled = int((percent / 100) * length)
        empty = length - filled
        return f"[{'█' * filled}{'░' * empty}]"


# Convenience function for quick notifications
def send_quick_notification(message: str) -> bool:
    """Send a quick text notification to Discord."""
    notifier = DiscordNotifier()
    return notifier.send_notification(message)


if __name__ == "__main__":
    # Test notifications
    print("Testing Discord notifications...")
    notifier = DiscordNotifier()
    
    # Test milestone notification
    notifier.send_milestone_notification(
        milestone=5.00,
        total_spent=5.02,
        prs_generated=91,
        executions=91
    )
