#!/usr/bin/env python3
"""
Discord Notifier for AI Orchestrator

Sends detailed notifications to Discord about PR creations and status updates.
"""

import os
import json
from typing import Dict, Any, Optional, List
from datetime import datetime
from discord_webhook import DiscordWebhook, DiscordEmbed


class DiscordNotifier:
    """Discord webhook notifier."""
    
    def __init__(self, webhook_url: Optional[str] = None):
        """
        Initialize Discord notifier.
        
        Args:
            webhook_url: Discord webhook URL (or use DISCORD_WEBHOOK_URL env var)
        """
        self.webhook_url = webhook_url or os.getenv('DISCORD_WEBHOOK_URL')
        if not self.webhook_url:
            raise ValueError("Discord webhook URL required (set DISCORD_WEBHOOK_URL or pass webhook_url)")
    
    def notify_pr_created(
        self,
        task_id: str,
        task_title: str,
        module: str,
        pr_url: str,
        branch: str,
        details: Optional[Dict[str, Any]] = None
    ) -> bool:
        """
        Notify about PR creation.
        
        Args:
            task_id: Task identifier
            task_title: Task title
            module: Module name
            pr_url: Pull request URL
            branch: Branch name
            details: Additional details
            
        Returns:
            True if notification sent successfully
        """
        webhook = DiscordWebhook(url=self.webhook_url, rate_limit_retry=True)
        
        embed = DiscordEmbed(
            title=f"🤖 New AI-Generated PR: {task_title}",
            description=f"Automated pull request created for `{task_id}`",
            color=0x00ff00,
            url=pr_url
        )
        
        embed.add_embed_field(name="Task ID", value=f"`{task_id}`", inline=True)
        embed.add_embed_field(name="Module", value=f"`{module}`", inline=True)
        embed.add_embed_field(name="Branch", value=f"`{branch}`", inline=True)
        
        if details:
            if 'priority' in details:
                priority_emoji = self._get_priority_emoji(details['priority'])
                embed.add_embed_field(
                    name="Priority",
                    value=f"{priority_emoji} {details['priority'].upper()}",
                    inline=True
                )
            
            if 'files_changed' in details:
                embed.add_embed_field(
                    name="Files Changed",
                    value=str(details['files_changed']),
                    inline=True
                )
            
            if 'compilation_status' in details:
                status_emoji = "✅" if details['compilation_status'] == 'success' else "❌"
                embed.add_embed_field(
                    name="Compilation",
                    value=f"{status_emoji} {details['compilation_status']}",
                    inline=True
                )
        
        embed.set_footer(text="AI Development Automation")
        embed.set_timestamp()
        
        webhook.add_embed(embed)
        
        try:
            response = webhook.execute()
            return response.status_code == 200
        except Exception as e:
            print(f"Failed to send Discord notification: {e}")
            return False
    
    def notify_pr_validation(
        self,
        task_id: str,
        pr_url: str,
        validation_results: Dict[str, Any]
    ) -> bool:
        """
        Notify about PR validation results.
        
        Args:
            task_id: Task identifier
            pr_url: Pull request URL
            validation_results: Validation results
            
        Returns:
            True if notification sent successfully
        """
        webhook = DiscordWebhook(url=self.webhook_url, rate_limit_retry=True)
        
        overall_pass = validation_results.get('overall_pass', False)
        color = 0x00ff00 if overall_pass else 0xff0000
        status = "✅ Passed" if overall_pass else "❌ Failed"
        
        embed = DiscordEmbed(
            title=f"Validation {status}: {task_id}",
            description=f"Validation results for pull request",
            color=color,
            url=pr_url
        )
        
        # Compilation results
        compilation = validation_results.get('compilation', {})
        comp_status = "✅" if compilation.get('success') else "❌"
        embed.add_embed_field(
            name="Compilation",
            value=f"{comp_status} {'Success' if compilation.get('success') else 'Failed'}",
            inline=True
        )
        
        if compilation.get('errors'):
            error_summary = '\n'.join([f"- {e}" for e in compilation['errors'][:3]])
            embed.add_embed_field(
                name="Compilation Errors",
                value=f"```\n{error_summary}\n```",
                inline=False
            )
        
        # Folia compatibility
        folia = validation_results.get('folia_compatibility', {})
        folia_status = "✅" if folia.get('compatible', True) else "⚠️"
        embed.add_embed_field(
            name="Folia Compatibility",
            value=f"{folia_status} {'Compatible' if folia.get('compatible', True) else 'Issues Found'}",
            inline=True
        )
        
        if folia.get('critical_issues'):
            issues_summary = '\n'.join([
                f"- Line {issue['line']}: {issue['message']}"
                for issue in folia['critical_issues'][:2]
            ])
            embed.add_embed_field(
                name="Folia Issues",
                value=issues_summary,
                inline=False
            )
        
        # Plugin descriptor
        descriptor = validation_results.get('plugin_descriptor', {})
        desc_status = "✅" if descriptor.get('valid') else "❌"
        embed.add_embed_field(
            name="Plugin Descriptor",
            value=f"{desc_status} {'Valid' if descriptor.get('valid') else 'Invalid'}",
            inline=True
        )
        
        embed.set_footer(text="AI Development Automation")
        embed.set_timestamp()
        
        webhook.add_embed(embed)
        
        try:
            response = webhook.execute()
            return response.status_code == 200
        except Exception as e:
            print(f"Failed to send Discord notification: {e}")
            return False
    
    def notify_pr_merged(
        self,
        task_id: str,
        task_title: str,
        pr_url: str,
        merge_info: Optional[Dict[str, Any]] = None
    ) -> bool:
        """
        Notify about PR merge.
        
        Args:
            task_id: Task identifier
            task_title: Task title
            pr_url: Pull request URL
            merge_info: Additional merge information
            
        Returns:
            True if notification sent successfully
        """
        webhook = DiscordWebhook(url=self.webhook_url, rate_limit_retry=True)
        
        embed = DiscordEmbed(
            title=f"✅ PR Merged: {task_title}",
            description=f"AI-generated code successfully merged for `{task_id}`",
            color=0x00ff00,
            url=pr_url
        )
        
        embed.add_embed_field(name="Task ID", value=f"`{task_id}`", inline=True)
        
        if merge_info:
            if 'merged_by' in merge_info:
                embed.add_embed_field(
                    name="Merged By",
                    value=merge_info['merged_by'],
                    inline=True
                )
            
            if 'commits' in merge_info:
                embed.add_embed_field(
                    name="Commits",
                    value=str(merge_info['commits']),
                    inline=True
                )
        
        embed.set_footer(text="AI Development Automation • Feedback collected for future improvements")
        embed.set_timestamp()
        
        webhook.add_embed(embed)
        
        try:
            response = webhook.execute()
            return response.status_code == 200
        except Exception as e:
            print(f"Failed to send Discord notification: {e}")
            return False
    
    def notify_error(
        self,
        task_id: str,
        error_message: str,
        details: Optional[Dict[str, Any]] = None
    ) -> bool:
        """
        Notify about an error during processing.
        
        Args:
            task_id: Task identifier
            error_message: Error message
            details: Additional error details
            
        Returns:
            True if notification sent successfully
        """
        webhook = DiscordWebhook(url=self.webhook_url, rate_limit_retry=True)
        
        embed = DiscordEmbed(
            title=f"❌ Error Processing Task: {task_id}",
            description=error_message,
            color=0xff0000
        )
        
        embed.add_embed_field(name="Task ID", value=f"`{task_id}`", inline=True)
        
        if details:
            if 'stage' in details:
                embed.add_embed_field(
                    name="Failed Stage",
                    value=details['stage'],
                    inline=True
                )
            
            if 'error_details' in details:
                error_text = details['error_details'][:500]  # Limit length
                embed.add_embed_field(
                    name="Details",
                    value=f"```\n{error_text}\n```",
                    inline=False
                )
        
        embed.set_footer(text="AI Development Automation")
        embed.set_timestamp()
        
        webhook.add_embed(embed)
        
        try:
            response = webhook.execute()
            return response.status_code == 200
        except Exception as e:
            print(f"Failed to send Discord notification: {e}")
            return False
    
    def notify_summary(
        self,
        processed: int,
        succeeded: int,
        failed: int,
        details: Optional[List[Dict[str, Any]]] = None
    ) -> bool:
        """
        Send summary notification of processing run.
        
        Args:
            processed: Total tasks processed
            succeeded: Number of successful tasks
            failed: Number of failed tasks
            details: List of task details
            
        Returns:
            True if notification sent successfully
        """
        webhook = DiscordWebhook(url=self.webhook_url, rate_limit_retry=True)
        
        embed = DiscordEmbed(
            title="📊 AI Development Automation Summary",
            description=f"Processing run completed",
            color=0x3498db
        )
        
        embed.add_embed_field(name="Processed", value=str(processed), inline=True)
        embed.add_embed_field(name="Succeeded", value=f"✅ {succeeded}", inline=True)
        embed.add_embed_field(name="Failed", value=f"❌ {failed}", inline=True)
        
        if details:
            summary_text = '\n'.join([
                f"{'✅' if d.get('success') else '❌'} {d.get('task_id', 'unknown')}: {d.get('title', 'N/A')}"
                for d in details[:5]  # Limit to 5 tasks
            ])
            embed.add_embed_field(
                name="Tasks",
                value=summary_text,
                inline=False
            )
        
        embed.set_footer(text="AI Development Automation")
        embed.set_timestamp()
        
        webhook.add_embed(embed)
        
        try:
            response = webhook.execute()
            return response.status_code == 200
        except Exception as e:
            print(f"Failed to send Discord notification: {e}")
            return False
    
    def _get_priority_emoji(self, priority: str) -> str:
        """Get emoji for priority level."""
        priority_map = {
            'high': '🔴',
            'medium': '🟡',
            'low': '🟢'
        }
        return priority_map.get(priority.lower(), '⚪')


def main():
    """Example usage and testing."""
    import sys
    
    if len(sys.argv) < 2:
        print("Usage: python discord_notifier.py <test|pr|validation|merged|error|summary>")
        sys.exit(1)
    
    notification_type = sys.argv[1]
    
    try:
        notifier = DiscordNotifier()
        
        if notification_type == 'test':
            print("Sending test notification...")
            success = notifier.notify_pr_created(
                task_id='test_001',
                task_title='Test Task',
                module='zakum-core',
                pr_url='https://github.com/orbis2026-design/zakum20/pull/1',
                branch='feature/test-task',
                details={
                    'priority': 'high',
                    'files_changed': 5,
                    'compilation_status': 'success'
                }
            )
        
        elif notification_type == 'validation':
            print("Sending validation notification...")
            success = notifier.notify_pr_validation(
                task_id='test_001',
                pr_url='https://github.com/orbis2026-design/zakum20/pull/1',
                validation_results={
                    'overall_pass': True,
                    'compilation': {'success': True, 'errors': []},
                    'folia_compatibility': {'compatible': True, 'critical_issues': []},
                    'plugin_descriptor': {'valid': True}
                }
            )
        
        elif notification_type == 'summary':
            print("Sending summary notification...")
            success = notifier.notify_summary(
                processed=5,
                succeeded=4,
                failed=1,
                details=[
                    {'success': True, 'task_id': 'core_001', 'title': 'Player Cache'},
                    {'success': True, 'task_id': 'gui_001', 'title': 'Animations'},
                    {'success': False, 'task_id': 'pets_001', 'title': 'XP Sharing'}
                ]
            )
        
        else:
            print(f"Unknown notification type: {notification_type}")
            sys.exit(1)
        
        if success:
            print("✓ Notification sent successfully")
        else:
            print("✗ Failed to send notification")
            sys.exit(1)
    
    except Exception as e:
        print(f"Error: {e}", file=sys.stderr)
        sys.exit(1)


if __name__ == '__main__':
    main()
