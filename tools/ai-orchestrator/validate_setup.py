#!/usr/bin/env python3
"""
Setup validation script for AI Orchestrator

Checks that all components are properly configured.
"""

import os
import sys
from pathlib import Path


def check_python_version():
    """Check Python version."""
    version = sys.version_info
    if version.major < 3 or (version.major == 3 and version.minor < 9):
        return False, f"Python 3.9+ required, found {version.major}.{version.minor}"
    return True, f"Python {version.major}.{version.minor}.{version.micro}"


def check_dependencies():
    """Check required Python packages."""
    required = [
        'anthropic',
        'openai',
        'yaml',
        'discord_webhook'
    ]
    
    missing = []
    for package in required:
        try:
            __import__(package)
        except ImportError:
            missing.append(package)
    
    if missing:
        return False, f"Missing packages: {', '.join(missing)}"
    return True, f"All {len(required)} packages installed"


def check_project_structure():
    """Check project directory structure."""
    project_root = Path(__file__).parent.parent.parent
    
    required_paths = [
        'docs/PLUGIN-TASKS.md',
        'tools/ai-orchestrator/config.yaml',
        'tools/ai-orchestrator/orchestrator.py',
        'tools/ai-orchestrator/parse_docs.py',
        'tools/ai-orchestrator/claude_analyzer.py',
        'tools/ai-orchestrator/chatgpt_coder.py',
        'tools/ai-orchestrator/validator.py',
        'tools/ai-orchestrator/discord_notifier.py',
        '.github/workflows/ai-development.yml',
        '.github/workflows/ai-feedback.yml',
    ]
    
    missing = []
    for path in required_paths:
        if not (project_root / path).exists():
            missing.append(path)
    
    if missing:
        return False, f"Missing files:\n  " + '\n  '.join(missing)
    return True, f"All {len(required_paths)} required files present"


def check_task_parser():
    """Check task parser functionality."""
    try:
        sys.path.insert(0, str(Path(__file__).parent))
        from parse_docs import TaskParser
        
        project_root = Path(__file__).parent.parent.parent
        tasks_file = project_root / 'docs' / 'PLUGIN-TASKS.md'
        
        parser = TaskParser(str(tasks_file))
        tasks = parser.get_pending_tasks(['high', 'medium', 'low'])
        
        if not tasks:
            return False, "No pending tasks found (check PLUGIN-TASKS.md)"
        
        return True, f"Found {len(tasks)} pending tasks"
    
    except Exception as e:
        return False, f"Parser error: {str(e)}"


def check_environment():
    """Check environment variables."""
    env_vars = {
        'ANTHROPIC_API_KEY': False,
        'OPENAI_API_KEY': False,
        'DISCORD_WEBHOOK_URL': False,
        'GITHUB_TOKEN': False
    }
    
    set_vars = []
    for var in env_vars:
        if os.getenv(var):
            env_vars[var] = True
            set_vars.append(var)
    
    status = f"Set: {len(set_vars)}/4"
    if len(set_vars) < 2:
        return False, f"{status} (API keys required for operation)"
    return True, status


def check_gradle():
    """Check Gradle wrapper."""
    project_root = Path(__file__).parent.parent.parent
    gradlew = project_root / 'gradlew'
    
    if not gradlew.exists():
        return False, "gradlew not found"
    
    if not os.access(gradlew, os.X_OK):
        return False, "gradlew not executable"
    
    return True, "Gradle wrapper present and executable"


def check_git():
    """Check Git configuration."""
    import subprocess
    
    try:
        result = subprocess.run(
            ['git', '--version'],
            capture_output=True,
            text=True,
            timeout=5
        )
        if result.returncode == 0:
            version = result.stdout.strip()
            return True, version
        return False, "Git not working"
    except Exception as e:
        return False, f"Git check failed: {str(e)}"


def main():
    """Run all validation checks."""
    print("=" * 70)
    print("AI Orchestrator Setup Validation")
    print("=" * 70)
    print()
    
    checks = [
        ("Python Version", check_python_version),
        ("Python Dependencies", check_dependencies),
        ("Project Structure", check_project_structure),
        ("Task Parser", check_task_parser),
        ("Environment Variables", check_environment),
        ("Gradle Wrapper", check_gradle),
        ("Git Installation", check_git),
    ]
    
    passed = 0
    failed = 0
    warnings = 0
    
    for name, check_func in checks:
        try:
            success, message = check_func()
            
            if success:
                status = "✓"
                status_text = "PASS"
                passed += 1
            elif name == "Environment Variables":
                # Environment variables can be a warning
                status = "⚠"
                status_text = "WARN"
                warnings += 1
            else:
                status = "✗"
                status_text = "FAIL"
                failed += 1
            
            print(f"{status} {name:.<30} {status_text}")
            if message:
                print(f"  → {message}")
        
        except Exception as e:
            print(f"✗ {name:.<30} ERROR")
            print(f"  → {str(e)}")
            failed += 1
    
    print()
    print("=" * 70)
    print(f"Results: {passed} passed, {failed} failed, {warnings} warnings")
    print("=" * 70)
    print()
    
    if failed > 0:
        print("❌ Setup validation FAILED")
        print()
        print("Action required:")
        print("  1. Install missing dependencies: pip install -r requirements.txt")
        print("  2. Verify all required files are present")
        print("  3. Fix any errors listed above")
        return 1
    
    if warnings > 0:
        print("⚠️  Setup validation PASSED with warnings")
        print()
        print("For full functionality:")
        print("  1. Set environment variables (API keys)")
        print("  2. Configure GitHub repository secrets")
        print("  3. See QUICKSTART.md for details")
        return 0
    
    print("✅ Setup validation PASSED")
    print()
    print("System is ready to use!")
    print("Next steps:")
    print("  1. Configure GitHub repository secrets (see QUICKSTART.md)")
    print("  2. Add tasks to docs/PLUGIN-TASKS.md")
    print("  3. Run: python orchestrator.py --dry-run")
    return 0


if __name__ == '__main__':
    sys.exit(main())
