# Zakum 24/7 Automation System Guide

**Version:** 1.0.0  
**Last Updated:** 2026-02-15  
**Status:** Production Ready

## Overview

The Zakum 24/7 Automation System is a comprehensive GitHub Actions-based workflow orchestration platform designed to automate end-to-end Minecraft Java plugin development. It manages task assignment, code generation, quality verification, testing, and deployment with built-in budget tracking and cost controls.

## Architecture

### System Components

1. **Manager/Orchestrator** (`00-manager-orchestrator.yml`)
   - Runs hourly via cron schedule
   - Reads TASK_REGISTRY.json for available tasks
   - Assigns highest-priority tasks based on ROI
   - Tracks budget and prevents overspending
   - Triggers worker workflows
   - Sends Discord notifications

2. **Worker/Executor** (`01-worker-executor.yml`)
   - Executes assigned tasks
   - Creates feature branches
   - Implements changes
   - Runs verification gates
   - Creates pull requests
   - Updates task status

3. **24/7 Scheduler** (`02-24-7-scheduler.yml`)
   - Runs every 6 hours
   - Manages budget cycles
   - Archives old budget data
   - Monitors system health
   - Detects stuck tasks
   - Generates metrics reports

4. **Quality Gates** (`03-quality-gates.yml`)
   - Validates API boundaries
   - Checks plugin descriptors
   - Verifies module conventions
   - Runs platform infrastructure checks
   - Enforces compilation standards
   - Controls merge eligibility

5. **Worker Codegen** (`04-worker-codegen.yml`)
   - Generates new module scaffolding
   - Uses `tools/new-plugin-module.ps1`
   - Creates plugin.yml, config.yml, build files
   - Verifies generated code
   - Creates PRs for new modules

6. **Worker Documentation** (`05-worker-documentation.yml`)
   - Generates API documentation (Javadoc)
   - Creates module inventory
   - Updates usage guides
   - Maintains documentation index
   - Runs weekly and on code changes

7. **Worker Testing** (`06-worker-testing.yml`)
   - Executes unit tests
   - Runs integration tests
   - Performs smoke tests
   - Generates test reports
   - Calculates coverage metrics

8. **Worker Soak** (`07-worker-soak.yml`)
   - Runs 24/7 uptime tests
   - Simulates 500+ players
   - Tests memory stress
   - Validates task saturation
   - Checks queue backpressure
   - Monitors cache lifecycle

9. **Analytics Dashboard** (`08-analytics-dashboard.yml`)
   - Collects task metrics
   - Tracks budget utilization
   - Monitors repository health
   - Generates visual dashboards
   - Runs daily reports

10. **Cost Tracking** (`09-cost-tracking.yml`)
    - Calculates workflow costs
    - Tracks budget spending
    - Generates cost reports
    - Sends budget alerts
    - Projects monthly costs

## Setup Instructions

### Prerequisites

1. **GitHub Repository**
   - Public or private repository
   - Admin access required
   - GitHub Actions enabled

2. **Required Files**
   - `TASK_REGISTRY.json` - Task definitions
   - `.github/workflows/*.yml` - Workflow files
   - `tools/new-plugin-module.ps1` - Module generator

3. **Optional Secrets**
   - `DISCORD_WEBHOOK_URL` - For Discord notifications

### Installation Steps

1. **Clone Repository**
   ```bash
   git clone https://github.com/yourusername/zakum20.git
   cd zakum20
   ```

2. **Verify Files**
   ```bash
   ls -la .github/workflows/
   ls -la TASK_REGISTRY.json
   ```

3. **Configure Secrets** (Optional)
   - Go to Settings → Secrets and variables → Actions
   - Add `DISCORD_WEBHOOK_URL` with your Discord webhook URL

4. **Initialize Budget Tracking**
   ```bash
   mkdir -p .github/automation
   ```

5. **Enable Workflows**
   - Go to Actions tab in GitHub
   - Enable GitHub Actions if disabled
   - Workflows will start automatically based on schedules

## Operation

### Automatic Operation

The system operates automatically with the following schedule:

- **Hourly** (0 * * * *) - Manager assigns tasks
- **Every 6 hours** (0 */6 * * *) - Scheduler manages cycles
- **Daily at 8 AM** (0 8 * * *) - Analytics dashboard
- **Twice daily** (0 0,12 * * *) - Cost tracking
- **Weekly on Sunday** (0 0 * * 0) - Documentation generation
- **Weekly on Saturday** (0 0 * * 6) - Soak testing

### Manual Triggering

All workflows support manual triggering via `workflow_dispatch`:

1. Go to Actions tab
2. Select workflow
3. Click "Run workflow"
4. Fill in any required inputs
5. Click "Run workflow" button

### Task Priority System

Tasks are prioritized by category:

1. **Wave A** (Priority 100) - Critical expansion modules
2. **Core Platform** (Priority 80) - Foundation infrastructure
3. **Data Hardening** (Priority 60) - Reliability and performance
4. **Features** (Priority 40) - New functionality
5. **Documentation** (Priority 20) - Guides and docs

Within each category, tasks are sorted by points (higher first).

### Budget Management

- **Daily Limit:** $25.00
- **Tracking:** Automatic per-day budget files
- **Pause:** System pauses when budget exhausted
- **Reset:** Automatic at midnight UTC
- **Override:** Manual force_assign option available

Budget files: `.github/automation/budget-YYYY-MM-DD.json`

### Task Workflow

1. **Ready** - Task available for assignment
2. **Assigned** - Manager has assigned task to worker
3. **In Progress** - Worker is executing task
4. **Testing** - Task completed, verification running
5. **Review** - PR created, awaiting review
6. **Completed** - Task merged and done
7. **Blocked** - Task failed or has issues

## Task Registry

### Structure

```json
{
  "version": "1.0.0",
  "updated": "2026-02-15T17:56:47Z",
  "budget": {
    "dailyLimit": 25.0,
    "currency": "USD",
    "trackingEnabled": true
  },
  "tasks": [
    {
      "id": "task-001",
      "name": "Task Name",
      "category": "waveA",
      "points": 10,
      "priority": 100,
      "estimatedMinutes": 120,
      "dependencies": [],
      "status": "ready",
      "description": "Task description",
      "verificationGates": ["build", "test"]
    }
  ]
}
```

### Adding New Tasks

1. Edit `TASK_REGISTRY.json`
2. Add task object with required fields
3. Set appropriate category and priority
4. Define verification gates
5. List any task dependencies
6. Commit and push changes

### Task Dependencies

Tasks can depend on other tasks:

```json
"dependencies": ["task-001", "task-002"]
```

Dependent tasks won't be assigned until dependencies are completed.

## Verification Gates

### Available Gates

- `verifyApiBoundaries` - No core imports in feature modules
- `verifyPluginDescriptors` - Valid plugin.yml files
- `verifyModuleBuildConventions` - Consistent build configuration
- `verifyPlatformInfrastructure` - Full platform checks
- `build` - Gradle build (no tests)
- `test` - Run all tests

### Running Gates Locally

```bash
# Run all platform verification gates
./gradlew verifyPlatformInfrastructure

# Run individual gates
./gradlew verifyApiBoundaries
./gradlew verifyPluginDescriptors

# Run build and tests
./gradlew build test
```

## Discord Notifications

### Setup

1. Create Discord webhook in your server
2. Add as GitHub secret: `DISCORD_WEBHOOK_URL`
3. Notifications will be sent automatically

### Notification Events

- Task assignment (Manager)
- Task completion (Worker)
- System status updates (Scheduler)
- Budget alerts (Cost Tracking)

### Notification Format

```json
{
  "embeds": [{
    "title": "🤖 Event Title",
    "description": "Event description",
    "color": 3447003,
    "fields": [
      {"name": "Field", "value": "Value"}
    ],
    "timestamp": "2026-02-15T17:56:47Z"
  }]
}
```

## Analytics and Reporting

### Dashboard Location

`.github/automation/analytics/DASHBOARD.md`

### Metrics Tracked

- Task status distribution
- Points progress
- Budget utilization
- Repository statistics
- Workflow activity
- Module count
- Code metrics

### Cost Reports

`.github/automation/reports/cost-report-YYYYMMDD.md`

### Budget Files

`.github/automation/budget-YYYY-MM-DD.json`

Archived to: `.github/automation/archive/`

## Troubleshooting

### Workflows Not Running

1. Check Actions tab for errors
2. Verify workflow files are valid YAML
3. Ensure repository has Actions enabled
4. Check branch protection rules

### Budget Exhausted

1. Check current budget file
2. Wait for midnight UTC reset
3. Or manually trigger with `force_assign: true`

### Tasks Stuck in 'assigned' Status

Tasks may become stuck in 'assigned' status if workflow dispatch fails (e.g., HTTP 422 errors due to incorrect parameters).

**Common causes:**
- Workflow dispatch HTTP 422 error (invalid parameters)
- GitHub Actions service outage
- Network connectivity issues
- Rate limiting

**Solutions:**

1. **Use the admin reset utility** (Recommended):
   ```bash
   # Interactive mode - shows stuck tasks and asks for confirmation
   ./tools/admin-reset-tasks.sh
   
   # Auto mode - resets all stuck tasks without confirmation
   ./tools/admin-reset-tasks.sh --auto
   
   # Reset specific task(s)
   ./tools/admin-reset-tasks.sh data-001 data-002
   ```
   
   The script will:
   - Create a backup of TASK_REGISTRY.json
   - Reset specified or all 'assigned' tasks to 'ready' status
   - Provide git commands for committing changes

2. **Manual reset** (Alternative):
   - Edit TASK_REGISTRY.json
   - Find tasks with `"status": "assigned"`
   - Change to `"status": "ready"`
   - Remove the `"assignedAt"` field
   - Commit and push changes

3. **Check workflow logs**:
   - Go to Actions tab
   - Check 00-manager-orchestrator workflow logs
   - Look for workflow dispatch errors
   - Fix any configuration issues

4. **Re-run failed workflows**:
   - After fixing issues, tasks will be picked up in next orchestrator run
   - Or manually trigger the orchestrator with `workflow_dispatch`

### Workflow Dispatch HTTP 422 Errors

HTTP 422 errors typically indicate invalid workflow parameters.

**For 06-worker-testing.yml errors:**
- Ensure only `task_id` and optionally `test_scope` are passed
- Do not pass `task_json` parameter (not supported by this workflow)
- The orchestrator has been fixed to handle this correctly

**General debugging:**
1. Check workflow definition in `.github/workflows/`
2. Verify `workflow_dispatch` inputs match what's being passed
3. Review orchestrator logs for exact error messages
4. Check GitHub Status page for API issues

### Tasks Stuck (General)

1. Check Scheduler workflow output
2. Look for stuck task reports
3. Manually update task status in TASK_REGISTRY.json
4. Re-run failed workflows

### Quality Gates Failing

1. Review gate output in workflow logs
2. Fix issues locally first
3. Run gates locally: `./gradlew verifyPlatformInfrastructure`
4. Push fixes and re-run workflow

### PR Creation Failing

1. Check GitHub token permissions
2. Verify branch doesn't already exist
3. Ensure no merge conflicts
4. Check for repository protection rules

## Best Practices

### Task Design

- Keep tasks small and focused (< 3 hours)
- Define clear verification gates
- Write detailed descriptions
- Set realistic point values
- Specify all dependencies

### Budget Management

- Monitor daily spending
- Prioritize high-ROI tasks
- Use manual triggers sparingly
- Review cost reports weekly

### Quality Control

- Always run verification gates
- Fix issues before merge
- Keep API boundaries clean
- Maintain plugin descriptors
- Write tests for new code

### Documentation

- Update docs with code changes
- Keep README.md current
- Document breaking changes
- Maintain changelog

## Advanced Configuration

### Adjusting Schedule

Edit cron expressions in workflow files:

```yaml
on:
  schedule:
    - cron: '0 * * * *'  # Every hour
    - cron: '0 0 * * *'  # Daily at midnight
    - cron: '0 0 * * 0'  # Weekly on Sunday
```

### Changing Budget Limit

Edit `TASK_REGISTRY.json`:

```json
"budget": {
  "dailyLimit": 50.0,  // Change from 25.0
  "currency": "USD",
  "trackingEnabled": true
}
```

### Adding Custom Workers

1. Create new workflow file: `.github/workflows/XX-worker-custom.yml`
2. Follow existing worker patterns
3. Add to manager routing logic
4. Update documentation

### Modifying Quality Gates

Edit `03-quality-gates.yml`:

```yaml
- name: Custom gate
  run: |
    ./gradlew customTask --no-daemon
```

## Administrative Tools

### Task Reset Utility

The `admin-reset-tasks.sh` script helps recover from stuck tasks.

**Location:** `tools/admin-reset-tasks.sh`

**Use cases:**
- Tasks stuck in 'assigned' status due to workflow dispatch failures
- HTTP 422 errors during task assignment
- GitHub Actions service disruptions
- Need to retry failed task assignments

**Usage:**

```bash
# Interactive mode (recommended for manual use)
./tools/admin-reset-tasks.sh

# Automatic mode (for scripts/automation)
./tools/admin-reset-tasks.sh --auto

# Reset specific tasks
./tools/admin-reset-tasks.sh data-001
./tools/admin-reset-tasks.sh data-001 data-002

# Show help
./tools/admin-reset-tasks.sh --help
```

**Features:**
- Automatic backup before changes
- Interactive confirmation (unless --auto)
- Support for resetting specific tasks or all stuck tasks
- Colorized output for better readability
- Validation of task status before reset

**Example workflow:**

1. Identify stuck tasks:
   ```bash
   ./tools/admin-reset-tasks.sh
   ```
   
2. Review the list of stuck tasks

3. Confirm reset (or press Ctrl+C to cancel)

4. The script creates a backup and resets tasks

5. Review changes:
   ```bash
   git diff TASK_REGISTRY.json
   ```

6. Commit and push:
   ```bash
   git add TASK_REGISTRY.json
   git commit -m "chore: reset stuck tasks"
   git push origin master
   ```

**Recovery from errors:**

If you need to restore the backup:
```bash
cp TASK_REGISTRY.json.backup-YYYYMMDD-HHMMSS TASK_REGISTRY.json
```

## Security

### Secrets Management

- Never commit secrets to repository
- Use GitHub Secrets for sensitive data
- Rotate secrets regularly
- Limit secret access

### Permissions

Workflows use minimal required permissions:

```yaml
permissions:
  contents: write      # For commits
  pull-requests: write # For PRs
  issues: write        # For issue creation
```

### Branch Protection

Recommended settings:

- Require pull request reviews
- Require status checks (Quality Gates)
- Require branches to be up to date
- No force pushes

## Support and Maintenance

### Monitoring

- Check daily analytics dashboard
- Review cost reports
- Monitor stuck tasks
- Watch budget alerts

### Updates

- Review and merge automation PRs
- Update TASK_REGISTRY.json regularly
- Keep workflows up to date
- Archive completed tasks

### Cleanup

- Archive old budget files monthly
- Remove completed tasks from registry
- Clean up merged branches
- Update documentation

## References

### Documentation

- [Plugin Dev Kit](../docs/23-PLUGIN-DEVKIT.md)
- [Core Primer](../docs/27-CORE-PRIMER.md)
- [Infrastructure Directive](../docs/22-ANY-PLUGIN-INFRASTRUCTURE-DIRECTIVE.md)
- [Ecosystem Consolidation](../docs/22-PLUGIN-ECOSYSTEM-CONSOLIDATION.md)

### Tools

- [Module Generator](../tools/new-plugin-module.ps1)
- [Process Gates](../tools/run-process-gates.ps1)

### GitHub Resources

- [GitHub Actions Documentation](https://docs.github.com/en/actions)
- [Workflow Syntax](https://docs.github.com/en/actions/reference/workflow-syntax-for-github-actions)
- [GitHub API](https://docs.github.com/en/rest)

## Version History

- **1.0.0** (2026-02-15) - Initial release
  - 10 workflow system
  - 29 task registry
  - Budget tracking
  - Quality gates
  - Analytics dashboard
  - Cost monitoring

## License

This automation system is part of the Zakum project and follows the same license as the main repository.

---

*For questions or issues, please open a GitHub issue or contact the development team.*
