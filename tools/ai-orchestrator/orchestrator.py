#!/usr/bin/env python3
"""
Main Orchestrator for AI Development Automation

Coordinates multi-model AI collaboration for plugin development.
"""

import os
import sys
import json
import yaml
import subprocess
from pathlib import Path
from typing import Dict, Any, Optional, List
from datetime import datetime

# Import our modules
from parse_docs import TaskParser, Task
from claude_analyzer import ClaudeAnalyzer
from chatgpt_coder import ChatGPTCoder
from validator import CodeValidator
from discord_notifier import DiscordNotifier


class AIOrchestrator:
    """Main orchestrator for AI-driven development."""
    
    def __init__(self, config_path: str = "config.yaml", project_root: Optional[str] = None):
        """
        Initialize orchestrator.
        
        Args:
            config_path: Path to configuration file
            project_root: Project root directory (defaults to current directory)
        """
        self.config = self._load_config(config_path)
        self.project_root = Path(project_root or os.getcwd())
        
        # Initialize components
        self.task_parser = TaskParser(
            str(self.project_root / self.config['docs']['tasks_file'])
        )
        
        self.claude_analyzer = ClaudeAnalyzer(
            api_key=self._get_api_key('claude'),
            model=self.config['claude']['model']
        )
        
        self.chatgpt_coder = ChatGPTCoder(
            api_key=self._get_api_key('chatgpt'),
            model=self.config['chatgpt']['model']
        )
        
        self.validator = CodeValidator(
            str(self.project_root),
            self.config['validation']['gradle_wrapper']
        )
        
        if self.config['discord']['enabled']:
            self.discord_notifier = DiscordNotifier(
                self._get_env_value(self.config['discord']['webhook_url'])
            )
        else:
            self.discord_notifier = None
        
        # Feedback directory
        self.feedback_dir = self.project_root / self.config['docs']['feedback_dir']
        self.feedback_dir.mkdir(parents=True, exist_ok=True)
    
    def _load_config(self, config_path: str) -> Dict[str, Any]:
        """Load configuration from YAML file."""
        with open(config_path, 'r', encoding='utf-8') as f:
            return yaml.safe_load(f)
    
    def _get_api_key(self, service: str) -> str:
        """Get API key for service."""
        api_key = self.config[service]['api_key']
        return self._get_env_value(api_key)
    
    def _get_env_value(self, value: str) -> str:
        """Replace environment variable placeholders."""
        if value.startswith('${') and value.endswith('}'):
            env_var = value[2:-1]
            return os.getenv(env_var, '')
        return value
    
    def process_task(self, task: Task, dry_run: bool = False) -> Dict[str, Any]:
        """
        Process a single task through the AI pipeline.
        
        Args:
            task: Task to process
            dry_run: If True, don't create PR or modify files
            
        Returns:
            Processing results
        """
        print(f"\n{'='*80}")
        print(f"Processing Task: {task.task_id} - {task.title}")
        print(f"Module: {task.module} | Priority: {task.priority}")
        print(f"{'='*80}\n")
        
        result = {
            'task_id': task.task_id,
            'success': False,
            'stage': '',
            'error': None
        }
        
        try:
            # Stage 1: Claude analyzes requirements
            result['stage'] = 'analysis'
            print("Stage 1: Analyzing requirements with Claude...")
            
            analysis_result = self.claude_analyzer.analyze_task(
                task.full_content,
                context={
                    'module': task.module,
                    'project_info': self._get_project_info()
                }
            )
            
            if not analysis_result['success']:
                result['error'] = f"Analysis failed: {analysis_result.get('error')}"
                return result
            
            analysis = analysis_result['analysis']
            print("✓ Analysis complete\n")
            
            # Stage 2: ChatGPT generates implementation
            result['stage'] = 'generation'
            print("Stage 2: Generating implementation with ChatGPT...")
            
            generation_result = self.chatgpt_coder.generate_implementation(
                analysis['specification'],
                task.module,
                context={
                    'package_base': f"net.orbis.{task.module.replace('-', '.')}",
                    'zakum_api': self._get_zakum_api_info()
                }
            )
            
            if not generation_result['success']:
                result['error'] = f"Generation failed: {generation_result.get('error')}"
                return result
            
            implementation = generation_result['implementation']
            print(f"✓ Generated {len(implementation['java_files'])} Java files\n")
            
            if dry_run:
                print("DRY RUN: Skipping file creation and validation")
                result['success'] = True
                result['analysis'] = analysis
                result['implementation'] = implementation
                return result
            
            # Stage 3: Create branch and apply changes
            result['stage'] = 'apply'
            print("Stage 3: Creating branch and applying changes...")
            
            branch_name = f"ai-gen/{task.task_id}"
            self._create_branch(branch_name)
            
            # Apply implementation
            file_paths = self._apply_implementation(implementation, task.module)
            print(f"✓ Created/modified {len(file_paths)} files\n")
            
            # Stage 4: Validate implementation
            result['stage'] = 'validation'
            print("Stage 4: Validating implementation...")
            
            validation_result = self.validator.validate_implementation(
                task.module,
                file_paths,
                timeout=self.config['validation']['compile_timeout']
            )
            
            print(f"✓ Validation complete: {'PASSED' if validation_result['overall_pass'] else 'FAILED'}\n")
            
            # Stage 5: Commit and create PR
            result['stage'] = 'pr_creation'
            print("Stage 5: Creating pull request...")
            
            commit_msg = f"AI-generated: {task.title} ({task.task_id})"
            self._commit_changes(commit_msg, file_paths)
            
            pr_info = self._create_pull_request(
                branch_name,
                task,
                analysis,
                implementation,
                validation_result
            )
            
            print(f"✓ PR created: {pr_info['url']}\n")
            
            # Stage 6: Send Discord notification
            if self.discord_notifier:
                result['stage'] = 'notification'
                print("Stage 6: Sending Discord notification...")
                
                self.discord_notifier.notify_pr_created(
                    task.task_id,
                    task.title,
                    task.module,
                    pr_info['url'],
                    branch_name,
                    details={
                        'priority': task.priority,
                        'files_changed': len(file_paths),
                        'compilation_status': 'success' if validation_result['overall_pass'] else 'failed'
                    }
                )
                
                self.discord_notifier.notify_pr_validation(
                    task.task_id,
                    pr_info['url'],
                    validation_result
                )
                
                print("✓ Notification sent\n")
            
            # Update task status
            self.task_parser.update_task_status(task.task_id, 'in-progress')
            
            result['success'] = True
            result['pr_url'] = pr_info['url']
            result['branch'] = branch_name
            result['validation'] = validation_result
            
            return result
        
        except Exception as e:
            result['error'] = str(e)
            
            if self.discord_notifier:
                self.discord_notifier.notify_error(
                    task.task_id,
                    f"Failed at stage: {result['stage']}",
                    details={'error_details': str(e)}
                )
            
            return result
    
    def process_pending_tasks(self, max_tasks: int = 1, dry_run: bool = False) -> Dict[str, Any]:
        """
        Process pending tasks in priority order.
        
        Args:
            max_tasks: Maximum number of tasks to process
            dry_run: If True, don't create PRs or modify files
            
        Returns:
            Summary of processing results
        """
        pending_tasks = self.task_parser.get_pending_tasks(
            self.config['processing']['priority_order']
        )
        
        if not pending_tasks:
            print("No pending tasks found")
            return {'processed': 0, 'succeeded': 0, 'failed': 0, 'tasks': []}
        
        print(f"Found {len(pending_tasks)} pending tasks")
        print(f"Processing up to {max_tasks} task(s)...\n")
        
        results = []
        succeeded = 0
        failed = 0
        
        for i, task in enumerate(pending_tasks[:max_tasks]):
            result = self.process_task(task, dry_run=dry_run)
            results.append(result)
            
            if result['success']:
                succeeded += 1
            else:
                failed += 1
                print(f"✗ Task failed: {result.get('error')}\n")
        
        # Send summary notification
        if self.discord_notifier and not dry_run:
            self.discord_notifier.notify_summary(
                processed=len(results),
                succeeded=succeeded,
                failed=failed,
                details=[
                    {
                        'success': r['success'],
                        'task_id': r['task_id'],
                        'title': next((t.title for t in pending_tasks if t.task_id == r['task_id']), 'Unknown')
                    }
                    for r in results
                ]
            )
        
        summary = {
            'processed': len(results),
            'succeeded': succeeded,
            'failed': failed,
            'tasks': results
        }
        
        print(f"\n{'='*80}")
        print(f"Processing Summary: {succeeded}/{len(results)} succeeded")
        print(f"{'='*80}\n")
        
        return summary
    
    def _get_project_info(self) -> str:
        """Get project information for context."""
        return f"""Project: Zakum Suite
Java Version: {self.config['project']['java_version']}
Paper Version: {self.config['project']['paper_version']}
Folia Compatible: {self.config['project']['folia_compatible']}
Spigot Compatible: {self.config['project']['spigot_compatible']}
"""
    
    def _get_zakum_api_info(self) -> str:
        """Get Zakum API usage information."""
        return """Use Zakum API for:
- Service resolution: zakum.getService(ServiceType.class)
- Scheduling: zakum.getScheduler() (handles Folia/Spigot)
- Configuration: zakum.getConfig()
- ACE Engine: zakum.getAceEngine()
- Database: zakum.getDatabase()
"""
    
    def _create_branch(self, branch_name: str):
        """Create a new Git branch."""
        subprocess.run(
            ['git', 'checkout', '-b', branch_name],
            cwd=self.project_root,
            check=True,
            capture_output=True
        )
    
    def _apply_implementation(self, implementation: Dict[str, Any], module: str) -> List[str]:
        """Apply generated implementation to project."""
        file_paths = []
        
        module_path = self.project_root / module
        
        # Apply Java files
        for java_file in implementation['java_files']:
            file_path = module_path / 'src' / 'main' / 'java' / java_file['path']
            file_path.parent.mkdir(parents=True, exist_ok=True)
            
            with open(file_path, 'w', encoding='utf-8') as f:
                f.write(java_file['content'])
            
            file_paths.append(str(file_path))
        
        # TODO: Apply build changes, config changes, etc.
        
        return file_paths
    
    def _commit_changes(self, message: str, file_paths: List[str]):
        """Commit changes to Git."""
        # Add files
        subprocess.run(
            ['git', 'add'] + file_paths,
            cwd=self.project_root,
            check=True
        )
        
        # Commit
        subprocess.run(
            ['git', 'commit', '-m', message],
            cwd=self.project_root,
            check=True
        )
    
    def _create_pull_request(
        self,
        branch_name: str,
        task: Task,
        analysis: Dict[str, Any],
        implementation: Dict[str, Any],
        validation: Dict[str, Any]
    ) -> Dict[str, Any]:
        """Create pull request using GitHub CLI."""
        
        pr_body = self._build_pr_body(task, analysis, implementation, validation)
        
        # Push branch
        subprocess.run(
            ['git', 'push', '-u', 'origin', branch_name],
            cwd=self.project_root,
            check=True
        )
        
        # Create PR using gh CLI
        result = subprocess.run(
            [
                'gh', 'pr', 'create',
                '--title', f"{task.title} ({task.task_id})",
                '--body', pr_body,
                '--base', self.config['github']['base_branch']
            ],
            cwd=self.project_root,
            capture_output=True,
            text=True,
            check=True
        )
        
        pr_url = result.stdout.strip()
        
        return {'url': pr_url, 'branch': branch_name}
    
    def _build_pr_body(
        self,
        task: Task,
        analysis: Dict[str, Any],
        implementation: Dict[str, Any],
        validation: Dict[str, Any]
    ) -> str:
        """Build PR description."""
        
        body = f"""## AI-Generated Implementation: {task.title}

**Task ID:** `{task.task_id}`
**Module:** `{task.module}`
**Priority:** {task.priority.upper()}

### Task Description
{task.description[:500]}...

### Implementation Summary
- Generated {len(implementation['java_files'])} Java files
- Compilation: {'✅ Success' if validation['compilation']['success'] else '❌ Failed'}
- Folia Compatible: {'✅ Yes' if validation['folia_compatibility']['compatible'] else '⚠️ Issues'}
- Plugin Descriptor: {'✅ Valid' if validation['plugin_descriptor']['valid'] else '❌ Invalid'}

### Generated Files
"""
        
        for java_file in implementation['java_files']:
            body += f"- `{java_file['path']}`\n"
        
        body += f"""

### Validation Results
Overall: {'✅ PASSED' if validation['overall_pass'] else '❌ FAILED'}

"""
        
        if validation['compilation'].get('errors'):
            body += "**Compilation Errors:**\n```\n"
            body += '\n'.join(validation['compilation']['errors'][:5])
            body += "\n```\n\n"
        
        if validation['folia_compatibility'].get('issues'):
            body += f"**Folia Compatibility Issues:** {len(validation['folia_compatibility']['issues'])}\n\n"
        
        body += """
---
*This PR was automatically generated by the AI Development Automation system using Claude for analysis and ChatGPT for implementation.*
"""
        
        return body
    
    def store_feedback(self, task_id: str, pr_data: Dict[str, Any]):
        """Store feedback from merged PR for future learning."""
        feedback_file = self.feedback_dir / f"{task_id}.json"
        
        feedback = {
            'task_id': task_id,
            'timestamp': datetime.now().isoformat(),
            'pr_data': pr_data
        }
        
        with open(feedback_file, 'w', encoding='utf-8') as f:
            json.dump(feedback, f, indent=2)


def main():
    """Main entry point."""
    import argparse
    
    parser = argparse.ArgumentParser(description='AI Development Orchestrator')
    parser.add_argument('--config', default='config.yaml', help='Configuration file')
    parser.add_argument('--project-root', default='.', help='Project root directory')
    parser.add_argument('--max-tasks', type=int, default=1, help='Maximum tasks to process')
    parser.add_argument('--dry-run', action='store_true', help='Dry run mode (no PRs)')
    parser.add_argument('--task-id', help='Process specific task by ID')
    
    args = parser.parse_args()
    
    try:
        # Change to project root
        os.chdir(args.project_root)
        
        orchestrator = AIOrchestrator(
            config_path=args.config,
            project_root=args.project_root
        )
        
        if args.task_id:
            # Process specific task
            task = orchestrator.task_parser.get_task_by_id(args.task_id)
            if not task:
                print(f"Task not found: {args.task_id}")
                sys.exit(1)
            
            result = orchestrator.process_task(task, dry_run=args.dry_run)
            
            if result['success']:
                print("✓ Task processed successfully")
                sys.exit(0)
            else:
                print(f"✗ Task failed: {result.get('error')}")
                sys.exit(1)
        else:
            # Process pending tasks
            summary = orchestrator.process_pending_tasks(
                max_tasks=args.max_tasks,
                dry_run=args.dry_run
            )
            
            if summary['failed'] > 0:
                sys.exit(1)
    
    except Exception as e:
        print(f"Fatal error: {e}", file=sys.stderr)
        import traceback
        traceback.print_exc()
        sys.exit(1)


if __name__ == '__main__':
    main()
