"""
Feedback Tracker - Tracks merged PRs and learns from successful patterns.
Stores patterns to improve future code generation.
"""

import json
from pathlib import Path
from typing import Dict, List, Optional
from datetime import datetime


class FeedbackTracker:
    """Tracks successful patterns from merged PRs."""
    
    def __init__(self, tracking_file: Path):
        self.tracking_file = Path(tracking_file)
        self.data = self._load_data()
    
    def _load_data(self) -> Dict:
        """Load tracking data from file."""
        if self.tracking_file.exists():
            with open(self.tracking_file, 'r') as f:
                return json.load(f)
        else:
            return {
                "merged_prs": [],
                "successful_patterns": {},
                "common_fixes": [],
                "best_practices": [],
                "last_updated": datetime.utcnow().isoformat()
            }
    
    def _save_data(self):
        """Save tracking data to file."""
        self.data["last_updated"] = datetime.utcnow().isoformat()
        with open(self.tracking_file, 'w') as f:
            json.dump(self.data, f, indent=2)
    
    def record_merged_pr(
        self,
        pr_url: str,
        task_id: str,
        task_name: str,
        module: str,
        code_summary: str,
        review_comments: Optional[List[str]] = None
    ):
        """Record a successfully merged PR."""
        pr_data = {
            "pr_url": pr_url,
            "task_id": task_id,
            "task_name": task_name,
            "module": module,
            "code_summary": code_summary,
            "review_comments": review_comments or [],
            "merged_at": datetime.utcnow().isoformat()
        }
        
        self.data["merged_prs"].append(pr_data)
        
        # Limit to last 100 PRs
        if len(self.data["merged_prs"]) > 100:
            self.data["merged_prs"] = self.data["merged_prs"][-100:]
        
        self._save_data()
    
    def extract_pattern(
        self,
        pattern_name: str,
        pattern_description: str,
        code_snippet: str,
        applicable_modules: List[str]
    ):
        """Extract a successful pattern for future use."""
        pattern = {
            "description": pattern_description,
            "code_snippet": code_snippet,
            "applicable_modules": applicable_modules,
            "uses": 0,
            "created_at": datetime.utcnow().isoformat()
        }
        
        self.data["successful_patterns"][pattern_name] = pattern
        self._save_data()
    
    def get_relevant_patterns(self, module: str, task_type: str = None) -> Dict:
        """Get patterns relevant to a specific module and task type."""
        relevant = {}
        
        for pattern_name, pattern_data in self.data["successful_patterns"].items():
            # Check if pattern applies to this module
            if not pattern_data["applicable_modules"] or module in pattern_data["applicable_modules"]:
                relevant[pattern_name] = pattern_data
        
        return relevant
    
    def record_pattern_use(self, pattern_name: str):
        """Record that a pattern was used in code generation."""
        if pattern_name in self.data["successful_patterns"]:
            self.data["successful_patterns"][pattern_name]["uses"] += 1
            self._save_data()
    
    def add_common_fix(self, issue: str, fix: str):
        """Add a common fix for a recurring issue."""
        fix_data = {
            "issue": issue,
            "fix": fix,
            "added_at": datetime.utcnow().isoformat()
        }
        
        # Check if similar fix already exists
        for existing_fix in self.data["common_fixes"]:
            if existing_fix["issue"] == issue:
                existing_fix["fix"] = fix
                existing_fix["updated_at"] = datetime.utcnow().isoformat()
                self._save_data()
                return
        
        self.data["common_fixes"].append(fix_data)
        self._save_data()
    
    def add_best_practice(self, practice: str, category: str = "general"):
        """Add a best practice learned from reviews."""
        practice_data = {
            "practice": practice,
            "category": category,
            "added_at": datetime.utcnow().isoformat()
        }
        
        # Avoid duplicates
        for existing in self.data["best_practices"]:
            if existing["practice"] == practice:
                return
        
        self.data["best_practices"].append(practice_data)
        self._save_data()
    
    def get_improvement_suggestions(self) -> Dict:
        """Get suggestions for improving future code generation."""
        suggestions = {
            "common_issues": [],
            "recommended_patterns": [],
            "best_practices": []
        }
        
        # Extract common issues from fixes
        for fix in self.data["common_fixes"][-10:]:  # Last 10 fixes
            suggestions["common_issues"].append({
                "issue": fix["issue"],
                "how_to_avoid": fix["fix"]
            })
        
        # Get most-used patterns
        sorted_patterns = sorted(
            self.data["successful_patterns"].items(),
            key=lambda x: x[1]["uses"],
            reverse=True
        )
        
        for pattern_name, pattern_data in sorted_patterns[:5]:  # Top 5
            suggestions["recommended_patterns"].append({
                "name": pattern_name,
                "description": pattern_data["description"],
                "uses": pattern_data["uses"]
            })
        
        # Get recent best practices
        suggestions["best_practices"] = [
            bp["practice"] for bp in self.data["best_practices"][-10:]
        ]
        
        return suggestions
    
    def get_statistics(self) -> Dict:
        """Get statistics about merged PRs and patterns."""
        return {
            "total_merged_prs": len(self.data["merged_prs"]),
            "total_patterns": len(self.data["successful_patterns"]),
            "total_common_fixes": len(self.data["common_fixes"]),
            "total_best_practices": len(self.data["best_practices"]),
            "most_active_module": self._get_most_active_module(),
            "recent_merge_rate": self._calculate_recent_merge_rate()
        }
    
    def _get_most_active_module(self) -> Optional[str]:
        """Get the module with most merged PRs."""
        if not self.data["merged_prs"]:
            return None
        
        module_counts = {}
        for pr in self.data["merged_prs"]:
            module = pr["module"]
            module_counts[module] = module_counts.get(module, 0) + 1
        
        return max(module_counts, key=module_counts.get)
    
    def _calculate_recent_merge_rate(self) -> float:
        """Calculate merge rate for last 30 days."""
        from datetime import timedelta
        
        if not self.data["merged_prs"]:
            return 0.0
        
        cutoff = datetime.utcnow() - timedelta(days=30)
        recent_merges = [
            pr for pr in self.data["merged_prs"]
            if datetime.fromisoformat(pr["merged_at"]) > cutoff
        ]
        
        return len(recent_merges) / 30.0  # PRs per day


if __name__ == "__main__":
    from pathlib import Path
    
    # Test the tracker
    test_file = Path(__file__).parent / "test_feedback.json"
    tracker = FeedbackTracker(test_file)
    
    # Record a test PR
    tracker.record_merged_pr(
        pr_url="https://github.com/example/repo/pull/1",
        task_id="test_001",
        task_name="Test Task",
        module="zakum-core",
        code_summary="Added test feature with proper error handling"
    )
    
    # Add a pattern
    tracker.extract_pattern(
        pattern_name="folia_scheduler",
        pattern_description="Use region scheduler for Folia compatibility",
        code_snippet="entity.getScheduler().run(plugin, task -> { ... });",
        applicable_modules=["zakum-core", "zakum-battlepass"]
    )
    
    # Add a best practice
    tracker.add_best_practice(
        "Always use try-catch blocks for database operations",
        category="error_handling"
    )
    
    # Get statistics
    stats = tracker.get_statistics()
    print("Feedback Tracker Statistics:")
    print(json.dumps(stats, indent=2))
    
    # Get improvement suggestions
    suggestions = tracker.get_improvement_suggestions()
    print("\nImprovement Suggestions:")
    print(json.dumps(suggestions, indent=2))
    
    # Clean up test file
    if test_file.exists():
        test_file.unlink()
        print("\n✅ Test completed and cleaned up")
