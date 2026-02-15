# AI Orchestrator

This directory contains the AI development automation system that enables continuous AI-driven development using Claude and ChatGPT.

## Overview

The system orchestrates multi-model AI collaboration:
- **Claude** analyzes requirements and produces detailed specifications
- **ChatGPT** generates Java implementation based on specs
- **Validator** ensures code compiles and meets quality standards
- **Discord** notifies team of PR creation and status updates

## Setup

1. Install Python dependencies:
```bash
pip install -r requirements.txt
```

2. Configure environment variables:
```bash
export ANTHROPIC_API_KEY="your-claude-api-key"
export OPENAI_API_KEY="your-openai-api-key"
export DISCORD_WEBHOOK_URL="your-discord-webhook"
export GITHUB_TOKEN="your-github-token"
```

3. Run the orchestrator:
```bash
python orchestrator.py
```

## Components

- `parse_docs.py` - Extracts tasks from markdown documentation
- `claude_analyzer.py` - Claude API integration for requirement analysis
- `chatgpt_coder.py` - ChatGPT API integration for code generation
- `validator.py` - Code validation (compilation, formatting, Folia compatibility)
- `discord_notifier.py` - Discord webhook integration
- `orchestrator.py` - Main controller coordinating all components

## Configuration

Edit `config.yaml` to customize:
- AI model settings
- Validation rules
- Task priorities
- Project-specific settings

## Feedback System

The `feedback/` directory stores successful PR implementations to improve future generations.
