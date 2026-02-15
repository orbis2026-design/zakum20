#!/usr/bin/env python3
"""
ChatGPT Coder for AI Orchestrator

Uses ChatGPT API to generate Java implementation based on specifications.
"""

import os
import json
from typing import Dict, Any, Optional, List
from openai import OpenAI


class ChatGPTCoder:
    """ChatGPT-based code generator."""
    
    def __init__(self, api_key: Optional[str] = None, model: str = "gpt-4-turbo-preview"):
        """
        Initialize ChatGPT coder.
        
        Args:
            api_key: OpenAI API key (or use OPENAI_API_KEY env var)
            model: ChatGPT model to use
        """
        self.api_key = api_key or os.getenv('OPENAI_API_KEY')
        if not self.api_key:
            raise ValueError("OpenAI API key required (set OPENAI_API_KEY or pass api_key)")
        
        self.model = model
        self.client = OpenAI(api_key=self.api_key)
    
    def generate_implementation(
        self,
        specification: str,
        module: str,
        context: Optional[Dict[str, Any]] = None
    ) -> Dict[str, Any]:
        """
        Generate Java implementation from specification.
        
        Args:
            specification: Detailed technical specification (from Claude)
            module: Target module name
            context: Additional context (existing code structure, dependencies, etc.)
            
        Returns:
            Dictionary containing:
                - files: List of generated files with paths and content
                - build_changes: Suggested build.gradle.kts changes if needed
                - config_changes: Configuration file changes if needed
        """
        system_prompt = self._build_system_prompt()
        user_prompt = self._build_generation_prompt(specification, module, context)
        
        try:
            response = self.client.chat.completions.create(
                model=self.model,
                messages=[
                    {"role": "system", "content": system_prompt},
                    {"role": "user", "content": user_prompt}
                ],
                temperature=0.7,
                max_tokens=4096
            )
            
            response_text = response.choices[0].message.content
            
            # Parse structured response
            implementation = self._parse_implementation_response(response_text)
            
            return {
                'success': True,
                'implementation': implementation,
                'raw_response': response_text
            }
        
        except Exception as e:
            return {
                'success': False,
                'error': str(e)
            }
    
    def _build_system_prompt(self) -> str:
        """Build system prompt for ChatGPT."""
        return """You are an expert Java developer specializing in Minecraft plugin development.

Your role is to generate clean, production-ready Java code based on specifications.

Key requirements:
- Java 21 with modern language features
- PaperSpigot 1.21.1 API compatibility
- Folia-compatible threading (regionized schedulers)
- Spigot fallback when Folia features not available
- Clean architecture with proper separation of concerns
- Comprehensive error handling
- Thread-safe implementations
- Performance-optimized code

Code style:
- Use final classes where appropriate
- Immutable data structures when possible
- Descriptive variable and method names
- JavaDoc for public APIs
- Null-safe operations (Optional, @Nullable)
- Consistent formatting

Provide complete, compilable code with all necessary imports and structure."""
    
    def _build_generation_prompt(
        self,
        specification: str,
        module: str,
        context: Optional[Dict[str, Any]]
    ) -> str:
        """Build code generation prompt."""
        prompt = f"""Generate Java implementation for the following specification.

TARGET MODULE: {module}

SPECIFICATION:
{specification}
"""
        
        if context:
            prompt += "\n\nCONTEXT:\n"
            
            if 'package_base' in context:
                prompt += f"- Base Package: {context['package_base']}\n"
            
            if 'existing_classes' in context:
                prompt += f"- Existing Classes:\n"
                for cls in context['existing_classes']:
                    prompt += f"  - {cls}\n"
            
            if 'dependencies' in context:
                prompt += f"- Available Dependencies:\n"
                for dep in context['dependencies']:
                    prompt += f"  - {dep}\n"
            
            if 'zakum_api' in context:
                prompt += f"\n- Zakum API Usage:\n{context['zakum_api']}\n"
        
        prompt += """

Please provide:

1. JAVA FILES:
   For each Java file needed:
   ```java
   // File: path/to/FileName.java
   package net.orbis...;
   
   [complete implementation]
   ```

2. BUILD CHANGES (if needed):
   ```gradle
   // Add to build.gradle.kts dependencies
   [any new dependencies]
   ```

3. CONFIG CHANGES (if needed):
   ```yaml
   # config.yml additions/changes
   [configuration]
   ```

4. PLUGIN DESCRIPTOR CHANGES (if needed):
   ```yaml
   # plugin.yml additions
   [commands, permissions, etc.]
   ```

Ensure:
- All code is complete and compilable
- Proper error handling throughout
- Folia/Spigot compatibility handled correctly
- Thread safety for concurrent access
- Performance considerations addressed
"""
        
        return prompt
    
    def _parse_implementation_response(self, response_text: str) -> Dict[str, Any]:
        """
        Parse structured implementation from ChatGPT's response.
        
        Args:
            response_text: Raw response from ChatGPT
            
        Returns:
            Structured implementation dictionary
        """
        implementation = {
            'java_files': [],
            'build_changes': '',
            'config_changes': '',
            'plugin_descriptor_changes': ''
        }
        
        # Extract code blocks
        import re
        
        # Find all code blocks with file comments
        java_pattern = r'```java\s*\n(?:// File: (.+?)\n)?(.*?)```'
        java_matches = re.finditer(java_pattern, response_text, re.DOTALL)
        
        for match in java_matches:
            file_path = match.group(1)
            code_content = match.group(2).strip()
            
            if file_path:
                implementation['java_files'].append({
                    'path': file_path.strip(),
                    'content': code_content
                })
            else:
                # No file path, might be inline code
                implementation['java_files'].append({
                    'path': 'Unnamed.java',
                    'content': code_content
                })
        
        # Extract Gradle changes
        gradle_pattern = r'```gradle\s*\n(.*?)```'
        gradle_match = re.search(gradle_pattern, response_text, re.DOTALL)
        if gradle_match:
            implementation['build_changes'] = gradle_match.group(1).strip()
        
        # Extract YAML/config changes
        yaml_pattern = r'```yaml\s*\n(?:# (.+?)\n)?(.*?)```'
        yaml_matches = re.finditer(yaml_pattern, response_text, re.DOTALL)
        
        for match in yaml_matches:
            comment = match.group(1)
            yaml_content = match.group(2).strip()
            
            if comment and 'plugin.yml' in comment.lower():
                implementation['plugin_descriptor_changes'] = yaml_content
            elif comment and 'config' in comment.lower():
                implementation['config_changes'] = yaml_content
            else:
                # Default to config
                implementation['config_changes'] += '\n' + yaml_content
        
        return implementation
    
    def apply_implementation(
        self,
        implementation: Dict[str, Any],
        base_path: str,
        module: str,
        dry_run: bool = False
    ) -> Dict[str, Any]:
        """
        Apply generated implementation to the project.
        
        Args:
            implementation: Parsed implementation dictionary
            base_path: Project base path
            module: Target module
            dry_run: If True, don't write files (just validate)
            
        Returns:
            Results with file paths and status
        """
        results = {
            'created_files': [],
            'modified_files': [],
            'errors': []
        }
        
        import os
        from pathlib import Path
        
        module_path = Path(base_path) / module
        
        if not module_path.exists():
            results['errors'].append(f"Module path does not exist: {module_path}")
            return results
        
        # Process Java files
        for java_file in implementation['java_files']:
            try:
                file_path = module_path / 'src' / 'main' / 'java' / java_file['path']
                
                if dry_run:
                    results['created_files'].append(str(file_path))
                else:
                    # Create directories if needed
                    file_path.parent.mkdir(parents=True, exist_ok=True)
                    
                    # Write file
                    with open(file_path, 'w', encoding='utf-8') as f:
                        f.write(java_file['content'])
                    
                    results['created_files'].append(str(file_path))
            
            except Exception as e:
                results['errors'].append(f"Error writing {java_file['path']}: {e}")
        
        return results


def main():
    """Example usage and testing."""
    import sys
    
    if len(sys.argv) < 3:
        print("Usage: python chatgpt_coder.py <module> <specification>")
        print("\nExample:")
        print("  python chatgpt_coder.py zakum-core 'Create a player cache class...'")
        sys.exit(1)
    
    module = sys.argv[1]
    specification = ' '.join(sys.argv[2:])
    
    try:
        coder = ChatGPTCoder()
        
        print(f"Generating implementation for {module}...")
        result = coder.generate_implementation(specification, module)
        
        if result['success']:
            print("\n=== IMPLEMENTATION GENERATED ===\n")
            impl = result['implementation']
            
            print(f"Java Files: {len(impl['java_files'])}")
            for java_file in impl['java_files']:
                print(f"  - {java_file['path']}")
            
            if impl['build_changes']:
                print("\nBuild Changes Required: Yes")
            
            if impl['config_changes']:
                print("Config Changes Required: Yes")
            
            print("\nRaw Response:")
            print(result['raw_response'])
        else:
            print(f"Error: {result['error']}", file=sys.stderr)
            sys.exit(1)
    
    except Exception as e:
        print(f"Error: {e}", file=sys.stderr)
        sys.exit(1)


if __name__ == '__main__':
    main()
