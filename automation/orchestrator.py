"""
Main orchestrator for AI automation system.
Handles cost tracking, budget enforcement, and model execution coordination.
"""

import os
import json
import sys
from datetime import datetime
from typing import Dict, Any, Optional, Tuple
from pathlib import Path

from config import (
    CLAUDE_MODEL, OPENAI_MODEL,
    CLAUDE_INPUT_COST, CLAUDE_OUTPUT_COST,
    OPENAI_INPUT_COST, OPENAI_OUTPUT_COST,
    MONTHLY_BUDGET, NOTIFICATION_THRESHOLDS,
    BUDGET_FILE, ESTIMATED_COST_PER_EXECUTION
)
from discord_notifier import DiscordNotifier


class BudgetTracker:
    """Tracks and manages budget spending."""
    
    def __init__(self, budget_file: str = BUDGET_FILE):
        """Initialize budget tracker with file path."""
        self.budget_file = Path(budget_file)
        self.data = self._load_budget_data()
        self.notifier = DiscordNotifier()
    
    def _load_budget_data(self) -> Dict[str, Any]:
        """Load budget data from JSON file."""
        if not self.budget_file.exists():
            return self._create_new_budget_data()
        
        try:
            with open(self.budget_file, 'r') as f:
                data = json.load(f)
            
            # Check if we need to reset for new month
            current_month = datetime.now().strftime("%Y-%m")
            if data.get('current_month') != current_month:
                print(f"New month detected. Resetting budget from {data.get('current_month')} to {current_month}")
                return self._reset_for_new_month(data)
            
            return data
        except Exception as e:
            print(f"Error loading budget data: {e}. Creating new file.")
            return self._create_new_budget_data()
    
    def _create_new_budget_data(self) -> Dict[str, Any]:
        """Create new budget data structure."""
        return {
            "version": "1.0",
            "current_month": datetime.now().strftime("%Y-%m"),
            "total_spent": 0.0,
            "executions": 0,
            "prs_generated": 0,
            "tokens_used": {
                "claude_input": 0,
                "claude_output": 0,
                "openai_input": 0,
                "openai_output": 0
            },
            "milestones_reached": [],
            "last_reset": datetime.now().isoformat(),
            "history": []
        }
    
    def _reset_for_new_month(self, old_data: Dict[str, Any]) -> Dict[str, Any]:
        """Reset budget for new month, archiving old data."""
        # Archive old month data
        old_month = old_data.get('current_month', 'unknown')
        archive_entry = {
            "month": old_month,
            "total_spent": old_data.get('total_spent', 0.0),
            "prs_generated": old_data.get('prs_generated', 0),
            "executions": old_data.get('executions', 0),
            "milestones_reached": old_data.get('milestones_reached', [])
        }
        
        # Send monthly recap
        self.notifier.send_monthly_recap(old_data)
        
        # Create new month data
        new_data = self._create_new_budget_data()
        new_data['history'] = old_data.get('history', [])
        new_data['history'].append(archive_entry)
        
        return new_data
    
    def _save_budget_data(self):
        """Save budget data to JSON file."""
        try:
            with open(self.budget_file, 'w') as f:
                json.dump(self.data, f, indent=2)
        except Exception as e:
            print(f"Error saving budget data: {e}")
    
    def check_budget_available(self) -> Tuple[bool, float]:
        """
        Check if budget is available for another execution.
        Returns: (is_available, remaining_budget)
        """
        total_spent = self.data.get('total_spent', 0.0)
        remaining = MONTHLY_BUDGET - total_spent
        
        # Check if we have enough budget for another execution
        is_available = remaining >= ESTIMATED_COST_PER_EXECUTION
        
        return is_available, remaining
    
    def record_execution(
        self,
        claude_input_tokens: int,
        claude_output_tokens: int,
        openai_input_tokens: int,
        openai_output_tokens: int,
        pr_created: bool = False
    ) -> Dict[str, Any]:
        """
        Record an execution and calculate cost.
        Returns: Dictionary with cost information and milestone status
        """
        # Calculate costs
        claude_cost = (
            claude_input_tokens * CLAUDE_INPUT_COST +
            claude_output_tokens * CLAUDE_OUTPUT_COST
        )
        openai_cost = (
            openai_input_tokens * OPENAI_INPUT_COST +
            openai_output_tokens * OPENAI_OUTPUT_COST
        )
        total_cost = claude_cost + openai_cost
        
        # Get previous total for milestone checking
        previous_total = self.data.get('total_spent', 0.0)
        
        # Update budget data
        self.data['total_spent'] += total_cost
        self.data['executions'] += 1
        if pr_created:
            self.data['prs_generated'] += 1
        
        # Update token counts
        self.data['tokens_used']['claude_input'] += claude_input_tokens
        self.data['tokens_used']['claude_output'] += claude_output_tokens
        self.data['tokens_used']['openai_input'] += openai_input_tokens
        self.data['tokens_used']['openai_output'] += openai_output_tokens
        
        # Check for milestone
        milestone_reached = self._check_milestone(previous_total, self.data['total_spent'])
        
        # Save updated data
        self._save_budget_data()
        
        return {
            'claude_cost': claude_cost,
            'openai_cost': openai_cost,
            'total_cost': total_cost,
            'total_spent': self.data['total_spent'],
            'remaining_budget': MONTHLY_BUDGET - self.data['total_spent'],
            'milestone_reached': milestone_reached,
            'executions': self.data['executions'],
            'prs_generated': self.data['prs_generated']
        }
    
    def _check_milestone(self, previous_total: float, new_total: float) -> Optional[float]:
        """Check if a milestone was crossed and hasn't been notified yet."""
        for milestone in NOTIFICATION_THRESHOLDS:
            if previous_total < milestone <= new_total:
                # Check if we've already notified for this milestone
                if milestone not in self.data.get('milestones_reached', []):
                    self.data['milestones_reached'].append(milestone)
                    return milestone
        return None
    
    def get_budget_status(self) -> Dict[str, Any]:
        """Get current budget status."""
        return {
            'total_spent': self.data.get('total_spent', 0.0),
            'remaining': MONTHLY_BUDGET - self.data.get('total_spent', 0.0),
            'executions': self.data.get('executions', 0),
            'prs_generated': self.data.get('prs_generated', 0),
            'milestones_reached': self.data.get('milestones_reached', []),
            'current_month': self.data.get('current_month')
        }


class AIOrchestrator:
    """Orchestrates AI model execution with cost tracking."""
    
    def __init__(self):
        """Initialize orchestrator."""
        self.budget_tracker = BudgetTracker()
        self.notifier = DiscordNotifier()
    
    def can_execute(self) -> Tuple[bool, str]:
        """
        Check if execution can proceed.
        Returns: (can_proceed, message)
        """
        is_available, remaining = self.budget_tracker.check_budget_available()
        
        if not is_available:
            message = f"Budget exhausted. Spent: ${MONTHLY_BUDGET - remaining:.2f}, Remaining: ${remaining:.2f}"
            return False, message
        
        message = f"Budget available. Remaining: ${remaining:.2f}"
        return True, message
    
    def execute_analysis_with_claude(self, task_description: str) -> Tuple[str, int, int]:
        """
        Execute task analysis with Claude Sonnet 4.5.
        Returns: (specification, input_tokens, output_tokens)
        
        NOTE: This is a placeholder. Actual implementation would call Claude API.
        """
        print(f"[CLAUDE] Analyzing task: {task_description[:100]}...")
        
        # Placeholder for actual Claude API call
        # In production, this would use the Anthropic API
        specification = f"""
        SPECIFICATION for task: {task_description}
        
        Requirements:
        1. Implement feature as described
        2. Follow existing code patterns
        3. Add appropriate error handling
        4. Include documentation
        """
        
        # Estimate token usage (in production, these come from API response)
        input_tokens = len(task_description.split()) * 2  # Rough estimate
        output_tokens = len(specification.split()) * 2
        
        return specification, input_tokens, output_tokens
    
    def execute_coding_with_gpt4o_mini(self, specification: str) -> Tuple[str, int, int]:
        """
        Execute code generation with GPT-4o Mini.
        Returns: (code, input_tokens, output_tokens)
        
        NOTE: This is a placeholder. Actual implementation would call OpenAI API.
        """
        print(f"[GPT-4o-Mini] Generating code from specification...")
        
        # Placeholder for actual OpenAI API call
        # In production, this would use the OpenAI API
        code = f"""
        // Generated code based on specification
        // {specification[:100]}...
        
        public class GeneratedImplementation {{
            // Implementation here
        }}
        """
        
        # Estimate token usage (in production, these come from API response)
        input_tokens = len(specification.split()) * 2
        output_tokens = len(code.split()) * 2
        
        return code, input_tokens, output_tokens
    
    def execute_full_workflow(self, task_description: str) -> Dict[str, Any]:
        """
        Execute full workflow: check budget, analyze with Claude, code with GPT-4o Mini.
        Returns: Dictionary with results and cost information
        """
        # Check if we can execute
        can_proceed, message = self.can_execute()
        if not can_proceed:
            print(f"Cannot execute: {message}")
            self.notifier.send_budget_exceeded(
                self.budget_tracker.data.get('total_spent', 0.0),
                self.budget_tracker.data.get('prs_generated', 0)
            )
            return {
                'success': False,
                'error': message
            }
        
        print(f"Starting execution: {message}")
        
        # Get current execution number
        execution_number = self.budget_tracker.data.get('executions', 0) + 1
        
        # Notify execution start
        budget_status = self.budget_tracker.get_budget_status()
        self.notifier.send_execution_start(
            execution_number,
            budget_status['remaining']
        )
        
        try:
            # Step 1: Claude Sonnet analysis
            specification, claude_input, claude_output = self.execute_analysis_with_claude(task_description)
            
            # Step 2: GPT-4o Mini code generation
            code, openai_input, openai_output = self.execute_coding_with_gpt4o_mini(specification)
            
            # Step 3: Record execution and costs
            cost_info = self.budget_tracker.record_execution(
                claude_input_tokens=claude_input,
                claude_output_tokens=claude_output,
                openai_input_tokens=openai_input,
                openai_output_tokens=openai_output,
                pr_created=True  # Assuming PR is created successfully
            )
            
            print(f"Execution complete. Cost: ${cost_info['total_cost']:.4f}")
            print(f"Total spent: ${cost_info['total_spent']:.2f} / ${MONTHLY_BUDGET:.2f}")
            print(f"Remaining: ${cost_info['remaining_budget']:.2f}")
            
            # Step 4: Send notifications
            self.notifier.send_execution_complete(
                execution_number=execution_number,
                cost=cost_info['total_cost'],
                pr_number=execution_number,  # Placeholder
                budget_remaining=cost_info['remaining_budget']
            )
            
            # Step 5: Check for milestone
            if cost_info['milestone_reached']:
                print(f"Milestone reached: ${cost_info['milestone_reached']:.2f}!")
                self.notifier.send_milestone_notification(
                    milestone=cost_info['milestone_reached'],
                    total_spent=cost_info['total_spent'],
                    prs_generated=cost_info['prs_generated'],
                    executions=cost_info['executions']
                )
            
            return {
                'success': True,
                'specification': specification,
                'code': code,
                'cost_info': cost_info
            }
            
        except Exception as e:
            error_msg = f"Execution failed: {str(e)}"
            print(error_msg)
            self.notifier.send_error(error_msg, execution_number)
            return {
                'success': False,
                'error': error_msg
            }


def main():
    """Main entry point for orchestrator."""
    print("=" * 60)
    print("AI Orchestrator - Sonnet 4.5 + GPT-4o Mini")
    print(f"Monthly Budget: ${MONTHLY_BUDGET}")
    print(f"Estimated cost per execution: ${ESTIMATED_COST_PER_EXECUTION:.4f}")
    print("=" * 60)
    
    orchestrator = AIOrchestrator()
    
    # Get budget status
    status = orchestrator.budget_tracker.get_budget_status()
    print(f"\nCurrent Status:")
    print(f"  Month: {status['current_month']}")
    print(f"  Spent: ${status['total_spent']:.2f}")
    print(f"  Remaining: ${status['remaining']:.2f}")
    print(f"  Executions: {status['executions']}")
    print(f"  PRs: {status['prs_generated']}")
    print(f"  Milestones: {status['milestones_reached']}")
    
    # Check if we can execute
    can_proceed, message = orchestrator.can_execute()
    print(f"\n{message}")
    
    if not can_proceed:
        print("\nExecution aborted: Budget exhausted")
        sys.exit(1)
    
    # Execute if task description provided
    if len(sys.argv) > 1:
        task_description = " ".join(sys.argv[1:])
        print(f"\nExecuting task: {task_description}")
        result = orchestrator.execute_full_workflow(task_description)
        
        if result['success']:
            print("\n✅ Execution successful!")
            sys.exit(0)
        else:
            print(f"\n❌ Execution failed: {result['error']}")
            sys.exit(1)
    else:
        print("\nNo task provided. Use: python orchestrator.py 'task description'")
        sys.exit(0)


if __name__ == "__main__":
    main()
