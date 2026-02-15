#!/usr/bin/env python3
"""
Claude Analyzer for AI Orchestrator

Uses Claude API to analyze requirements and produce detailed specifications.
"""

import os
import json
from typing import Dict, Any, Optional
from anthropic import Anthropic


class ClaudeAnalyzer:
    """Claude-based requirement analyzer."""
    
    def __init__(self, api_key: Optional[str] = None, model: str = "claude-3-5-sonnet-20241022"):
        """
        Initialize Claude analyzer.
        
        Args:
            api_key: Anthropic API key (or use ANTHROPIC_API_KEY env var)
            model: Claude model to use
        """
        self.api_key = api_key or os.getenv('ANTHROPIC_API_KEY')
        if not self.api_key:
            raise ValueError("Anthropic API key required (set ANTHROPIC_API_KEY or pass api_key)")
        
        self.model = model
        self.client = Anthropic(api_key=self.api_key)
    
    def analyze_task(self, task_content: str, context: Optional[Dict[str, Any]] = None) -> Dict[str, Any]:
        """
        Analyze a task and produce detailed specification.
        
        Args:
            task_content: Full task description
            context: Additional context (project info, existing code, etc.)
            
        Returns:
            Dictionary containing analysis results with:
                - specification: Detailed technical specification
                - implementation_notes: Key implementation considerations
                - test_strategy: Testing approach
                - risks: Potential risks and mitigations
        """
        system_prompt = self._build_system_prompt()
        user_prompt = self._build_analysis_prompt(task_content, context)
        
        try:
            response = self.client.messages.create(
                model=self.model,
                max_tokens=4096,
                system=system_prompt,
                messages=[
                    {"role": "user", "content": user_prompt}
                ]
            )
            
            # Extract the response text
            response_text = response.content[0].text
            
            # Parse structured response
            analysis = self._parse_analysis_response(response_text)
            
            return {
                'success': True,
                'analysis': analysis,
                'raw_response': response_text
            }
        
        except Exception as e:
            return {
                'success': False,
                'error': str(e)
            }
    
    def _build_system_prompt(self) -> str:
        """Build system prompt for Claude."""
        return """You are a senior software architect analyzing requirements for Minecraft plugin development.

Your role is to:
1. Analyze task requirements in detail
2. Produce clear, implementable specifications
3. Identify technical challenges and solutions
4. Consider Folia/Spigot compatibility requirements
5. Suggest optimal design patterns

Focus on:
- PaperSpigot 1.21.1 + Java 21 compatibility
- Folia's regionized threading model
- Performance and scalability
- Clean architecture and maintainability
- Integration with existing Zakum API

Provide responses in a structured format with clear sections."""
    
    def _build_analysis_prompt(self, task_content: str, context: Optional[Dict[str, Any]]) -> str:
        """Build analysis prompt for specific task."""
        prompt = f"""Analyze the following plugin development task and provide a detailed specification.

TASK:
{task_content}
"""
        
        if context:
            prompt += f"\n\nADDITIONAL CONTEXT:\n"
            if 'module' in context:
                prompt += f"- Target Module: {context['module']}\n"
            if 'existing_code' in context:
                prompt += f"- Existing Code Structure:\n{context['existing_code']}\n"
            if 'project_info' in context:
                prompt += f"- Project Info:\n{context['project_info']}\n"
        
        prompt += """

Please provide:

1. SPECIFICATION:
   - Detailed technical specification
   - API design (classes, methods, interfaces)
   - Data structures and models
   - Configuration schema

2. IMPLEMENTATION NOTES:
   - Key implementation considerations
   - Folia/Spigot compatibility approach
   - Threading and concurrency strategy
   - Error handling approach

3. TEST STRATEGY:
   - Unit test coverage plan
   - Integration test scenarios
   - Performance test considerations

4. RISKS AND MITIGATIONS:
   - Potential technical risks
   - Mitigation strategies
   - Alternative approaches if needed

Structure your response clearly with section headers."""
        
        return prompt
    
    def _parse_analysis_response(self, response_text: str) -> Dict[str, Any]:
        """
        Parse structured analysis from Claude's response.
        
        Args:
            response_text: Raw response from Claude
            
        Returns:
            Structured analysis dictionary
        """
        # Extract sections based on headers
        sections = {
            'specification': '',
            'implementation_notes': '',
            'test_strategy': '',
            'risks': ''
        }
        
        current_section = None
        lines = response_text.split('\n')
        
        for line in lines:
            line_lower = line.lower().strip()
            
            # Detect section headers
            if 'specification' in line_lower and ':' in line_lower:
                current_section = 'specification'
            elif 'implementation' in line_lower and 'note' in line_lower:
                current_section = 'implementation_notes'
            elif 'test' in line_lower and 'strategy' in line_lower:
                current_section = 'test_strategy'
            elif 'risk' in line_lower:
                current_section = 'risks'
            elif current_section:
                sections[current_section] += line + '\n'
        
        # Clean up sections
        for key in sections:
            sections[key] = sections[key].strip()
        
        # If parsing failed, put everything in specification
        if not any(sections.values()):
            sections['specification'] = response_text
        
        return sections


def main():
    """Example usage and testing."""
    import sys
    
    if len(sys.argv) < 2:
        print("Usage: python claude_analyzer.py <task-description>")
        print("\nExample:")
        print("  python claude_analyzer.py 'Implement player caching layer'")
        sys.exit(1)
    
    task_description = ' '.join(sys.argv[1:])
    
    try:
        analyzer = ClaudeAnalyzer()
        
        print("Analyzing task with Claude...")
        result = analyzer.analyze_task(task_description)
        
        if result['success']:
            print("\n=== ANALYSIS COMPLETE ===\n")
            analysis = result['analysis']
            
            if analysis['specification']:
                print("SPECIFICATION:")
                print(analysis['specification'])
                print()
            
            if analysis['implementation_notes']:
                print("IMPLEMENTATION NOTES:")
                print(analysis['implementation_notes'])
                print()
            
            if analysis['test_strategy']:
                print("TEST STRATEGY:")
                print(analysis['test_strategy'])
                print()
            
            if analysis['risks']:
                print("RISKS:")
                print(analysis['risks'])
                print()
        else:
            print(f"Error: {result['error']}", file=sys.stderr)
            sys.exit(1)
    
    except Exception as e:
        print(f"Error: {e}", file=sys.stderr)
        sys.exit(1)


if __name__ == '__main__':
    main()
