#!/usr/bin/env python3
"""
Demonstration script for AI orchestration system.
Shows budget tracking, milestone notifications, and cost calculations.
"""

import sys
import os

# Add parent directory to path
sys.path.insert(0, os.path.dirname(__file__))

from orchestrator import BudgetTracker
from discord_notifier import DiscordNotifier


def demo_budget_status():
    """Display current budget status."""
    print("=" * 70)
    print("BUDGET STATUS")
    print("=" * 70)
    
    tracker = BudgetTracker()
    status = tracker.get_budget_status()
    
    print(f"Month: {status['current_month']}")
    print(f"Total Spent: ${status['total_spent']:.2f}")
    print(f"Remaining: ${status['remaining']:.2f}")
    print(f"Executions: {status['executions']}")
    print(f"PRs Generated: {status['prs_generated']}")
    print(f"Milestones Reached: {status['milestones_reached']}")
    print()


def demo_single_execution():
    """Demonstrate a single execution."""
    print("=" * 70)
    print("SINGLE EXECUTION DEMO")
    print("=" * 70)
    
    tracker = BudgetTracker()
    
    # Check if we can execute
    can_proceed, remaining = tracker.check_budget_available()
    print(f"Budget Available: {can_proceed}")
    print(f"Remaining: ${remaining:.2f}")
    
    if not can_proceed:
        print("\n⛔ Budget exhausted! Cannot execute.")
        return
    
    print("\nExecuting...")
    
    # Record execution with typical token usage
    result = tracker.record_execution(
        claude_input_tokens=2000,
        claude_output_tokens=1500,
        openai_input_tokens=2500,
        openai_output_tokens=4000,
        pr_created=True
    )
    
    print(f"\n✅ Execution Complete!")
    print(f"Claude Cost: ${result['claude_cost']:.4f}")
    print(f"OpenAI Cost: ${result['openai_cost']:.4f}")
    print(f"Total Cost: ${result['total_cost']:.4f}")
    print(f"Total Spent: ${result['total_spent']:.2f}")
    print(f"Remaining Budget: ${result['remaining_budget']:.2f}")
    
    if result['milestone_reached']:
        print(f"\n🎉 MILESTONE REACHED: ${result['milestone_reached']:.2f}!")
    
    print()


def demo_milestone_simulation(target_milestone=5.0):
    """Simulate executions until reaching a milestone."""
    print("=" * 70)
    print(f"MILESTONE SIMULATION - Target: ${target_milestone:.2f}")
    print("=" * 70)
    
    tracker = BudgetTracker()
    notifier = DiscordNotifier()
    
    initial_spent = tracker.data['total_spent']
    print(f"Starting from: ${initial_spent:.2f}")
    print(f"Need to reach: ${target_milestone:.2f}")
    print(f"Gap: ${target_milestone - initial_spent:.2f}")
    print()
    
    # Calculate approximate executions needed using config value
    from config import ESTIMATED_COST_PER_EXECUTION
    needed = int((target_milestone - initial_spent) / ESTIMATED_COST_PER_EXECUTION) + 2
    print(f"Estimated executions needed: {needed}")
    print()
    
    execution_count = 0
    milestone_hit = False
    
    while tracker.data['total_spent'] < target_milestone + 0.1 and execution_count < needed + 10:
        result = tracker.record_execution(
            claude_input_tokens=2000,
            claude_output_tokens=1500,
            openai_input_tokens=2500,
            openai_output_tokens=4000,
            pr_created=True
        )
        
        execution_count += 1
        
        # Show progress every 20 executions
        if execution_count % 20 == 0:
            print(f"Execution #{execution_count}: ${result['total_spent']:.2f}")
        
        if result['milestone_reached']:
            print(f"\n✨ MILESTONE HIT at execution #{execution_count}!")
            print(f"   Target: ${result['milestone_reached']:.2f}")
            print(f"   Actual: ${result['total_spent']:.2f}")
            print(f"   PRs: {result['prs_generated']}")
            milestone_hit = True
            
            # Simulate notification
            notifier.send_milestone_notification(
                milestone=result['milestone_reached'],
                total_spent=result['total_spent'],
                prs_generated=result['prs_generated'],
                executions=result['executions']
            )
            break
    
    print(f"\nFinal Status:")
    print(f"  Executions: {execution_count}")
    print(f"  Total Spent: ${tracker.data['total_spent']:.2f}")
    print(f"  Milestone Hit: {milestone_hit}")
    print()


def demo_cost_calculations():
    """Show cost calculation details."""
    print("=" * 70)
    print("COST CALCULATIONS")
    print("=" * 70)
    
    from config import (
        CLAUDE_INPUT_COST, CLAUDE_OUTPUT_COST,
        OPENAI_INPUT_COST, OPENAI_OUTPUT_COST,
        ESTIMATED_COST_PER_EXECUTION, MONTHLY_BUDGET
    )
    
    print("\nModel Pricing:")
    print(f"  Claude Sonnet 4.5 Input:  ${CLAUDE_INPUT_COST * 1_000_000:.2f} per 1M tokens")
    print(f"  Claude Sonnet 4.5 Output: ${CLAUDE_OUTPUT_COST * 1_000_000:.2f} per 1M tokens")
    print(f"  GPT-4o Mini Input:        ${OPENAI_INPUT_COST * 1_000_000:.2f} per 1M tokens")
    print(f"  GPT-4o Mini Output:       ${OPENAI_OUTPUT_COST * 1_000_000:.2f} per 1M tokens")
    
    print("\nTypical Execution:")
    claude_cost = (2000 * CLAUDE_INPUT_COST) + (1500 * CLAUDE_OUTPUT_COST)
    openai_cost = (2500 * OPENAI_INPUT_COST) + (4000 * OPENAI_OUTPUT_COST)
    total_cost = claude_cost + openai_cost
    
    print(f"  Claude Analysis:  2000 input + 1500 output = ${claude_cost:.4f}")
    print(f"  GPT-4o Code Gen:  2500 input + 4000 output = ${openai_cost:.4f}")
    print(f"  Total Cost:       ${total_cost:.4f}")
    
    print(f"\nMonthly Budget: ${MONTHLY_BUDGET:.2f}")
    max_executions = int(MONTHLY_BUDGET / total_cost)
    print(f"Maximum Executions: {max_executions}")
    print(f"Expected PRs: {max_executions}")
    print(f"Daily Rate: {max_executions / 30:.1f} PRs/day")
    print()


def main():
    """Main demo function."""
    if len(sys.argv) > 1:
        command = sys.argv[1].lower()
        
        if command == "status":
            demo_budget_status()
        elif command == "execute":
            demo_single_execution()
        elif command == "milestone":
            target = float(sys.argv[2]) if len(sys.argv) > 2 else 5.0
            demo_milestone_simulation(target)
        elif command == "costs":
            demo_cost_calculations()
        else:
            print(f"Unknown command: {command}")
            print("\nAvailable commands:")
            print("  status    - Show current budget status")
            print("  execute   - Simulate single execution")
            print("  milestone [amount] - Simulate until milestone (default: 5.0)")
            print("  costs     - Show cost calculations")
    else:
        # Run all demos
        demo_cost_calculations()
        demo_budget_status()
        print("\nFor more demos, run:")
        print("  python demo.py status")
        print("  python demo.py execute")
        print("  python demo.py milestone 5.0")
        print("  python demo.py costs")


if __name__ == "__main__":
    main()
