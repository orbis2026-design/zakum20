# GitHub Copilot Configuration

This repository is configured with GitHub Copilot instructions to help AI coding agents work more effectively with the Zakum Suite codebase.

## Configuration Files

### 1. Repository-wide Instructions
**File**: `.github/copilot-instructions.md`

This file contains project-wide guidelines that apply to all Copilot interactions in this repository, including:
- Project overview and tech stack
- Architecture and module structure
- Critical rules (especially the API boundary enforcement)
- Development workflow with IntelliJ IDEA
- Build commands and testing guidelines
- Common pitfalls to avoid

### 2. Specialized Agents
**Directory**: `.github/agents/`

We have created four specialized agent personas to handle different types of tasks:

#### Documentation Agent (`documentation_agent.md`)
- **Purpose**: Maintains all Markdown documentation
- **Scope**: Only works in `docs/` directory and root-level `*.md` files
- **Use Case**: Documentation updates, new docs, keeping docs in sync with code

#### Testing Agent (`testing_agent.md`)
- **Purpose**: Writes and maintains unit tests
- **Scope**: Only works in `src/test/java` directories
- **Use Case**: Adding tests for new features, writing regression tests for bugs

#### Bridge Agent (`bridge_agent.md`)
- **Purpose**: Creates and maintains bridge modules for third-party plugin integrations
- **Scope**: Only works in `zakum-bridge-*` directories
- **Use Case**: New bridge integrations, updating existing bridges

#### Feature Agent (`feature_agent.md`)
- **Purpose**: Develops feature modules that extend Zakum
- **Scope**: Only works in feature module directories
- **Use Case**: New feature development, enhancing existing features
- **Critical**: Enforces the API boundary rule (`zakum-api` only, never `zakum-core`)

## How It Works

When you use GitHub Copilot in this repository:

1. **All agents** read the repository-wide instructions from `copilot-instructions.md`
2. **Task-specific agents** additionally read their specialized instructions from `.github/agents/`
3. The instructions guide the agent to:
   - Use the correct tools and workflows
   - Follow project conventions
   - Respect boundaries (what files to modify, what to avoid)
   - Run the right verification commands

## Benefits

- ✅ **Consistency**: All AI contributions follow the same patterns and standards
- ✅ **Safety**: Clear boundaries prevent accidental modifications to critical files
- ✅ **API Enforcement**: Agents are explicitly instructed about the API boundary rule
- ✅ **Quality**: Agents know about build commands, testing, and verification steps
- ✅ **Efficiency**: Specialized agents have focused knowledge for their domain

## Key Principles Encoded

### 1. API Boundary (Critical)
The most important rule: **Feature modules MUST ONLY import from `net.orbis.zakum.api.*`**
- This is enforced by `./gradlew verifyApiBoundaries`
- Both the main instructions and feature_agent emphasize this repeatedly

### 2. IntelliJ IDEA Workflow
- CLI builds are deprecated
- Always use IntelliJ's Gradle integration
- Use "Reload All Gradle Projects" after dependency changes

### 3. Minimal Changes
- Make the smallest possible changes to achieve goals
- Don't modify working code unnecessarily
- Respect existing patterns and conventions

### 4. Security
- Check dependencies for vulnerabilities
- Never commit secrets
- Use proper validation and error handling

## For Copilot Users

When assigning tasks to `@copilot` in issues or PRs:

- The agent will automatically read these instructions
- You can reference specific agents if needed: "Let the documentation_agent handle this"
- The agent will respect the boundaries defined in these files
- All changes will be subject to code review as usual

## Maintenance

These instructions should be updated when:
- Major architecture changes occur
- New critical rules are established
- Development workflow changes
- New types of specialized tasks emerge

## Resources

- [GitHub Copilot Best Practices](https://github.blog/ai-and-ml/github-copilot/how-to-write-a-great-agents-md-lessons-from-over-2500-repositories/)
- [Copilot Instructions Guide](https://design.dev/guides/copilot-instructions/)
- [GitHub Copilot Documentation](https://docs.github.com/en/copilot)
