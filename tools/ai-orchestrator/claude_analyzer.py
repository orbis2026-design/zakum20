"""
Claude Analyzer - Uses Claude Sonnet 4.5 API to generate technical specifications.
Input: task requirements + relevant docs
Output: technical specification (markdown)
"""

import os
import json
from pathlib import Path
from typing import Dict, Optional
from datetime import datetime, timezone
import anthropic


class ClaudeAnalyzer:
    """Generates technical specifications using Claude API."""
    
    def __init__(self, api_key: str, output_dir: Path):
        self.api_key = api_key
        self.output_dir = Path(output_dir)
        self.output_dir.mkdir(exist_ok=True, parents=True)
        self.client = anthropic.Anthropic(api_key=api_key)
        self.model = "claude-sonnet-4-20250514"  # Claude Sonnet 4.5
    
    def generate_specification(self, task: Dict, context_docs: Optional[str] = None) -> Dict:
        """
        Generate a technical specification for the given task.
        
        Returns:
            Dict with keys: specification (str), input_tokens (int), output_tokens (int)
        """
        prompt = self._build_prompt(task, context_docs)
        
        try:
            response = self.client.messages.create(
                model=self.model,
                max_tokens=8000,
                temperature=0.3,  # Lower temperature for more focused technical specs
                messages=[
                    {"role": "user", "content": prompt}
                ]
            )
            
            specification = response.content[0].text
            
            # Extract token usage
            input_tokens = response.usage.input_tokens
            output_tokens = response.usage.output_tokens
            
            # Save specification to file
            spec_file = self._save_specification(task["task_id"], specification)
            
            return {
                "specification": specification,
                "input_tokens": input_tokens,
                "output_tokens": output_tokens,
                "spec_file": str(spec_file),
                "model": self.model,
                "timestamp": datetime.now(timezone.utc).isoformat()
            }
            
        except Exception as e:
            return {
                "error": str(e),
                "specification": None,
                "input_tokens": 0,
                "output_tokens": 0
            }
    
    def _build_prompt(self, task: Dict, context_docs: Optional[str] = None) -> str:
        """Build the prompt for Claude."""
        prompt = f"""You are a senior software architect specializing in Minecraft plugin development with Java 21 and PaperSpigot 1.21.1.

Task Information:
- ID: {task['task_id']}
- Name: {task['name']}
- Priority: {task['priority']}
- Module: {task['module']}
- Language: {task['language']}

Overview:
{task['overview']}

Requirements:
{task['requirements']}

Success Criteria:
{task['success_criteria']}
"""
        
        if context_docs:
            prompt += f"\n\nRelevant Documentation Context:\n{context_docs}\n"
        
        prompt += """
Please generate a detailed technical specification for implementing this task. Include:

1. **Architecture Overview**
   - High-level design approach
   - Key components and their responsibilities
   - Integration points with existing systems

2. **Implementation Details**
   - Class structure and hierarchy
   - Method signatures and responsibilities
   - Data structures and models
   - Thread safety considerations (Folia compatibility)

3. **API Design**
   - Public interfaces
   - Method contracts
   - Event handling
   - Configuration options

4. **Error Handling**
   - Exception types to define or use
   - Error recovery strategies
   - Logging requirements

5. **Testing Strategy**
   - Unit test scenarios
   - Integration test requirements
   - Edge cases to cover

6. **Performance Considerations**
   - Optimization opportunities
   - Resource usage
   - Scalability concerns

7. **Documentation Requirements**
   - JavaDoc comments needed
   - Usage examples
   - Configuration documentation

8. **Migration/Compatibility**
   - Backward compatibility concerns
   - Breaking changes (if any)
   - Upgrade path

Format the specification as clear, structured markdown that a developer can directly use to implement the feature.
Focus on being specific and actionable rather than vague or generic.
"""
        
        return prompt
    
    def _save_specification(self, task_id: str, specification: str) -> Path:
        """Save specification to markdown file."""
        filename = f"spec_{task_id}.md"
        filepath = self.output_dir / filename
        
        with open(filepath, 'w', encoding='utf-8') as f:
            f.write(f"# Technical Specification: {task_id}\n\n")
            f.write(f"Generated: {datetime.now(timezone.utc).isoformat()}\n\n")
            f.write("---\n\n")
            f.write(specification)
        
        return filepath
    
    def estimate_tokens(self, task: Dict, context_docs: Optional[str] = None) -> int:
        """Estimate input tokens for a task (rough approximation)."""
        prompt = self._build_prompt(task, context_docs)
        # Rough estimate: 1 token ≈ 4 characters
        return len(prompt) // 4


def load_context_docs(task: Dict, docs_dir: Path) -> Optional[str]:
    """Load relevant documentation based on task module and requirements."""
    context_parts = []
    
    # Map modules to relevant doc files
    doc_mapping = {
        "zakum-core": ["00-OVERVIEW.md", "02-THREADING.md", "14-CORE-API-FOUNDATION.md"],
        "zakum-battlepass": ["11-BATTLEPASS.md"],
        "zakum-bridge-commandapi": ["09-COMMANDAPI.md"],
        "zakum-bridge-citizens": ["05-BRIDGES.md"],
        "zakum-bridge-mythicmobs": ["05-BRIDGES.md"],
    }
    
    module = task.get("module", "zakum-core")
    doc_files = doc_mapping.get(module, ["00-OVERVIEW.md"])
    
    for doc_file in doc_files:
        doc_path = docs_dir / doc_file
        if doc_path.exists():
            with open(doc_path, 'r', encoding='utf-8') as f:
                content = f.read()
                context_parts.append(f"## From {doc_file}\n\n{content}\n\n")
    
    # Limit context to avoid token limits
    full_context = "".join(context_parts)
    if len(full_context) > 20000:  # Limit to ~5000 tokens
        full_context = full_context[:20000] + "\n\n[Context truncated...]"
    
    return full_context if context_parts else None


if __name__ == "__main__":
    import sys
    from config import ANTHROPIC_API_KEY, GENERATED_DIR
    
    if not ANTHROPIC_API_KEY:
        print("Error: ANTHROPIC_API_KEY environment variable not set")
        sys.exit(1)
    
    # Test with a sample task
    test_task = {
        "task_id": "test_001",
        "name": "Test Task",
        "priority": "high",
        "module": "zakum-core",
        "language": "java",
        "overview": "This is a test task for the Claude analyzer.",
        "requirements": "- Implement basic functionality\n- Add error handling",
        "success_criteria": "✅ Tests pass\n✅ Code compiles"
    }
    
    analyzer = ClaudeAnalyzer(ANTHROPIC_API_KEY, GENERATED_DIR)
    
    print("Generating specification...")
    result = analyzer.generate_specification(test_task)
    
    if "error" in result and result["specification"] is None:
        print(f"Error: {result['error']}")
    else:
        print(f"Specification generated successfully!")
        print(f"Input tokens: {result['input_tokens']}")
        print(f"Output tokens: {result['output_tokens']}")
        print(f"Saved to: {result['spec_file']}")
        print(f"\nFirst 500 characters:\n{result['specification'][:500]}...")
