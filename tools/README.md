# Administrative Tools

This directory contains administrative and utility scripts for managing the Zakum project.

## Scripts

### admin-reset-tasks.sh

Administrative utility to reset tasks stuck in 'assigned' status back to 'ready' in `TASK_REGISTRY.json`.

**Usage:**
```bash
./tools/admin-reset-tasks.sh              # Interactive mode: shows stuck tasks and confirms
./tools/admin-reset-tasks.sh --auto       # Auto mode: resets all stuck tasks without confirmation
./tools/admin-reset-tasks.sh task-001     # Reset specific task by ID
./tools/admin-reset-tasks.sh --help       # Show help
```

**When to use:**
- When workflow dispatch fails but tasks are marked as assigned
- When tasks are stuck in 'assigned' status for an extended period

---

### stop-all-workflows.sh

Administrative utility to stop all running GitHub Actions workflows.

**Usage:**
```bash
./tools/stop-all-workflows.sh              # Interactive mode: shows running workflows and confirms
./tools/stop-all-workflows.sh --auto       # Auto mode: stops all workflows without confirmation
./tools/stop-all-workflows.sh --exclude-current  # Exclude current workflow from being stopped
./tools/stop-all-workflows.sh --help       # Show help
```

**Environment Variables Required:**
- `GITHUB_TOKEN` - GitHub API token with workflow permissions
- `GITHUB_REPOSITORY` - Repository in format 'owner/repo'
- `GITHUB_RUN_ID` - Current workflow run ID (optional, for --exclude-current)

**When to use:**
- Emergency stops when workflows need to be cancelled
- When multiple workflows are running and consuming resources unnecessarily
- Before maintenance or major changes
- When workflows are stuck or behaving unexpectedly

**Examples:**
```bash
# Stop all workflows interactively
./tools/stop-all-workflows.sh

# Stop all workflows automatically (no confirmation)
./tools/stop-all-workflows.sh --auto

# Stop all workflows except the currently running one (useful in CI)
./tools/stop-all-workflows.sh --auto --exclude-current

# When run from GitHub Actions (environment variables are automatically set)
./tools/stop-all-workflows.sh --auto --exclude-current
```

**Note:** This script uses the GitHub API to cancel workflows. It requires a valid `GITHUB_TOKEN` with appropriate permissions. When running from within a GitHub Actions workflow, these environment variables are automatically available.

---

### new-plugin-module.ps1

PowerShell script to generate new plugin module scaffolding.

**Usage:**
```powershell
.\tools\new-plugin-module.ps1
```

---

### run-process-gates.ps1

PowerShell script to run process gates verification.

**Usage:**
```powershell
.\tools\run-process-gates.ps1
```

---

## Requirements

- **Bash scripts**: Require `bash`, `jq`, and `curl`
- **PowerShell scripts**: Require PowerShell Core or Windows PowerShell
- **All scripts**: Should be run from the repository root directory

## Support

For issues or questions about these tools:
1. Check the help output of each script (`--help` flag)
2. Review the [AUTOMATION_GUIDE.md](../.github/AUTOMATION_GUIDE.md)
3. Open a GitHub issue
