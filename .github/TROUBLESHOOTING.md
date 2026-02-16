# Workflow System Troubleshooting Guide

## Common Issues and Solutions

### 1. HTTP 422: Unexpected Inputs Error

**Symptom:** Workflow dispatch fails with "HTTP 422: Unexpected inputs provided"

**Cause:** Worker workflow doesn't accept the input parameters being sent by the manager

**Solution:**
1. Check which workflow is failing in the manager orchestrator logs
2. Verify the workflow's input parameters in the workflow file:
   ```yaml
   on:
     workflow_dispatch:
       inputs:
         task_id:
           description: 'Task ID'
           required: true
   ```
3. Ensure the manager is sending only the inputs that the workflow accepts
4. For documentation tasks → send only `task_id`
5. For other tasks → send `task_id` and `task_json`

**Reference:** See `.github/workflows/00-manager-orchestrator.yml` lines 207-224

---

### 2. Secrets Not Accessible

**Symptom:** Workflow logs show "⚠️ OPENAI_API_KEY is not configured"

**Cause:** Secret not configured in repository settings or wrong scope

**Solution:**
1. Go to **Settings → Secrets and variables → Actions**
2. Verify the secret exists with the exact name (case-sensitive)
3. Check you're in the correct repository (not a fork)
4. Ensure the workflow is running on a branch with access to secrets
5. Repository secrets are not available in pull requests from forks

**How to verify:**
```bash
# Run manager orchestrator manually
gh workflow run 00-manager-orchestrator.yml

# Check the "Validate secret accessibility" step in logs
```

**Reference:** See `.github/SECRETS-AND-LABELS-SETUP.md`

---

### 3. Tasks Stuck in "assigned" Status

**Symptom:** Tasks remain in "assigned" status indefinitely

**Cause:** Worker workflow failed or didn't update task status

**Solution:**

**Option 1: Use Admin Reset Tool**
```bash
# Interactive mode
./tools/admin-reset-tasks.sh

# Auto mode (resets all stuck tasks)
./tools/admin-reset-tasks.sh --auto

# Reset specific task
./tools/admin-reset-tasks.sh task-001
```

**Option 2: Manual Reset**
```bash
# Edit TASK_REGISTRY.json
# Change task status from "assigned" to "ready"
# Remove "assignedAt" timestamp

git add TASK_REGISTRY.json
git commit -m "chore: reset stuck task"
git push
```

**Prevention:**
- Check workflow logs for errors
- Ensure worker workflows have proper error handling
- Verify timeouts are appropriate (currently 240 minutes)

---

### 4. Failed Task Recovery Loop

**Symptom:** Same tasks keep failing repeatedly

**Cause:** Underlying issue not resolved, circuit breaker triggered

**Solution:**

**Check Failed Tasks File:**
```bash
cat .github/automation/FAILED_TASKS.json
```

**Identify Tasks with >3 Failures:**
- These tasks are flagged for manual review
- Max 5 retries before abandonment

**Common Causes:**
1. **Invalid Workflow:** Workflow file has syntax errors
2. **Invalid Inputs:** Task data doesn't match workflow expectations
3. **Permission Issues:** Workflow lacks necessary permissions
4. **Timeout:** Task exceeds workflow timeout

**Resolution Steps:**
1. Check the specific workflow file mentioned in logs
2. Fix the identified issue
3. Reset failed task counter:
   ```bash
   # Edit .github/automation/FAILED_TASKS.json
   # Remove the task entry or set failedAttempts to 0
   git add .github/automation/FAILED_TASKS.json
   git commit -m "chore: reset failed task counter"
   git push
   ```

**Reference:** See `.github/automation/FAILED_TASKS_README.md`

---

### 5. Budget Exhausted Too Quickly

**Symptom:** "Daily budget exhausted" message appears early

**Cause:** Tasks consuming more budget than estimated

**Solution:**

**Check Current Budget:**
```bash
TODAY=$(date +%Y-%m-%d)
cat .github/automation/budget-$TODAY.json
```

**Adjust Budget Limit:**
```bash
# Edit the budget file
jq '.limit = 50' .github/automation/budget-$TODAY.json > tmp.json
mv tmp.json .github/automation/budget-$TODAY.json

# Or wait for next day (budget resets at midnight UTC)
```

**Force Task Assignment:**
```bash
# Override budget check
gh workflow run 00-manager-orchestrator.yml -f force_assign=true
```

**Review Cost Estimates:**
- Check `TASK_REGISTRY.json` for `estimatedMinutes`
- Current formula: `(minutes * 0.008) / 60 = cost in USD`
- Adjust task estimates if needed

---

### 6. Gradle Build Failures

**Symptom:** Worker workflows fail during `./gradlew build`

**Common Causes:**

**A. Wrong Java Version**
```yaml
# Solution: Ensure workflow uses Java 21
- name: Set up Java 21
  uses: actions/setup-java@v4
  with:
    distribution: 'temurin'
    java-version: '21'
```

**B. Gradle Wrapper Not Executable**
```bash
# Solution: Make wrapper executable
chmod +x gradlew
git add gradlew
git commit -m "fix: make gradlew executable"
```

**C. Dependency Download Failures**
```bash
# Solution: Add retry logic or check network
./gradlew build --refresh-dependencies --no-daemon
```

**D. Test Failures**
```bash
# Solution: Skip tests during build
./gradlew build -x test --no-daemon
```

**Reference:** See `DEPENDENCY-MANIFEST.md` for build requirements

---

### 7. Workflow Not Triggering

**Symptom:** Scheduled workflow doesn't run or manual dispatch fails

**Cause:** Multiple possible causes

**Solution:**

**For Scheduled Workflows:**
1. Check cron syntax in workflow file
2. Ensure repository is not archived
3. Verify workflow is enabled in Actions tab
4. Scheduled workflows don't run on inactive repositories

**For Manual Dispatch:**
```bash
# Check workflow exists and is valid
gh workflow list

# View specific workflow
gh workflow view 00-manager-orchestrator.yml

# Run workflow manually
gh workflow run 00-manager-orchestrator.yml
```

**For Workflow Dispatch Triggers:**
1. Check `GH_TOKEN` is available
2. Verify token has `actions: write` permission
3. Check rate limits (5000 API calls per hour)

---

### 8. Git Push Conflicts

**Symptom:** "Push failed" or "Pull failed, there may be conflicts"

**Cause:** Multiple workflows updating same files simultaneously

**Solution:**

**Automatic Retry (Built-in):**
- Workflows automatically retry push 3 times
- Pulls latest changes between retries

**Manual Resolution:**
```bash
# Clone repo locally
git clone https://github.com/your-org/zakum20
cd zakum20

# Pull latest changes
git pull origin master

# Check for conflicts
git status

# Resolve conflicts manually
# Edit conflicting files
git add .
git commit -m "chore: resolve merge conflicts"
git push origin master
```

**Prevention:**
- Use single-task mode (current default)
- Increase delays between task dispatches
- Implement file-level locking (future enhancement)

---

### 9. Discord Notifications Not Sending

**Symptom:** No Discord messages despite successful task assignments

**Cause:** Webhook URL not configured or invalid

**Solution:**

**Verify Webhook:**
1. Check secret exists: **Settings → Secrets → DISCORD_WEBHOOK_URL**
2. Test webhook manually:
   ```bash
   curl -X POST "$WEBHOOK_URL" \
     -H "Content-Type: application/json" \
     -d '{"content":"Test notification"}'
   ```
3. Ensure webhook URL is valid and not expired
4. Check Discord server permissions

**Workflow Check:**
```yaml
# Notification only sends if:
# 1. Tasks were assigned
# 2. DISCORD_WEBHOOK_URL secret exists
env:
  DISCORD_WEBHOOK_URL: ${{ secrets.DISCORD_WEBHOOK_URL }}
```

---

### 10. 24/7 Loop Not Working

**Symptom:** Workflows stop after completing tasks

**Cause:** Seamless looping not triggering or budget exhausted

**Solution:**

**Check Loop Logic:**
1. Verify manager orchestrator completed successfully
2. Check "Check for more ready tasks" step in logs
3. Ensure budget is not exhausted
4. Verify there are ready tasks available

**Manual Trigger:**
```bash
# Restart the loop manually
gh workflow run 00-manager-orchestrator.yml
```

**Monitor Loop:**
```bash
# Watch workflow runs
gh run list --workflow=00-manager-orchestrator.yml

# View specific run
gh run view <run-id>
```

**Rate Limit Protection:**
- 30-second delay between cycles
- Prevents GitHub API rate limiting
- Respects budget constraints

---

## Diagnostic Commands

### Check System Status
```bash
# View all workflow runs
gh run list --limit 20

# Check failed runs
gh run list --status failure

# View specific run logs
gh run view <run-id> --log
```

### Check Task Registry
```bash
# Count tasks by status
jq '[.tasks | group_by(.status)[] | {status: .[0].status, count: length}]' TASK_REGISTRY.json

# Find stuck tasks
jq '.tasks[] | select(.status == "assigned")' TASK_REGISTRY.json

# Find ready tasks
jq '.tasks[] | select(.status == "ready")' TASK_REGISTRY.json
```

### Check Failed Tasks
```bash
# View all failed tasks
cat .github/automation/FAILED_TASKS.json

# Count failed tasks
jq '.tasks | length' .github/automation/FAILED_TASKS.json

# Find tasks exceeding retry limit
jq '.tasks[] | select(.failedAttempts > 5)' .github/automation/FAILED_TASKS.json
```

### Check Budget
```bash
# Current budget
TODAY=$(date +%Y-%m-%d)
cat .github/automation/budget-$TODAY.json

# Budget history
ls -la .github/automation/archive/
```

---

## Getting Help

If you've tried these solutions and still have issues:

1. **Check Workflow Logs:**
   - Go to **Actions** tab in GitHub
   - Find the failed workflow run
   - Review step-by-step logs

2. **Check Documentation:**
   - [AUTOMATION_SYSTEM.md](../AUTOMATION_SYSTEM.md) - System overview
   - [SECRETS-AND-LABELS-SETUP.md](.github/SECRETS-AND-LABELS-SETUP.md) - Configuration
   - [FAILED_TASKS_README.md](.github/automation/FAILED_TASKS_README.md) - Recovery system

3. **Open an Issue:**
   - Include workflow run ID
   - Include error messages from logs
   - Describe what you've already tried
   - Tag with `infrastructure` label

---

**Last Updated:** 2026-02-16
**Maintainer:** GitHub Actions Bot
