"""
Discord Notifier - Sends webhook notifications to Discord.
Formats task updates, specifications, and code status.
"""

import json
import requests
from typing import Dict, Optional
from datetime import datetime


class DiscordNotifier:
    """Sends notifications to Discord via webhook."""
    
    def __init__(self, webhook_url: str):
        self.webhook_url = webhook_url
        self.enabled = bool(webhook_url)
    
    def send_notification(
        self,
        task: Dict,
        status: str,
        spec_summary: Optional[str] = None,
        code_summary: Optional[str] = None,
        pr_url: Optional[str] = None,
        build_success: Optional[bool] = None,
        error_message: Optional[str] = None
    ) -> bool:
        """
        Send a formatted notification to Discord.
        
        Args:
            task: Task dictionary with task information
            status: Status string (e.g., "started", "completed", "failed")
            spec_summary: Summary of Claude's specification
            code_summary: Summary of generated code
            pr_url: URL to the created PR
            build_success: Whether build validation passed
            error_message: Error message if failed
        
        Returns:
            True if notification sent successfully
        """
        if not self.enabled:
            return False
        
        # Determine color based on status
        color = self._get_color(status, build_success)
        
        # Build embed
        embed = {
            "title": f"🤖 AI Development: {task['name']}",
            "description": self._build_description(status, error_message),
            "color": color,
            "fields": [
                {
                    "name": "Task ID",
                    "value": f"`{task['task_id']}`",
                    "inline": True
                },
                {
                    "name": "Priority",
                    "value": task['priority'].upper(),
                    "inline": True
                },
                {
                    "name": "Module",
                    "value": task['module'],
                    "inline": True
                }
            ],
            "timestamp": datetime.now(timezone.utc).isoformat(),
            "footer": {
                "text": "AI Development System"
            }
        }
        
        # Add specification summary if available
        if spec_summary:
            embed["fields"].append({
                "name": "📋 Specification",
                "value": self._truncate(spec_summary, 300),
                "inline": False
            })
        
        # Add code summary if available
        if code_summary:
            embed["fields"].append({
                "name": "💻 Implementation",
                "value": self._truncate(code_summary, 300),
                "inline": False
            })
        
        # Add build status if available
        if build_success is not None:
            build_status = "✅ Passed" if build_success else "❌ Failed"
            embed["fields"].append({
                "name": "🔨 Build Validation",
                "value": build_status,
                "inline": True
            })
        
        # Add PR link if available
        if pr_url:
            embed["fields"].append({
                "name": "🔗 Pull Request",
                "value": f"[View PR]({pr_url})",
                "inline": True
            })
        
        # Send webhook
        payload = {
            "embeds": [embed],
            "username": "AI Dev Bot"
        }
        
        try:
            response = requests.post(
                self.webhook_url,
                json=payload,
                headers={"Content-Type": "application/json"},
                timeout=10
            )
            return response.status_code == 204
        except Exception as e:
            print(f"Failed to send Discord notification: {e}")
            return False
    
    def _get_color(self, status: str, build_success: Optional[bool]) -> int:
        """Get color code for embed based on status."""
        if status == "failed" or build_success is False:
            return 0xFF0000  # Red
        elif status == "completed" and build_success:
            return 0x00FF00  # Green
        elif status == "started":
            return 0x0099FF  # Blue
        elif status == "building":
            return 0xFFAA00  # Orange
        else:
            return 0x808080  # Gray
    
    def _build_description(self, status: str, error_message: Optional[str]) -> str:
        """Build description based on status."""
        status_emojis = {
            "started": "🚀 Task processing started",
            "spec_generated": "📋 Specification generated",
            "code_generated": "💻 Code implementation generated",
            "building": "🔨 Running build validation",
            "completed": "✅ Task completed successfully",
            "failed": "❌ Task failed"
        }
        
        description = status_emojis.get(status, f"Status: {status}")
        
        if error_message:
            description += f"\n\n**Error:** {error_message}"
        
        return description
    
    def _truncate(self, text: str, max_length: int) -> str:
        """Truncate text to max length."""
        if len(text) <= max_length:
            return text
        return text[:max_length - 3] + "..."
    
    def send_budget_alert(self, budget_info: Dict, warning_level: str) -> bool:
        """Send budget warning notification."""
        if not self.enabled:
            return False
        
        # Color based on warning level
        colors = {
            "yellow": 0xFFFF00,  # Yellow
            "orange": 0xFFAA00,  # Orange
            "red": 0xFF0000,     # Red
        }
        
        color = colors.get(warning_level, 0x808080)
        
        embed = {
            "title": f"⚠️ Budget Alert: {warning_level.upper()}",
            "description": f"Budget usage is approaching limit",
            "color": color,
            "fields": [
                {
                    "name": "Phase",
                    "value": budget_info["phase"],
                    "inline": True
                },
                {
                    "name": "Total Spent",
                    "value": f"${budget_info['total_spent']:.2f}",
                    "inline": True
                },
                {
                    "name": "PRs Generated",
                    "value": str(budget_info["prs_generated"]),
                    "inline": True
                }
            ],
            "timestamp": datetime.now(timezone.utc).isoformat()
        }
        
        if budget_info["phase"] == "initial_sprint":
            embed["fields"].append({
                "name": "Sprint Budget",
                "value": f"${budget_info['spent_upfront']:.2f} / ${budget_info['upfront_budget']:.2f}",
                "inline": False
            })
        else:
            embed["fields"].append({
                "name": "Daily Budget",
                "value": f"${budget_info['spent_today']:.2f} / ${budget_info['daily_budget']:.2f}",
                "inline": False
            })
        
        payload = {
            "embeds": [embed],
            "username": "AI Dev Bot"
        }
        
        try:
            response = requests.post(
                self.webhook_url,
                json=payload,
                headers={"Content-Type": "application/json"},
                timeout=10
            )
            return response.status_code == 204
        except Exception as e:
            print(f"Failed to send Discord budget alert: {e}")
            return False
    
    def send_summary(self, daily_stats: Dict) -> bool:
        """Send daily summary notification."""
        if not self.enabled:
            return False
        
        embed = {
            "title": "📊 Daily Development Summary",
            "color": 0x0099FF,
            "fields": [
                {
                    "name": "Tasks Completed",
                    "value": str(daily_stats.get("tasks_completed", 0)),
                    "inline": True
                },
                {
                    "name": "PRs Generated",
                    "value": str(daily_stats.get("prs_generated", 0)),
                    "inline": True
                },
                {
                    "name": "Build Success Rate",
                    "value": f"{daily_stats.get('build_success_rate', 0):.1f}%",
                    "inline": True
                },
                {
                    "name": "Total Spent Today",
                    "value": f"${daily_stats.get('spent_today', 0):.2f}",
                    "inline": True
                },
                {
                    "name": "Tokens Used",
                    "value": f"{daily_stats.get('tokens_used', 0):,}",
                    "inline": True
                }
            ],
            "timestamp": datetime.now(timezone.utc).isoformat()
        }
        
        payload = {
            "embeds": [embed],
            "username": "AI Dev Bot"
        }
        
        try:
            response = requests.post(
                self.webhook_url,
                json=payload,
                headers={"Content-Type": "application/json"},
                timeout=10
            )
            return response.status_code == 204
        except Exception as e:
            print(f"Failed to send Discord summary: {e}")
            return False


if __name__ == "__main__":
    import os
    
    webhook_url = os.environ.get("DISCORD_WEBHOOK_URL", "")
    
    if not webhook_url:
        print("DISCORD_WEBHOOK_URL not set, skipping test")
    else:
        notifier = DiscordNotifier(webhook_url)
        
        test_task = {
            "task_id": "test_001",
            "name": "Test Notification",
            "priority": "high",
            "module": "zakum-core"
        }
        
        print("Sending test notification...")
        success = notifier.send_notification(
            task=test_task,
            status="completed",
            spec_summary="Generated a comprehensive technical specification",
            code_summary="Implemented with proper error handling and tests",
            build_success=True,
            pr_url="https://github.com/example/repo/pull/123"
        )
        
        if success:
            print("✅ Test notification sent successfully")
        else:
            print("❌ Failed to send test notification")
