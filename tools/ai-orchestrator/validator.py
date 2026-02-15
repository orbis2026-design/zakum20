"""
Validator - Runs build validation and compatibility checks.
Validates Java 21 compatibility and Folia safety patterns.
"""

import subprocess
import json
from pathlib import Path
from typing import Dict, List
from datetime import datetime


class BuildValidator:
    """Validates code by running Gradle build."""
    
    def __init__(self, repo_root: Path):
        self.repo_root = Path(repo_root)
        self.gradlew = self.repo_root / "gradlew"
        
        # Make gradlew executable if not already
        if self.gradlew.exists():
            import os
            import stat
            st = os.stat(self.gradlew)
            os.chmod(self.gradlew, st.st_mode | stat.S_IEXEC)
    
    def validate_build(self, module: str = None) -> Dict:
        """
        Run Gradle build validation.
        
        Args:
            module: Specific module to build (e.g., "zakum-core"), or None for all
        
        Returns:
            Dict with validation results
        """
        result = {
            "success": False,
            "build_output": "",
            "errors": [],
            "warnings": [],
            "timestamp": datetime.now(timezone.utc).isoformat()
        }
        
        try:
            # Build command
            if module:
                cmd = [str(self.gradlew), f":{module}:build", "--no-daemon", "--console=plain"]
            else:
                cmd = [str(self.gradlew), "build", "--no-daemon", "--console=plain"]
            
            # Run build
            process = subprocess.Popen(
                cmd,
                cwd=str(self.repo_root),
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                text=True
            )
            
            stdout, stderr = process.communicate(timeout=600)  # 10 minute timeout
            
            result["build_output"] = stdout
            result["success"] = process.returncode == 0
            
            if stderr:
                result["errors"].append(stderr)
            
            # Parse build output for errors and warnings
            self._parse_build_output(stdout, result)
            
            return result
            
        except subprocess.TimeoutExpired:
            result["errors"].append("Build timed out after 10 minutes")
            return result
        except Exception as e:
            result["errors"].append(f"Build error: {str(e)}")
            return result
    
    def _parse_build_output(self, output: str, result: Dict):
        """Parse build output to extract errors and warnings."""
        for line in output.split('\n'):
            line_lower = line.lower()
            
            # Detect compilation errors
            if 'error:' in line_lower or 'compilation error' in line_lower:
                result["errors"].append(line.strip())
            
            # Detect warnings
            elif 'warning:' in line_lower:
                result["warnings"].append(line.strip())
            
            # Detect test failures
            elif 'test failed' in line_lower or 'failure' in line_lower:
                if 'test' in line_lower:
                    result["errors"].append(line.strip())
    
    def check_java_21_compatibility(self, source_files: List[Path]) -> Dict:
        """
        Check if source files use Java 21 compatible syntax.
        
        This is a basic check - actual compilation is the real test.
        """
        result = {
            "compatible": True,
            "issues": []
        }
        
        for source_file in source_files:
            if not source_file.exists():
                continue
            
            try:
                with open(source_file, 'r', encoding='utf-8') as f:
                    content = f.read()
                
                # Check for incompatible patterns
                if 'import sun.' in content:
                    result["issues"].append(f"{source_file}: Uses internal sun.* packages")
                    result["compatible"] = False
                
                # Check for good patterns (records, pattern matching, etc.)
                if 'record ' in content:
                    result["issues"].append(f"{source_file}: ✅ Uses Java records")
                
                if 'instanceof' in content and 'instanceof ' in content:
                    result["issues"].append(f"{source_file}: ✅ May use pattern matching")
                
            except Exception as e:
                result["issues"].append(f"{source_file}: Error reading file: {e}")
        
        return result
    
    def check_folia_safety(self, source_files: List[Path]) -> Dict:
        """
        Check for Folia thread safety patterns.
        
        Looks for:
        - Use of schedulers (should be region-aware)
        - Synchronization patterns
        - Async annotations
        """
        result = {
            "safe": True,
            "issues": [],
            "suggestions": []
        }
        
        for source_file in source_files:
            if not source_file.exists():
                continue
            
            try:
                with open(source_file, 'r', encoding='utf-8') as f:
                    content = f.read()
                
                # Check for unsafe patterns
                if 'Bukkit.getScheduler()' in content:
                    result["issues"].append(
                        f"{source_file}: Uses Bukkit.getScheduler() - should use region scheduler for Folia"
                    )
                    result["safe"] = False
                
                if 'synchronized' not in content and 'ConcurrentHashMap' not in content:
                    if 'HashMap' in content or 'ArrayList' in content:
                        result["suggestions"].append(
                            f"{source_file}: Uses non-thread-safe collections - consider thread safety"
                        )
                
                # Check for good patterns
                if '@ThreadSafe' in content or '@RegionScheduled' in content:
                    result["issues"].append(f"{source_file}: ✅ Has thread safety annotations")
                
                if 'RegionScheduler' in content:
                    result["issues"].append(f"{source_file}: ✅ Uses region scheduler (Folia compatible)")
                
            except Exception as e:
                result["issues"].append(f"{source_file}: Error reading file: {e}")
        
        return result
    
    def generate_report(self, validation_results: Dict) -> str:
        """Generate a human-readable validation report."""
        report = []
        report.append("=" * 80)
        report.append("VALIDATION REPORT")
        report.append("=" * 80)
        report.append(f"Timestamp: {validation_results['timestamp']}")
        report.append(f"Build Success: {'✅ YES' if validation_results['success'] else '❌ NO'}")
        report.append("")
        
        if validation_results['errors']:
            report.append("ERRORS:")
            for error in validation_results['errors']:
                report.append(f"  ❌ {error}")
            report.append("")
        
        if validation_results['warnings']:
            report.append("WARNINGS:")
            for warning in validation_results['warnings'][:10]:  # Limit to 10
                report.append(f"  ⚠️  {warning}")
            if len(validation_results['warnings']) > 10:
                report.append(f"  ... and {len(validation_results['warnings']) - 10} more warnings")
            report.append("")
        
        report.append("=" * 80)
        
        return "\n".join(report)


if __name__ == "__main__":
    import sys
    from pathlib import Path
    
    # Test the validator
    repo_root = Path(__file__).parent.parent.parent
    validator = BuildValidator(repo_root)
    
    print("Running build validation...")
    result = validator.validate_build()
    
    print(validator.generate_report(result))
    
    if result["success"]:
        print("\n✅ Build validation passed!")
        sys.exit(0)
    else:
        print("\n❌ Build validation failed!")
        sys.exit(1)
