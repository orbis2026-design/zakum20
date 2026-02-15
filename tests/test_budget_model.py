#!/usr/bin/env python3
"""
Test script to validate budget tracking and notification thresholds.
"""
import json
import sys
import os
from datetime import datetime, timezone

# Add automation to path
sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..', 'automation'))

import config
import orchestrator
import discord_notifier


def test_budget_loading():
    """Test loading and saving budget data."""
    print("Testing budget loading...")
    budget = orchestrator.load_budget()
    assert isinstance(budget, dict), "Budget should be a dictionary"
    assert "total_spent" in budget, "Budget should have total_spent"
    assert "next_authorization_gate" in budget, "Budget should have next_authorization_gate"
    print("✅ Budget loading works")


def test_authorization_gate():
    """Test authorization gate detection."""
    print("\nTesting authorization gate detection...")
    
    # Test below gate
    budget = {
        "total_spent": 20.00,
        "next_authorization_gate": 25.00
    }
    assert not orchestrator.check_authorization_gate(budget), "Should not hit gate at $20"
    print("✅ Below gate: OK")
    
    # Test at gate
    budget["total_spent"] = 25.00
    assert orchestrator.check_authorization_gate(budget), "Should hit gate at $25"
    print("✅ At gate: OK")
    
    # Test above gate
    budget["total_spent"] = 30.00
    assert orchestrator.check_authorization_gate(budget), "Should hit gate above $25"
    print("✅ Above gate: OK")


def test_milestone_detection():
    """Test $5 milestone detection."""
    print("\nTesting milestone detection...")
    
    # Test crossing $5 milestone
    budget = {"total_spent": 5.50}
    assert orchestrator.check_milestone(budget, 4.50), "Should detect crossing $5"
    print("✅ Crossing milestone: OK")
    
    # Test not crossing milestone
    budget = {"total_spent": 5.50}
    assert not orchestrator.check_milestone(budget, 5.00), "Should not detect within same milestone"
    print("✅ Within milestone: OK")
    
    # Test multiple milestones
    budget = {"total_spent": 12.00}
    assert orchestrator.check_milestone(budget, 7.50), "Should detect crossing from $5 to $10 range"
    print("✅ Multiple milestones: OK")


def test_cost_calculation():
    """Test execution cost calculation."""
    print("\nTesting cost calculation...")
    
    # Test with example token counts
    # Typical execution: ~5000 Claude input, ~5000 Claude output, ~5000 OpenAI input, ~5000 OpenAI output
    cost = orchestrator.calculate_execution_cost(
        claude_input_tokens=5000,
        claude_output_tokens=5000,
        openai_input_tokens=5000,
        openai_output_tokens=5000
    )
    
    expected = (
        5000 * config.CLAUDE_INPUT_COST +
        5000 * config.CLAUDE_OUTPUT_COST +
        5000 * config.OPENAI_INPUT_COST +
        5000 * config.OPENAI_OUTPUT_COST
    )
    
    assert abs(cost - expected) < 0.001, f"Cost calculation incorrect: {cost} != {expected}"
    print(f"✅ Cost calculation: ${cost:.6f}")


def test_budget_update():
    """Test budget update logic."""
    print("\nTesting budget update...")
    
    budget = {
        "total_spent": 10.00,
        "total_prs_generated": 182,
        "total_tokens_used": 1820000
    }
    
    updated_budget, previous_spent = orchestrator.update_budget(
        budget, cost=0.055, tokens_used=10000, prs_generated=1
    )
    
    assert updated_budget["total_spent"] == 10.05, "Total spent should be 10.05"
    assert updated_budget["total_prs_generated"] == 183, "PRs should be 183"
    assert updated_budget["total_tokens_used"] == 1830000, "Tokens should be 1830000"
    assert previous_spent == 10.00, "Previous spent should be 10.00"
    print("✅ Budget update works")


def test_authorization_flow():
    """Test authorization flow."""
    print("\nTesting authorization flow...")
    
    # Create test budget at gate
    budget = {
        "total_spent": 25.00,
        "total_prs_generated": 454,
        "total_tokens_used": 4540000,
        "authorization_blocks": [],
        "next_authorization_gate": 25.00,
        "workflow_status": "RUNNING"
    }
    
    # Authorize next block
    authorized = orchestrator.authorize_next_block(budget)
    
    assert authorized["next_authorization_gate"] == 50.00, "Next gate should be $50"
    assert len(authorized["authorization_blocks"]) == 1, "Should have 1 auth block"
    assert authorized["authorization_blocks"][0]["gate_amount"] == 25.00, "Gate amount should be $25"
    print("✅ Authorization flow works")


def test_notification_formatting():
    """Test notification message formatting."""
    print("\nTesting notification formatting...")
    
    # Test milestone notification
    budget = {
        "total_spent": 5.00,
        "total_prs_generated": 91,
        "total_tokens_used": 910000,
        "next_authorization_gate": 25.00
    }
    
    milestone_msg = discord_notifier.format_milestone_notification(budget)
    assert "💰" in milestone_msg, "Should have money emoji"
    assert "$5.00" in milestone_msg, "Should show $5.00"
    assert "91" in milestone_msg, "Should show PR count"
    print("✅ Milestone notification format OK")
    
    # Test authorization gate notification
    budget["total_spent"] = 25.00
    budget["total_prs_generated"] = 454
    budget["total_tokens_used"] = 4540000
    
    gate_msg = discord_notifier.format_authorization_gate_notification(budget)
    assert "⛔" in gate_msg, "Should have stop emoji"
    assert "$25.00" in gate_msg, "Should show $25.00"
    assert "454" in gate_msg, "Should show PR count"
    assert "Action Required" in gate_msg, "Should have action required text"
    print("✅ Authorization gate notification format OK")


def test_execution_counts():
    """Test that execution counts match expected values."""
    print("\nTesting execution counts...")
    
    # At $0.055 per execution:
    # $25 / $0.055 = 454 executions
    executions_per_gate = config.AUTHORIZATION_GATE / 0.055
    assert abs(executions_per_gate - 454) < 2, f"Should be ~454 executions per gate, got {executions_per_gate}"
    print(f"✅ Executions per gate: {executions_per_gate:.0f}")
    
    # $5 notifications: 91 executions
    executions_per_notification = config.NOTIFICATION_INTERVAL / 0.055
    assert abs(executions_per_notification - 91) < 2, f"Should be ~91 executions per notification, got {executions_per_notification}"
    print(f"✅ Executions per notification: {executions_per_notification:.0f}")
    
    # 5 notifications before gate
    notifications_per_gate = config.AUTHORIZATION_GATE / config.NOTIFICATION_INTERVAL
    assert notifications_per_gate == 5, f"Should be 5 notifications per gate, got {notifications_per_gate}"
    print(f"✅ Notifications per gate: {notifications_per_gate:.0f}")


def main():
    """Run all tests."""
    print("="*70)
    print("Budget Model Test Suite")
    print("="*70)
    
    try:
        test_budget_loading()
        test_authorization_gate()
        test_milestone_detection()
        test_cost_calculation()
        test_budget_update()
        test_authorization_flow()
        test_notification_formatting()
        test_execution_counts()
        
        print("\n" + "="*70)
        print("✅ All tests passed!")
        print("="*70)
        return 0
    except AssertionError as e:
        print(f"\n❌ Test failed: {e}")
        return 1
    except Exception as e:
        print(f"\n❌ Unexpected error: {e}")
        import traceback
        traceback.print_exc()
        return 1


if __name__ == "__main__":
    sys.exit(main())
