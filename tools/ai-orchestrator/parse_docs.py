"""
Task parser for extracting tasks from PLUGIN-TASKS.md.
Parses markdown with TASK_ID markers and extracts metadata.
"""

import re
import json
from pathlib import Path
from typing import List, Dict, Optional
from datetime import datetime


class TaskParser:
    """Parser for extracting tasks from markdown files."""
    
    def __init__(self, task_file: Path):
        self.task_file = Path(task_file)
    
    def parse_tasks(self) -> List[Dict]:
        """Parse all tasks from the markdown file."""
        if not self.task_file.exists():
            raise FileNotFoundError(f"Task file not found: {self.task_file}")
        
        with open(self.task_file, 'r', encoding='utf-8') as f:
            content = f.read()
        
        tasks = []
        
        # Split by task headers (## Task:)
        task_sections = re.split(r'\n## Task:', content)
        
        for section in task_sections[1:]:  # Skip first split (header)
            task = self._parse_task_section("## Task:" + section)
            if task:
                tasks.append(task)
        
        # Sort by priority (high > medium > low)
        priority_order = {"high": 0, "medium": 1, "low": 2}
        tasks.sort(key=lambda t: priority_order.get(t.get("priority", "low"), 3))
        
        return tasks
    
    def _parse_task_section(self, section: str) -> Optional[Dict]:
        """Parse a single task section."""
        # Extract task name
        name_match = re.search(r'## Task:\s*(.+?)(?:\n|$)', section)
        if not name_match:
            return None
        
        task_name = name_match.group(1).strip()
        
        # Extract metadata from HTML comments
        task_id = self._extract_metadata(section, "TASK_ID")
        priority = self._extract_metadata(section, "TASK_PRIORITY")
        module = self._extract_metadata(section, "TASK_MODULE")
        language = self._extract_metadata(section, "TASK_LANGUAGE")
        
        # Task ID is required
        if not task_id:
            return None
        
        # Extract content (everything after metadata comments)
        content_start = section.find("-->", section.rfind("<!--"))
        if content_start != -1:
            content = section[content_start + 3:].strip()
        else:
            content = section
        
        # Extract overview
        overview_match = re.search(r'###\s*Overview\s*\n(.+?)(?=\n###|\Z)', content, re.DOTALL)
        overview = overview_match.group(1).strip() if overview_match else ""
        
        # Extract requirements
        requirements_match = re.search(r'###\s*Requirements\s*\n(.+?)(?=\n###|\Z)', content, re.DOTALL)
        requirements = requirements_match.group(1).strip() if requirements_match else ""
        
        # Extract success criteria
        success_match = re.search(r'###\s*Success Criteria\s*\n(.+?)(?=\n###|\Z)', content, re.DOTALL)
        success_criteria = success_match.group(1).strip() if success_match else ""
        
        return {
            "task_id": task_id,
            "name": task_name,
            "priority": priority or "medium",
            "module": module or "zakum-core",
            "language": language or "java",
            "overview": overview,
            "requirements": requirements,
            "success_criteria": success_criteria,
            "full_content": content,
            "parsed_at": datetime.utcnow().isoformat()
        }
    
    def _extract_metadata(self, content: str, key: str) -> Optional[str]:
        """Extract metadata from HTML comment."""
        pattern = rf'<!--\s*{key}:\s*(.+?)\s*-->'
        match = re.search(pattern, content, re.IGNORECASE)
        return match.group(1).strip() if match else None
    
    def get_next_task(self, completed_tasks: List[str]) -> Optional[Dict]:
        """Get the next task that hasn't been completed."""
        tasks = self.parse_tasks()
        
        for task in tasks:
            if task["task_id"] not in completed_tasks:
                return task
        
        return None
    
    def export_to_json(self, output_file: Path):
        """Export parsed tasks to JSON file."""
        tasks = self.parse_tasks()
        
        with open(output_file, 'w', encoding='utf-8') as f:
            json.dump(tasks, f, indent=2, ensure_ascii=False)
        
        return len(tasks)


class CompletedTasksTracker:
    """Tracks which tasks have been completed."""
    
    def __init__(self, tracking_file: Path):
        self.tracking_file = Path(tracking_file)
        self.completed = self._load_completed()
    
    def _load_completed(self) -> List[str]:
        """Load completed task IDs."""
        if self.tracking_file.exists():
            with open(self.tracking_file, 'r') as f:
                data = json.load(f)
                return data.get("completed_task_ids", [])
        return []
    
    def mark_completed(self, task_id: str, pr_url: Optional[str] = None):
        """Mark a task as completed."""
        if task_id not in self.completed:
            self.completed.append(task_id)
        
        self._save_completed(pr_url)
    
    def _save_completed(self, pr_url: Optional[str] = None):
        """Save completed tasks to file."""
        data = {
            "completed_task_ids": self.completed,
            "total_completed": len(self.completed),
            "last_updated": datetime.utcnow().isoformat()
        }
        
        if pr_url:
            data["last_pr_url"] = pr_url
        
        with open(self.tracking_file, 'w') as f:
            json.dump(data, f, indent=2)
    
    def is_completed(self, task_id: str) -> bool:
        """Check if a task has been completed."""
        return task_id in self.completed
    
    def get_completed_count(self) -> int:
        """Get count of completed tasks."""
        return len(self.completed)


if __name__ == "__main__":
    # Test the parser
    import sys
    from pathlib import Path
    
    # Default to the docs directory
    repo_root = Path(__file__).parent.parent.parent
    task_file = repo_root / "docs" / "PLUGIN-TASKS.md"
    
    if not task_file.exists():
        print(f"Task file not found: {task_file}")
        sys.exit(1)
    
    parser = TaskParser(task_file)
    tasks = parser.parse_tasks()
    
    print(f"Parsed {len(tasks)} tasks:\n")
    
    for task in tasks:
        print(f"ID: {task['task_id']}")
        print(f"Name: {task['name']}")
        print(f"Priority: {task['priority']}")
        print(f"Module: {task['module']}")
        print(f"Language: {task['language']}")
        print(f"Overview: {task['overview'][:100]}...")
        print("-" * 80)
    
    # Export to JSON
    output_file = repo_root / "tools" / "ai-orchestrator" / "parsed_tasks.json"
    count = parser.export_to_json(output_file)
    print(f"\nExported {count} tasks to {output_file}")
