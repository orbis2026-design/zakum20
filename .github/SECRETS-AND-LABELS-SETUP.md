# GitHub Secrets and Labels Setup Guide

## Required Secrets Configuration

The automation system requires the following secrets to be configured in GitHub repository settings.

### How to Configure Secrets

1. Go to your GitHub repository
2. Navigate to **Settings** → **Secrets and variables** → **Actions**
3. Click **New repository secret**
4. Add each secret below

### Required Secrets

#### 1. OPENAI_API_KEY
- **Purpose**: Used by worker workflows for AI-assisted code generation and documentation
- **Format**: `sk-...` (OpenAI API key)
- **How to obtain**: 
  1. Go to https://platform.openai.com/api-keys
  2. Create a new API key
  3. Copy the key (starts with `sk-`)
- **Security**: Never commit this key to the repository
- **Usage**: Currently logged but not actively used (will be implemented in future iterations)

#### 2. ANTHROPIC_API_KEY
- **Purpose**: Used by worker workflows for AI-assisted tasks (alternative to OpenAI)
- **Format**: `sk-ant-...` (Anthropic Claude API key)
- **How to obtain**:
  1. Go to https://console.anthropic.com/
  2. Navigate to API Keys
  3. Create a new API key
  4. Copy the key (starts with `sk-ant-`)
- **Security**: Never commit this key to the repository
- **Usage**: Currently logged but not actively used (will be implemented in future iterations)

#### 3. DISCORD_WEBHOOK_URL (Optional)
- **Purpose**: Send notifications about workflow execution to Discord
- **Format**: `https://discord.com/api/webhooks/...`
- **How to obtain**:
  1. Open your Discord server
  2. Go to Server Settings → Integrations → Webhooks
  3. Create a new webhook
  4. Copy the webhook URL
- **Security**: Keep this URL private
- **Usage**: Active - sends notifications when tasks are assigned

### Verifying Secret Configuration

After adding secrets, you can verify they are accessible by:

1. **Run the Manager Workflow**:
   - Go to **Actions** → **00 - Manager Orchestrator**
   - Click **Run workflow**
   - Check the "Validate secret accessibility" step in the workflow logs

2. **Check Worker Workflows**:
   - Run any worker workflow (e.g., 01 - Worker Executor)
   - Check the "Validate API keys" step in the workflow logs
   - You should see ✅ for configured secrets and ⚠️ for missing ones

### Secret Usage in Workflows

Secrets are accessed in workflows using the syntax:
```yaml
env:
  OPENAI_API_KEY: ${{ secrets.OPENAI_API_KEY }}
  ANTHROPIC_API_KEY: ${{ secrets.ANTHROPIC_API_KEY }}
  DISCORD_WEBHOOK_URL: ${{ secrets.DISCORD_WEBHOOK_URL }}
```

### Troubleshooting

#### Secret Not Detected
- **Problem**: Workflow shows "⚠️ SECRET_NAME is not configured"
- **Solution**: 
  1. Verify the secret exists in repository settings
  2. Check the secret name matches exactly (case-sensitive)
  3. Ensure you're running the workflow on the correct branch
  4. Repository secrets are not available in forked repositories (security feature)

#### HTTP 401 Errors When Using API Keys
- **Problem**: API calls fail with authentication errors
- **Solution**:
  1. Regenerate the API key on the provider's website
  2. Update the secret in GitHub repository settings
  3. Ensure the API key has not expired

---

## Required GitHub Labels

The automation system uses labels to categorize issues and pull requests.

### How to Create Labels

1. Go to your GitHub repository
2. Navigate to **Issues** → **Labels**
3. Click **New label**
4. Add each label below

### Required Labels

#### 1. automation
- **Color**: `#0E8A16` (green)
- **Description**: Automated task execution by GitHub Actions
- **Usage**: Applied to PRs created by the automation system

#### 2. urgent
- **Color**: `#D93F0B` (red)
- **Description**: High priority task requiring immediate attention
- **Usage**: Applied to critical tasks and failures

#### 3. enhancement
- **Color**: `#A2EEEF` (light blue)
- **Description**: New feature or enhancement
- **Usage**: Applied to feature tasks

#### 4. documentation
- **Color**: `#0075CA` (blue)
- **Description**: Documentation updates
- **Usage**: Applied to documentation tasks

#### 5. testing
- **Color**: `#FBCA04` (yellow)
- **Description**: Testing and quality assurance
- **Usage**: Applied to test-related tasks

#### 6. infrastructure
- **Color**: `#BFD4F2` (light purple)
- **Description**: Infrastructure and build system changes
- **Usage**: Applied to platform and core tasks

### Bulk Label Creation Script

You can create all labels at once using the GitHub CLI:

```bash
gh label create automation --color 0E8A16 --description "Automated task execution by GitHub Actions"
gh label create urgent --color D93F0B --description "High priority task requiring immediate attention"
gh label create enhancement --color A2EEEF --description "New feature or enhancement"
gh label create documentation --color 0075CA --description "Documentation updates"
gh label create testing --color FBCA04 --description "Testing and quality assurance"
gh label create infrastructure --color BFD4F2 --description "Infrastructure and build system changes"
```

### Verifying Labels

After creating labels, verify they exist:

```bash
gh label list
```

---

## Security Best Practices

1. **Never Log Secret Values**: Always use `echo "✅ SECRET is available"` instead of `echo $SECRET`
2. **Rotate Keys Regularly**: Update API keys every 90 days
3. **Use Minimal Permissions**: Ensure API keys have only the permissions they need
4. **Monitor Usage**: Check API usage dashboards to detect unauthorized usage
5. **Revoke Compromised Keys**: Immediately revoke and replace any exposed keys

---

## Support

If you encounter issues with secrets or labels:
1. Check the workflow logs for specific error messages
2. Review this guide for configuration steps
3. Consult the [AUTOMATION_SYSTEM.md](../AUTOMATION_SYSTEM.md) for system architecture
4. Open an issue with the `infrastructure` label

---

**Last Updated**: 2026-02-16
**Maintainer**: GitHub Actions Bot
