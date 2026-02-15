# Zakum 24/7 Automation Workflows

This directory contains the GitHub Actions workflows that power the Zakum 24/7 automation system.

## Workflow Overview

| # | Workflow | Trigger | Purpose |
|---|----------|---------|---------|
| 00 | Manager Orchestrator | Hourly cron | Assigns tasks from registry, manages budget |
| 01 | Worker Executor | Workflow dispatch | Executes assigned tasks, creates PRs |
| 02 | 24/7 Scheduler | Every 6 hours | Budget cycles, health checks, metrics |
| 03 | Quality Gates | PR, Push | Verifies code quality, API boundaries |
| 04 | Worker Codegen | Workflow dispatch | Generates new module scaffolding |
| 05 | Worker Documentation | Weekly, Push | Auto-generates documentation |
| 06 | Worker Testing | PR, Push | Runs comprehensive test suites |
| 07 | Worker Soak | Weekly | 24/7 uptime and performance testing |
| 08 | Analytics Dashboard | Daily | Generates metrics and dashboards |
| 09 | Cost Tracking | Twice daily | Monitors budget and costs |

## Quick Start

1. The system runs automatically based on schedules
2. Manually trigger workflows from the Actions tab
3. Monitor progress in `.github/automation/` directory
4. View analytics in `.github/automation/analytics/DASHBOARD.md`

## Documentation

For complete setup and operation instructions, see:
- [AUTOMATION_GUIDE.md](../AUTOMATION_GUIDE.md)

## Task Registry

Tasks are defined in `TASK_REGISTRY.json` at the repository root:
- 29 tasks across 5 categories
- Priority-based assignment
- Dependency management
- Budget tracking

## Budget Control

- Daily limit: $25.00
- Automatic pause when exhausted
- Resets at midnight UTC
- Manual override available

## Support

For issues or questions:
1. Check workflow logs in Actions tab
2. Review [AUTOMATION_GUIDE.md](../AUTOMATION_GUIDE.md)
3. Open a GitHub issue

---
*Last Updated: 2026-02-15*
