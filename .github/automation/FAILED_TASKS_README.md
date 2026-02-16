# Failed Task Recovery System

## Overview

The Failed Task Recovery System tracks workflow dispatch failures and implements a circuit breaker pattern to prevent endless retry loops.

## File Location

`.github/automation/FAILED_TASKS.json`

## Schema

```json
{
  "tasks": [
    {
      "id": "task-id",
      "name": "Task Name",
      "category": "category",
      "points": 10,
      "priority": 100,
      "estimatedMinutes": 120,
      "dependencies": [],
      "status": "ready",
      "description": "Task description",
      "verificationGates": ["gate1", "gate2"],
      "failedAttempts": 1,
      "lastAttempt": "2026-02-16T03:44:36.180Z"
    }
  ],
  "lastUpdated": "2026-02-16T03:44:36.180Z"
}
```

## How It Works

### 1. Dispatch Tracking

When the manager orchestrator dispatches tasks to worker workflows, it tracks which dispatches succeed and which fail.

### 2. Failed Task Recording

If a dispatch fails (HTTP 422, network error, etc.):
- The task is added to `FAILED_TASKS.json` with `failedAttempts: 1`
- The `lastAttempt` timestamp is recorded

### 3. Retry Increment

If a previously-failed task is attempted again and fails:
- The `failedAttempts` counter is incremented
- The `lastAttempt` timestamp is updated

### 4. Success Recovery

When a previously-failed task successfully dispatches:
- It is automatically removed from `FAILED_TASKS.json`
- No manual intervention needed

### 5. Circuit Breaker

Tasks with more than 3 failed attempts are flagged:
```
🚫 Tasks with >3 failures (should be reviewed):
  - task-id: 4 attempts
```

**Retry Policy:**
- Maximum retries: 5 attempts per task
- Backoff strategy: Exponential (10s, 20s, 30s between retries within a dispatch)
- Tasks exceeding 5 failed attempts are marked for manual review
- Automatic removal on successful dispatch

These tasks should be:
- Manually reviewed for root cause
- Fixed if there's a workflow configuration issue
- Removed from `TASK_REGISTRY.json` if they're no longer needed
- Reset in `FAILED_TASKS.json` if the issue is resolved

## Workflow Integration

### Manager Orchestrator (00-manager-orchestrator.yml)

The "Track failed task dispatches" step:
1. Compares attempted tasks vs successfully dispatched tasks
2. Updates `FAILED_TASKS.json` with failures
3. Removes successful tasks from the failed list
4. Commits and pushes the updated file
5. Logs summary of failed tasks

## Resolution Steps

### For Tasks with >3 Failures

1. **Check workflow logs** for the specific error
2. **Common issues:**
   - Invalid input parameters
   - Workflow file syntax errors
   - Missing workflow file
   - Permission issues
3. **Fix the root cause**
4. **Reset the task:**
   - Remove from `FAILED_TASKS.json` manually, or
   - Let it automatically clear on next successful dispatch

### Manual Reset

To manually reset a task's failure count:

```bash
# Edit FAILED_TASKS.json
# Remove the task entry or reset failedAttempts to 0
git add .github/automation/FAILED_TASKS.json
git commit -m "chore: reset failed task tracking for task-id"
git push
```

## Monitoring

The manager orchestrator logs provide visibility:

```
🔍 Checking for failed dispatches...
❌ Task cleanup-001 failed to dispatch
📊 Failed tasks tracking: 1 task(s) in failed state
⚠️ Tasks with failed dispatch attempts:
  - cleanup-001: 3 attempt(s)
```

## Related Files

- `.github/workflows/00-manager-orchestrator.yml` - Manager workflow
- `TASK_REGISTRY.json` - Task definitions
- `.github/automation/FAILED_TASKS.json` - Failed task tracking

## See Also

- [Workflow Dispatch Error Fix Documentation](../docs/workflow-dispatch-fix.md)
- [Task Registry Documentation](TASK_REGISTRY_DOCS.md)
