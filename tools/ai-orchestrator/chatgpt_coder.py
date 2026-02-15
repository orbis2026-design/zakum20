"""
ChatGPT Coder - Uses GPT-4.1 API to generate production-ready Java code.
Input: Claude specification
Output: production-ready Java code with error handling, JavaDoc, and tests
"""

import os
import json
from pathlib import Path
from typing import Dict, Optional
from datetime import datetime
from openai import OpenAI


class ChatGPTCoder:
    """Generates production-ready code using OpenAI GPT-4 API."""
    
    def __init__(self, api_key: str, output_dir: Path):
        self.api_key = api_key
        self.output_dir = Path(output_dir)
        self.output_dir.mkdir(exist_ok=True, parents=True)
        self.client = OpenAI(api_key=api_key)
        self.model = "gpt-4-turbo-preview"  # GPT-4.1 equivalent
    
    def generate_implementation(self, task: Dict, specification: str, patterns: Optional[Dict] = None) -> Dict:
        """
        Generate Java implementation based on specification.
        
        Returns:
            Dict with keys: code (str), input_tokens (int), output_tokens (int)
        """
        prompt = self._build_prompt(task, specification, patterns)
        
        try:
            response = self.client.chat.completions.create(
                model=self.model,
                messages=[
                    {
                        "role": "system",
                        "content": "You are an expert Java developer specializing in Minecraft plugin development with PaperSpigot 1.21.1, Java 21, and Folia compatibility. You write clean, well-documented, production-ready code with proper error handling and thread safety."
                    },
                    {
                        "role": "user",
                        "content": prompt
                    }
                ],
                temperature=0.2,  # Low temperature for consistent code generation
                max_tokens=8000
            )
            
            code = response.choices[0].message.content
            
            # Extract token usage
            input_tokens = response.usage.prompt_tokens
            output_tokens = response.usage.completion_tokens
            
            # Save implementation to file
            impl_file = self._save_implementation(task["task_id"], code)
            
            return {
                "code": code,
                "input_tokens": input_tokens,
                "output_tokens": output_tokens,
                "impl_file": str(impl_file),
                "model": self.model,
                "timestamp": datetime.now(timezone.utc).isoformat()
            }
            
        except Exception as e:
            return {
                "error": str(e),
                "code": None,
                "input_tokens": 0,
                "output_tokens": 0
            }
    
    def _build_prompt(self, task: Dict, specification: str, patterns: Optional[Dict] = None) -> str:
        """Build the prompt for GPT-4."""
        prompt = f"""Generate production-ready Java code for the following task:

Task ID: {task['task_id']}
Task Name: {task['name']}
Module: {task['module']}

Technical Specification:
{specification}

Requirements:
1. **Java 21 Features**: Use modern Java 21 features where appropriate (records, pattern matching, etc.)
2. **PaperSpigot 1.21.1**: Use Paper API correctly, follow Paper best practices
3. **Folia Compatibility**: 
   - Use region-based scheduling, not global schedulers
   - Ensure thread safety with proper synchronization
   - Use @ThreadSafe annotations where appropriate
   - Avoid blocking operations on game threads
4. **Error Handling**:
   - Comprehensive try-catch blocks
   - Proper exception types
   - Detailed error logging
5. **Documentation**:
   - Complete JavaDoc for all public methods/classes
   - Include @param, @return, @throws tags
   - Add usage examples in class JavaDoc
6. **Code Quality**:
   - Clean, readable code
   - Proper naming conventions
   - No hardcoded values (use constants)
   - Follow Java best practices
7. **Testing**:
   - Include JUnit 5 test class
   - Test happy paths and edge cases
   - Mock Paper APIs appropriately
"""
        
        if patterns:
            prompt += f"\n\nSuccessful Patterns from Previous PRs:\n{json.dumps(patterns, indent=2)}\n"
        
        prompt += """
Generate the complete implementation including:
1. Main implementation class(es)
2. Any necessary interfaces or helper classes
3. JUnit 5 test class(es)
4. Configuration classes if needed

Format your response as a series of file blocks like this:

```java
// File: src/main/java/net/orbis/zakum/[module]/[ClassName].java
package net.orbis.zakum.[module];

// ... code here ...
```

```java
// File: src/test/java/net/orbis/zakum/[module]/[ClassName]Test.java
package net.orbis.zakum.[module];

// ... test code here ...
```

Be specific, complete, and production-ready. The code should compile and run without modifications.
"""
        
        return prompt
    
    def _save_implementation(self, task_id: str, code: str) -> Path:
        """Save implementation to file."""
        filename = f"implementation_{task_id}.java"
        filepath = self.output_dir / filename
        
        with open(filepath, 'w', encoding='utf-8') as f:
            f.write(f"// Implementation for Task: {task_id}\n")
            f.write(f"// Generated: {datetime.now(timezone.utc).isoformat()}\n")
            f.write("// This file contains multiple classes/files - extract as needed\n\n")
            f.write(code)
        
        return filepath
    
    def extract_files_from_code(self, code: str) -> Dict[str, str]:
        """
        Extract individual files from the generated code block.
        
        Returns:
            Dict mapping file paths to code content
        """
        import re
        
        files = {}
        
        # Pattern to match file blocks: // File: path followed by code block
        pattern = r'```java\s*\n//\s*File:\s*(.+?)\n(.*?)```'
        matches = re.findall(pattern, code, re.DOTALL)
        
        for filepath, content in matches:
            filepath = filepath.strip()
            content = content.strip()
            files[filepath] = content
        
        return files
    
    def estimate_tokens(self, task: Dict, specification: str) -> int:
        """Estimate input tokens (rough approximation)."""
        prompt = self._build_prompt(task, specification, None)
        # Rough estimate: 1 token ≈ 4 characters
        return len(prompt) // 4


if __name__ == "__main__":
    import sys
    from config import OPENAI_API_KEY, GENERATED_DIR
    
    if not OPENAI_API_KEY:
        print("Error: OPENAI_API_KEY environment variable not set")
        sys.exit(1)
    
    # Test with a sample specification
    test_task = {
        "task_id": "test_001",
        "name": "Test Task",
        "module": "zakum-core",
    }
    
    test_spec = """
## Architecture Overview
Create a simple utility class for string manipulation.

## Implementation Details
- Class: StringUtils
- Method: public static String capitalize(String input)
- Should handle null/empty strings

## Error Handling
- Return empty string for null input
- Log warnings for edge cases

## Testing Strategy
- Test null input
- Test empty string
- Test normal strings
- Test already capitalized strings
"""
    
    coder = ChatGPTCoder(OPENAI_API_KEY, GENERATED_DIR)
    
    print("Generating implementation...")
    result = coder.generate_implementation(test_task, test_spec)
    
    if "error" in result and result["code"] is None:
        print(f"Error: {result['error']}")
    else:
        print(f"Implementation generated successfully!")
        print(f"Input tokens: {result['input_tokens']}")
        print(f"Output tokens: {result['output_tokens']}")
        print(f"Saved to: {result['impl_file']}")
        
        # Try to extract files
        files = coder.extract_files_from_code(result['code'])
        print(f"\nExtracted {len(files)} file(s):")
        for filepath in files:
            print(f"  - {filepath}")
