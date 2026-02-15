"""
Main Orchestrator - Controls the entire AI development workflow.
Coordinates task parsing, Claude analysis, ChatGPT coding, validation, and PR creation.
"""

import sys
import json
import logging
from pathlib import Path
from datetime import datetime, timezone
from typing import Dict, Optional

# Import our modules
from config import (
    BudgetManager, get_config, validate_config,
    TASK_FILE, LOGS_DIR, GENERATED_DIR, REPO_ROOT
)
from parse_docs import TaskParser, CompletedTasksTracker
from claude_analyzer import ClaudeAnalyzer, load_context_docs
from chatgpt_coder import ChatGPTCoder
from validator import BuildValidator
from discord_notifier import DiscordNotifier
from feedback_tracker import FeedbackTracker


# Setup logging
log_file = LOGS_DIR / f"orchestrator_{datetime.now().strftime('%Y%m%d')}.log"
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s',
    handlers=[
        logging.FileHandler(log_file),
        logging.StreamHandler(sys.stdout)
    ]
)
logger = logging.getLogger(__name__)


class Orchestrator:
    """Main orchestrator for the AI development system."""
    
    def __init__(self, dry_run: bool = False):
        self.dry_run = dry_run
        self.config = get_config()
        
        # Initialize components
        self.budget_manager = BudgetManager()
        self.task_parser = TaskParser(TASK_FILE)
        self.completed_tracker = CompletedTasksTracker(
            Path(self.config["completed_tasks_file"])
        )
        self.feedback_tracker = FeedbackTracker(
            Path(self.config["merged_prs_file"])
        )
        
        # Initialize AI components (only if not dry run)
        if not dry_run:
            self.claude_analyzer = ClaudeAnalyzer(
                self.config["anthropic_api_key"],
                GENERATED_DIR
            )
            self.chatgpt_coder = ChatGPTCoder(
                self.config["openai_api_key"],
                GENERATED_DIR
            )
        
        self.validator = BuildValidator(REPO_ROOT)
        self.discord = DiscordNotifier(self.config["discord_webhook_url"])
    
    def run(self) -> Dict:
        """
        Run one iteration of the orchestrator.
        
        Returns:
            Dict with execution results
        """
        logger.info("=" * 80)
        logger.info("Starting AI Development Orchestrator")
        logger.info("=" * 80)
        
        # Validate configuration
        config_errors = validate_config()
        if config_errors and not self.dry_run:
            logger.error("Configuration errors:")
            for error in config_errors:
                logger.error(f"  - {error}")
            return {"success": False, "error": "Configuration validation failed"}
        
        # Check budget
        if not self.budget_manager.can_execute_task():
            logger.warning("Budget limit reached, cannot execute task")
            remaining = self.budget_manager.get_remaining_budget()
            return {
                "success": False,
                "error": f"Budget exhausted (${remaining:.2f} remaining)"
            }
        
        # Check budget warning level
        warning_level = self.budget_manager.get_warning_level()
        if warning_level in ["orange", "red"]:
            logger.warning(f"Budget warning level: {warning_level}")
            self.discord.send_budget_alert(
                self.budget_manager.budget,
                warning_level
            )
        
        # Get next task
        task = self.task_parser.get_next_task(self.completed_tracker.completed)
        
        if not task:
            logger.info("No more tasks to process")
            return {"success": True, "message": "No tasks remaining"}
        
        logger.info(f"Processing task: {task['task_id']} - {task['name']}")
        logger.info(f"  Priority: {task['priority']}")
        logger.info(f"  Module: {task['module']}")
        
        # Execute task
        result = self._execute_task(task)
        
        # Record completion if successful
        if result.get("success"):
            self.completed_tracker.mark_completed(
                task["task_id"],
                result.get("pr_url")
            )
            self.budget_manager.record_task_completed()
        
        logger.info("=" * 80)
        logger.info(f"Orchestrator run completed: {'SUCCESS' if result.get('success') else 'FAILED'}")
        logger.info("=" * 80)
        
        return result
    
    def _execute_task(self, task: Dict) -> Dict:
        """Execute a single task through the full workflow."""
        result = {
            "success": False,
            "task_id": task["task_id"],
            "task_name": task["name"],
            "steps": {}
        }
        
        try:
            # Step 1: Notify start
            logger.info("Step 1: Sending start notification")
            self.discord.send_notification(task, "started")
            result["steps"]["notification_sent"] = True
            
            # Step 2: Load context documentation
            logger.info("Step 2: Loading context documentation")
            context_docs = load_context_docs(task, REPO_ROOT / "docs")
            result["steps"]["context_loaded"] = True
            
            # Step 3: Generate specification with Claude
            logger.info("Step 3: Generating specification with Claude")
            
            if self.dry_run:
                logger.info("  [DRY RUN] Skipping Claude API call")
                spec_result = {
                    "specification": "DRY RUN SPECIFICATION",
                    "input_tokens": 0,
                    "output_tokens": 0
                }
            else:
                spec_result = self.claude_analyzer.generate_specification(
                    task, context_docs
                )
            
            if spec_result.get("error"):
                raise Exception(f"Claude error: {spec_result['error']}")
            
            logger.info(f"  Specification generated: {spec_result['input_tokens']} input tokens, "
                       f"{spec_result['output_tokens']} output tokens")
            
            # Record Claude spending
            claude_cost = self.budget_manager.calculate_cost(
                "claude",
                spec_result["input_tokens"],
                spec_result["output_tokens"]
            )
            
            specification = spec_result["specification"]
            result["steps"]["specification_generated"] = True
            result["spec_file"] = spec_result.get("spec_file")
            
            # Notify specification generated
            self.discord.send_notification(
                task,
                "spec_generated",
                spec_summary=specification[:300] + "..."
            )
            
            # Step 4: Generate code with ChatGPT
            logger.info("Step 4: Generating code with ChatGPT")
            
            # Get relevant patterns from feedback
            patterns = self.feedback_tracker.get_relevant_patterns(task["module"])
            
            if self.dry_run:
                logger.info("  [DRY RUN] Skipping ChatGPT API call")
                code_result = {
                    "code": "DRY RUN CODE",
                    "input_tokens": 0,
                    "output_tokens": 0
                }
            else:
                code_result = self.chatgpt_coder.generate_implementation(
                    task, specification, patterns
                )
            
            if code_result.get("error"):
                raise Exception(f"ChatGPT error: {code_result['error']}")
            
            logger.info(f"  Code generated: {code_result['input_tokens']} input tokens, "
                       f"{code_result['output_tokens']} output tokens")
            
            # Record ChatGPT spending
            gpt_cost = self.budget_manager.calculate_cost(
                "gpt4",
                code_result["input_tokens"],
                code_result["output_tokens"]
            )
            
            code = code_result["code"]
            result["steps"]["code_generated"] = True
            result["impl_file"] = code_result.get("impl_file")
            
            # Total cost for this task
            total_cost = claude_cost + gpt_cost
            logger.info(f"  Total cost: ${total_cost:.4f}")
            
            self.budget_manager.record_spending(
                total_cost,
                spec_result["input_tokens"] + spec_result["output_tokens"] +
                code_result["input_tokens"] + code_result["output_tokens"]
            )
            
            # Notify code generated
            self.discord.send_notification(
                task,
                "code_generated",
                code_summary=f"Generated implementation for {task['module']}"
            )
            
            # Step 5: Validate build (dry run skips actual changes)
            logger.info("Step 5: Validating build")
            
            if self.dry_run:
                logger.info("  [DRY RUN] Skipping build validation")
                validation_result = {
                    "success": True,
                    "build_output": "DRY RUN - SKIPPED",
                    "errors": [],
                    "warnings": [],
                    "timestamp": datetime.now(timezone.utc).isoformat()
                }
            else:
                # In a real implementation, we would:
                # 1. Apply the generated code to files
                # 2. Run gradle build
                # For now, just validate the current build
                validation_result = self.validator.validate_build(task["module"])
            
            logger.info(f"  Build validation: {'PASSED' if validation_result['success'] else 'FAILED'}")
            
            if not validation_result["success"]:
                logger.error("Build validation failed:")
                for error in validation_result["errors"][:5]:
                    logger.error(f"    {error}")
            
            result["steps"]["validation_run"] = True
            result["build_success"] = validation_result["success"]
            result["validation_report"] = self.validator.generate_report(validation_result)
            
            # Step 6: Record PR generated (simulated for now)
            logger.info("Step 6: Recording PR generation")
            self.budget_manager.record_pr_generated()
            result["steps"]["pr_generated"] = True
            
            # In a real implementation, we would create an actual PR here
            # For now, we'll just simulate it
            pr_url = f"https://github.com/orbis2026-design/zakum20/pull/simulated-{task['task_id']}"
            result["pr_url"] = pr_url
            
            # Step 7: Send completion notification
            logger.info("Step 7: Sending completion notification")
            self.discord.send_notification(
                task,
                "completed",
                spec_summary=specification[:200] + "...",
                code_summary=f"Implementation generated for {task['module']}",
                pr_url=pr_url,
                build_success=validation_result["success"]
            )
            
            result["success"] = True
            result["cost"] = total_cost
            
        except Exception as e:
            logger.error(f"Task execution failed: {str(e)}", exc_info=True)
            result["error"] = str(e)
            
            # Send failure notification
            self.discord.send_notification(
                task,
                "failed",
                error_message=str(e)
            )
        
        return result


def main():
    """Main entry point."""
    import argparse
    
    parser = argparse.ArgumentParser(description="AI Development Orchestrator")
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Run in dry-run mode (no API calls)"
    )
    parser.add_argument(
        "--stats",
        action="store_true",
        help="Show statistics and exit"
    )
    
    args = parser.parse_args()
    
    if args.stats:
        # Show statistics
        budget_mgr = BudgetManager()
        tracker = CompletedTasksTracker(REPO_ROOT / "tools" / "ai-orchestrator" / "completed_tasks.json")
        
        print("\n" + "=" * 80)
        print("AI DEVELOPMENT SYSTEM STATISTICS")
        print("=" * 80)
        print(f"Phase: {budget_mgr.budget['phase']}")
        print(f"Total Spent: ${budget_mgr.budget['total_spent']:.2f}")
        print(f"PRs Generated: {budget_mgr.budget['prs_generated']}")
        print(f"Tasks Completed: {budget_mgr.budget['tasks_completed']}")
        print(f"Tokens Used: {budget_mgr.budget['tokens_used']:,}")
        print(f"Can Execute: {budget_mgr.can_execute_task()}")
        print(f"Remaining Budget: ${budget_mgr.get_remaining_budget():.2f}")
        print(f"Warning Level: {budget_mgr.get_warning_level()}")
        print("=" * 80 + "\n")
        return 0
    
    # Run orchestrator
    orchestrator = Orchestrator(dry_run=args.dry_run)
    result = orchestrator.run()
    
    # Exit with appropriate code
    return 0 if result.get("success") else 1


if __name__ == "__main__":
    sys.exit(main())
