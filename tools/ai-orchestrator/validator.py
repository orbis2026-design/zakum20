#!/usr/bin/env python3
"""
Validator for AI Orchestrator

Validates generated code for compilation, formatting, and Folia compatibility.
"""

import os
import subprocess
import re
from typing import Dict, Any, List, Optional
from pathlib import Path


class CodeValidator:
    """Validator for generated plugin code."""
    
    def __init__(self, project_root: str, gradle_wrapper: str = "./gradlew"):
        """
        Initialize validator.
        
        Args:
            project_root: Project root directory
            gradle_wrapper: Path to Gradle wrapper script
        """
        self.project_root = Path(project_root)
        self.gradle_wrapper = gradle_wrapper
        
        if not self.project_root.exists():
            raise ValueError(f"Project root does not exist: {project_root}")
    
    def validate_implementation(
        self,
        module: str,
        files: List[str],
        timeout: int = 300
    ) -> Dict[str, Any]:
        """
        Validate implementation for a module.
        
        Args:
            module: Module name to validate
            files: List of file paths that were changed
            timeout: Compilation timeout in seconds
            
        Returns:
            Validation results with compilation status, errors, and warnings
        """
        results = {
            'compilation': self._validate_compilation(module, timeout),
            'folia_compatibility': self._check_folia_compatibility(files),
            'code_quality': self._check_code_quality(files),
            'plugin_descriptor': self._validate_plugin_descriptor(module),
            'overall_pass': False
        }
        
        # Overall pass if compilation succeeds and no critical issues
        results['overall_pass'] = (
            results['compilation']['success'] and
            not results['folia_compatibility']['critical_issues'] and
            results['plugin_descriptor']['valid']
        )
        
        return results
    
    def _validate_compilation(self, module: str, timeout: int) -> Dict[str, Any]:
        """
        Validate that module compiles successfully.
        
        Args:
            module: Module name
            timeout: Timeout in seconds
            
        Returns:
            Compilation result with success status and errors
        """
        result = {
            'success': False,
            'errors': [],
            'warnings': [],
            'output': ''
        }
        
        try:
            # Run Gradle compilation
            cmd = [self.gradle_wrapper, f":{module}:compileJava", "--console=plain"]
            
            process = subprocess.run(
                cmd,
                cwd=self.project_root,
                capture_output=True,
                text=True,
                timeout=timeout
            )
            
            result['output'] = process.stdout + process.stderr
            result['success'] = process.returncode == 0
            
            # Parse errors and warnings
            if not result['success']:
                result['errors'] = self._parse_compilation_errors(result['output'])
            
            result['warnings'] = self._parse_compilation_warnings(result['output'])
        
        except subprocess.TimeoutExpired:
            result['errors'].append(f"Compilation timeout after {timeout} seconds")
        except Exception as e:
            result['errors'].append(f"Compilation error: {str(e)}")
        
        return result
    
    def _parse_compilation_errors(self, output: str) -> List[str]:
        """Extract compilation errors from Gradle output."""
        errors = []
        
        # Look for error patterns
        error_pattern = r'error: (.+)'
        for match in re.finditer(error_pattern, output):
            errors.append(match.group(1).strip())
        
        # Also look for compilation failed messages
        if 'Compilation failed' in output or 'FAILED' in output:
            lines = output.split('\n')
            for i, line in enumerate(lines):
                if 'error:' in line.lower() or 'exception' in line.lower():
                    errors.append(line.strip())
        
        return errors[:10]  # Limit to first 10 errors
    
    def _parse_compilation_warnings(self, output: str) -> List[str]:
        """Extract compilation warnings from Gradle output."""
        warnings = []
        
        warning_pattern = r'warning: (.+)'
        for match in re.finditer(warning_pattern, output):
            warnings.append(match.group(1).strip())
        
        return warnings[:5]  # Limit to first 5 warnings
    
    def _check_folia_compatibility(self, files: List[str]) -> Dict[str, Any]:
        """
        Check Folia compatibility of generated code.
        
        Args:
            files: List of file paths to check
            
        Returns:
            Compatibility check results
        """
        result = {
            'compatible': True,
            'issues': [],
            'critical_issues': []
        }
        
        # Patterns that indicate Folia incompatibility
        incompatible_patterns = [
            (r'Bukkit\.getScheduler\(\)', 'Use Folia regionized scheduler instead of Bukkit scheduler'),
            (r'\.runTask(?:Later|Timer)?\(', 'Use entity/global/async scheduler methods'),
            (r'synchronized\s*\(', 'Synchronized blocks may cause issues with regionized threading'),
            (r'static\s+(?:final\s+)?(?:Map|List|Set)<.*?>\s+\w+\s*=', 'Static collections are not thread-safe in Folia'),
        ]
        
        for file_path in files:
            try:
                with open(file_path, 'r', encoding='utf-8') as f:
                    content = f.read()
                
                for pattern, message in incompatible_patterns:
                    matches = re.finditer(pattern, content)
                    for match in matches:
                        line_num = content[:match.start()].count('\n') + 1
                        issue = {
                            'file': file_path,
                            'line': line_num,
                            'message': message,
                            'code': match.group(0)
                        }
                        result['issues'].append(issue)
                        
                        # Mark as critical for scheduler issues
                        if 'scheduler' in message.lower():
                            result['critical_issues'].append(issue)
                            result['compatible'] = False
            
            except Exception as e:
                result['issues'].append({
                    'file': file_path,
                    'error': f"Failed to check: {str(e)}"
                })
        
        return result
    
    def _check_code_quality(self, files: List[str]) -> Dict[str, Any]:
        """
        Check code quality metrics.
        
        Args:
            files: List of file paths to check
            
        Returns:
            Code quality results
        """
        result = {
            'issues': [],
            'suggestions': []
        }
        
        quality_checks = [
            (r'System\.out\.print', 'Use proper logging instead of System.out'),
            (r'\.printStackTrace\(\)', 'Use proper logging instead of printStackTrace'),
            (r'catch\s*\(\s*Exception\s+\w+\s*\)\s*\{\s*\}', 'Empty catch blocks hide errors'),
            (r'@SuppressWarnings', 'Avoid suppressing warnings without good reason'),
        ]
        
        for file_path in files:
            try:
                with open(file_path, 'r', encoding='utf-8') as f:
                    content = f.read()
                
                for pattern, message in quality_checks:
                    matches = re.finditer(pattern, content)
                    for match in matches:
                        line_num = content[:match.start()].count('\n') + 1
                        result['suggestions'].append({
                            'file': file_path,
                            'line': line_num,
                            'message': message
                        })
            
            except Exception as e:
                result['issues'].append({
                    'file': file_path,
                    'error': f"Failed to check: {str(e)}"
                })
        
        return result
    
    def _validate_plugin_descriptor(self, module: str) -> Dict[str, Any]:
        """
        Validate plugin.yml descriptor.
        
        Args:
            module: Module name
            
        Returns:
            Validation results for plugin descriptor
        """
        result = {
            'valid': False,
            'exists': False,
            'errors': []
        }
        
        descriptor_path = self.project_root / module / 'src' / 'main' / 'resources' / 'plugin.yml'
        
        if not descriptor_path.exists():
            result['errors'].append("plugin.yml not found")
            return result
        
        result['exists'] = True
        
        try:
            import yaml
            
            with open(descriptor_path, 'r', encoding='utf-8') as f:
                descriptor = yaml.safe_load(f)
            
            # Check required fields
            required_fields = ['name', 'version', 'main', 'api-version']
            for field in required_fields:
                if field not in descriptor:
                    result['errors'].append(f"Missing required field: {field}")
            
            # Check api-version for Paper compatibility
            if 'api-version' in descriptor:
                api_version = str(descriptor['api-version'])
                if not api_version.startswith('1.21'):
                    result['errors'].append(f"api-version should be 1.21 or higher, got: {api_version}")
            
            result['valid'] = len(result['errors']) == 0
        
        except Exception as e:
            result['errors'].append(f"Failed to parse plugin.yml: {str(e)}")
        
        return result
    
    def run_verification_tasks(self, timeout: int = 300) -> Dict[str, Any]:
        """
        Run project-wide verification tasks.
        
        Args:
            timeout: Timeout in seconds
            
        Returns:
            Verification results
        """
        result = {
            'success': False,
            'output': '',
            'errors': []
        }
        
        try:
            cmd = [self.gradle_wrapper, "verifyPlatformInfrastructure", "--console=plain"]
            
            process = subprocess.run(
                cmd,
                cwd=self.project_root,
                capture_output=True,
                text=True,
                timeout=timeout
            )
            
            result['output'] = process.stdout + process.stderr
            result['success'] = process.returncode == 0
            
            if not result['success']:
                result['errors'].append("Platform verification failed")
        
        except subprocess.TimeoutExpired:
            result['errors'].append(f"Verification timeout after {timeout} seconds")
        except Exception as e:
            result['errors'].append(f"Verification error: {str(e)}")
        
        return result


def main():
    """Example usage and testing."""
    import sys
    import json
    
    if len(sys.argv) < 3:
        print("Usage: python validator.py <project-root> <module>")
        print("\nExample:")
        print("  python validator.py /path/to/zakum20 zakum-core")
        sys.exit(1)
    
    project_root = sys.argv[1]
    module = sys.argv[2]
    
    try:
        validator = CodeValidator(project_root)
        
        print(f"Validating module: {module}")
        
        # Get list of Java files in module
        module_path = Path(project_root) / module / 'src' / 'main' / 'java'
        files = [str(f) for f in module_path.rglob('*.java')] if module_path.exists() else []
        
        results = validator.validate_implementation(module, files)
        
        print("\n=== VALIDATION RESULTS ===\n")
        print(json.dumps(results, indent=2))
        
        if results['overall_pass']:
            print("\n✓ VALIDATION PASSED")
            sys.exit(0)
        else:
            print("\n✗ VALIDATION FAILED")
            sys.exit(1)
    
    except Exception as e:
        print(f"Error: {e}", file=sys.stderr)
        sys.exit(1)


if __name__ == '__main__':
    main()
