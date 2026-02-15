#!/usr/bin/env python3
"""
Documentation Parser for AI Orchestrator

Extracts structured tasks from PLUGIN-TASKS.md for AI processing.
"""

import re
import os
from dataclasses import dataclass
from typing import List, Optional, Dict, Any
from pathlib import Path


@dataclass
class Task:
    """Represents a parsed task from documentation."""
    task_id: str
    title: str
    priority: str
    module: str
    language: str
    status: str
    description: str
    full_content: str
    
    def to_dict(self) -> Dict[str, Any]:
        """Convert task to dictionary."""
        return {
            'task_id': self.task_id,
            'title': self.title,
            'priority': self.priority,
            'module': self.module,
            'language': self.language,
            'status': self.status,
            'description': self.description,
            'full_content': self.full_content
        }


class TaskParser:
    """Parser for extracting tasks from markdown documentation."""
    
    def __init__(self, docs_file: str):
        """
        Initialize parser.
        
        Args:
            docs_file: Path to PLUGIN-TASKS.md file
        """
        self.docs_file = Path(docs_file)
        if not self.docs_file.exists():
            raise FileNotFoundError(f"Documentation file not found: {docs_file}")
    
    def parse_tasks(self) -> List[Task]:
        """
        Parse all tasks from documentation.
        
        Returns:
            List of Task objects
        """
        with open(self.docs_file, 'r', encoding='utf-8') as f:
            content = f.read()
        
        tasks = []
        
        # Split by ## Task: headers
        task_pattern = r'## Task: ([^\n]+)\n(.*?)(?=\n## Task: |\n## Completed Tasks|$)'
        matches = re.finditer(task_pattern, content, re.DOTALL)
        
        for match in matches:
            title = match.group(1).strip()
            task_content = match.group(2).strip()
            
            # Extract metadata from HTML comments
            task_id = self._extract_metadata(task_content, 'TASK_ID')
            priority = self._extract_metadata(task_content, 'TASK_PRIORITY')
            module = self._extract_metadata(task_content, 'TASK_MODULE')
            language = self._extract_metadata(task_content, 'TASK_LANGUAGE')
            status = self._extract_metadata(task_content, 'TASK_STATUS', default='pending')
            
            # Skip if task_id is missing (invalid task)
            if not task_id:
                continue
            
            # Extract description (everything after metadata comments)
            description = self._extract_description(task_content)
            
            task = Task(
                task_id=task_id,
                title=title,
                priority=priority,
                module=module,
                language=language,
                status=status,
                description=description,
                full_content=f"## Task: {title}\n{task_content}"
            )
            
            tasks.append(task)
        
        return tasks
    
    def _extract_metadata(self, content: str, key: str, default: str = '') -> str:
        """
        Extract metadata value from HTML comment.
        
        Args:
            content: Task content
            key: Metadata key (e.g., 'TASK_ID')
            default: Default value if not found
            
        Returns:
            Metadata value or default
        """
        pattern = f'<!-- {key}: ([^-]+?) -->'
        match = re.search(pattern, content)
        return match.group(1).strip() if match else default
    
    def _extract_description(self, content: str) -> str:
        """
        Extract description text after metadata comments.
        
        Args:
            content: Task content
            
        Returns:
            Description text
        """
        # Remove all HTML comments
        cleaned = re.sub(r'<!--.*?-->', '', content, flags=re.DOTALL)
        return cleaned.strip()
    
    def get_pending_tasks(self, priority_order: Optional[List[str]] = None) -> List[Task]:
        """
        Get all pending tasks sorted by priority.
        
        Args:
            priority_order: List defining priority order (e.g., ['high', 'medium', 'low'])
            
        Returns:
            Sorted list of pending tasks
        """
        tasks = self.parse_tasks()
        pending = [t for t in tasks if t.status == 'pending']
        
        if priority_order:
            # Sort by priority order
            priority_map = {p: i for i, p in enumerate(priority_order)}
            pending.sort(key=lambda t: priority_map.get(t.priority, 999))
        
        return pending
    
    def get_task_by_id(self, task_id: str) -> Optional[Task]:
        """
        Get a specific task by ID.
        
        Args:
            task_id: Task identifier
            
        Returns:
            Task object or None if not found
        """
        tasks = self.parse_tasks()
        for task in tasks:
            if task.task_id == task_id:
                return task
        return None
    
    def update_task_status(self, task_id: str, new_status: str) -> bool:
        """
        Update task status in documentation file.
        
        Args:
            task_id: Task identifier
            new_status: New status value
            
        Returns:
            True if updated successfully
        """
        with open(self.docs_file, 'r', encoding='utf-8') as f:
            content = f.read()
        
        # Find and replace status for specific task
        pattern = f'(<!-- TASK_ID: {re.escape(task_id)} -->.*?<!-- TASK_STATUS: )[^-]+(?: -->)'
        replacement = f'\\g<1>{new_status} -->'
        
        new_content, count = re.subn(pattern, replacement, content, flags=re.DOTALL)
        
        if count > 0:
            with open(self.docs_file, 'w', encoding='utf-8') as f:
                f.write(new_content)
            return True
        
        return False


def main():
    """Example usage and testing."""
    import sys
    import json
    
    if len(sys.argv) < 2:
        print("Usage: python parse_docs.py <path-to-PLUGIN-TASKS.md>")
        sys.exit(1)
    
    docs_file = sys.argv[1]
    
    try:
        parser = TaskParser(docs_file)
        pending_tasks = parser.get_pending_tasks(['high', 'medium', 'low'])
        
        print(f"Found {len(pending_tasks)} pending tasks:\n")
        
        for task in pending_tasks:
            print(f"[{task.priority.upper()}] {task.task_id}: {task.title}")
            print(f"  Module: {task.module}")
            print(f"  Status: {task.status}")
            print()
        
        # Output as JSON for scripting
        if '--json' in sys.argv:
            tasks_data = [t.to_dict() for t in pending_tasks]
            print(json.dumps(tasks_data, indent=2))
    
    except Exception as e:
        print(f"Error: {e}", file=sys.stderr)
        sys.exit(1)


if __name__ == '__main__':
    main()
