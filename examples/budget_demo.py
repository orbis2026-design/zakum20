#!/usr/bin/env python3
"""
Example script showing how the budget tracking system works.
This simulates a complete workflow from initial state through multiple authorization gates.
"""
import sys
import os

# Add automation to path
sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..', 'automation'))

import config
import orchestrator
import discord_notifier


def print_separator():
    """Print a visual separator."""
    print("\n" + "="*70 + "\n")


def simulate_execution_batch(num_executions=91):
    """Simulate a batch of executions (one notification interval)."""
    print(f"Simulating {num_executions} PR generations...")
    
    # Load current budget
    budget = orchestrator.load_budget()
    previous_spent = budget.get("total_spent", 0)
    
    # Simulate executions
    cost_per_execution = 0.055
    tokens_per_execution = 10000
    
    for i in range(num_executions):
        budget, prev = orchestrator.update_budget(
            budget, 
            cost=cost_per_execution, 
            tokens_used=tokens_per_execution, 
            prs_generated=1
        )
    
    # Check for milestone
    if orchestrator.check_milestone(budget, previous_spent):
        print(f"\n📢 Milestone reached!")
        msg = discord_notifier.format_milestone_notification(budget)
        print(msg)
    
    # Check for authorization gate
    if orchestrator.check_authorization_gate(budget):
        print(f"\n⛔ Authorization gate reached!")
        msg = discord_notifier.format_authorization_gate_notification(budget)
        print(msg)
        orchestrator.save_budget(budget)
        return True  # Need authorization
    
    orchestrator.save_budget(budget)
    return False  # Can continue


def show_current_status():
    """Display current budget status."""
    budget = orchestrator.load_budget()
    print(f"Current Status:")
    print(f"  Total Spent: ${budget.get('total_spent', 0):.2f}")
    print(f"  PRs Generated: {budget.get('total_prs_generated', 0)}")
    print(f"  Tokens Used: {budget.get('total_tokens_used', 0):,}")
    print(f"  Next Gate: ${budget.get('next_authorization_gate', 25.00):.2f}")
    print(f"  Workflow Status: {budget.get('workflow_status', 'PAUSED')}")
    print(f"  Authorization Blocks: {len(budget.get('authorization_blocks', []))}")


def main():
    """Run the demonstration."""
    print("="*70)
    print("Budget Model Demonstration")
    print("="*70)
    
    # Show initial state
    print("\n📋 Initial State:")
    show_current_status()
    
    print_separator()
    
    # Simulate $0-$5 (91 PRs)
    print("🔄 Phase 1: $0-$5 (91 PRs)")
    print("-"*70)
    need_auth = simulate_execution_batch(91)
    show_current_status()
    
    if need_auth:
        print("\n⚠️  Unexpected: Hit gate early!")
        return
    
    print_separator()
    
    # Simulate $5-$10 (91 PRs)
    print("🔄 Phase 2: $5-$10 (91 PRs)")
    print("-"*70)
    need_auth = simulate_execution_batch(91)
    show_current_status()
    
    if need_auth:
        print("\n⚠️  Unexpected: Hit gate early!")
        return
    
    print_separator()
    
    # Simulate $10-$15 (91 PRs)
    print("🔄 Phase 3: $10-$15 (91 PRs)")
    print("-"*70)
    need_auth = simulate_execution_batch(91)
    show_current_status()
    
    if need_auth:
        print("\n⚠️  Unexpected: Hit gate early!")
        return
    
    print_separator()
    
    # Simulate $15-$20 (91 PRs)
    print("🔄 Phase 4: $15-$20 (91 PRs)")
    print("-"*70)
    need_auth = simulate_execution_batch(91)
    show_current_status()
    
    if need_auth:
        print("\n⚠️  Unexpected: Hit gate early!")
        return
    
    print_separator()
    
    # Simulate $20-$25 (91 PRs)
    print("🔄 Phase 5: $20-$25 (91 PRs)")
    print("-"*70)
    need_auth = simulate_execution_batch(91)
    show_current_status()
    
    if not need_auth:
        print("\n⚠️  Unexpected: Should have hit gate!")
        return
    
    print_separator()
    
    # Show authorization needed
    print("🔒 Authorization Required")
    print("-"*70)
    print("At this point, the workflow would pause and wait for authorization.")
    print("User would see Discord notification and need to:")
    print("  1. React with ✅ in Discord")
    print("  2. Click 'Run workflow' in GitHub Actions")
    print("  3. Select 'authorize' action")
    
    print_separator()
    
    # Simulate authorization
    print("✅ Authorizing Next $25 Block")
    print("-"*70)
    budget = orchestrator.load_budget()
    budget = orchestrator.authorize_next_block(budget)
    orchestrator.save_budget(budget)
    print(f"Authorized! New gate: ${budget['next_authorization_gate']:.2f}")
    show_current_status()
    
    print_separator()
    
    # Continue with $25-$30
    print("🔄 Phase 6: $25-$30 (91 PRs)")
    print("-"*70)
    need_auth = simulate_execution_batch(91)
    show_current_status()
    
    print_separator()
    
    # Final summary
    print("📊 Final Summary")
    print("-"*70)
    budget = orchestrator.load_budget()
    print(f"Total Spent: ${budget.get('total_spent', 0):.2f}")
    print(f"Total PRs: {budget.get('total_prs_generated', 0)}")
    print(f"Authorization Blocks: {len(budget.get('authorization_blocks', []))}")
    print(f"Next Gate: ${budget.get('next_authorization_gate', 25.00):.2f}")
    
    print_separator()
    
    print("✅ Demonstration Complete!")
    print("\nThis demonstrates:")
    print("  • $5 notification milestones")
    print("  • $25 authorization gates")
    print("  • Cumulative budget tracking")
    print("  • Authorization flow")
    print("  • Continuous operation with unlimited re-authorizations")


if __name__ == "__main__":
    # Save current budget
    import shutil
    import json
    
    backup_file = config.BUDGET_FILE + ".backup"
    if os.path.exists(config.BUDGET_FILE):
        shutil.copy(config.BUDGET_FILE, backup_file)
        print(f"📦 Backed up current budget to {backup_file}")
    
    # Reset to initial state for demo
    initial_budget = {
        "total_spent": 0.00,
        "total_prs_generated": 0,
        "total_tokens_used": 0,
        "authorization_blocks": [],
        "next_authorization_gate": 25.00,
        "workflow_status": "PAUSED",
        "last_updated": "2026-02-15T00:00:00Z"
    }
    
    with open(config.BUDGET_FILE, 'w') as f:
        json.dump(initial_budget, f, indent=2)
    
    try:
        main()
    finally:
        # Restore backup
        if os.path.exists(backup_file):
            shutil.move(backup_file, config.BUDGET_FILE)
            print(f"\n📦 Restored original budget from backup")
