#!/bin/bash

# stop-all-workflows.sh
# Administrative utility to stop all running GitHub Actions workflows
#
# Usage:
#   ./tools/stop-all-workflows.sh              # Interactive mode: shows running workflows and confirms
#   ./tools/stop-all-workflows.sh --auto       # Auto mode: stops all workflows without confirmation
#   ./tools/stop-all-workflows.sh --exclude-current  # Exclude current workflow from being stopped
#   ./tools/stop-all-workflows.sh --help       # Show help

set -e

# Color codes for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Show help
show_help() {
    echo "Stop All Workflows Utility"
    echo ""
    echo "Usage:"
    echo "  $0                     Interactive mode: shows running workflows and confirms"
    echo "  $0 --auto              Auto mode: stops all workflows without confirmation"
    echo "  $0 --exclude-current   Exclude the current running workflow (useful when run from CI)"
    echo "  $0 --help              Show this help message"
    echo ""
    echo "Description:"
    echo "  Stops all currently running or queued GitHub Actions workflows."
    echo "  This is useful for emergency stops or when you need to cancel all active jobs."
    echo ""
    echo "Environment Variables:"
    echo "  GITHUB_TOKEN           GitHub API token (required)"
    echo "  GITHUB_REPOSITORY      Repository in format 'owner/repo' (required)"
    echo "  GITHUB_RUN_ID          Current workflow run ID (optional, for --exclude-current)"
    echo ""
    echo "Examples:"
    echo "  $0                         # Interactive mode"
    echo "  $0 --auto                  # Stop all workflows automatically"
    echo "  $0 --auto --exclude-current  # Stop all except current workflow"
    exit 0
}

# Check if jq is installed
if ! command -v jq &> /dev/null; then
    echo -e "${RED}Error: jq is not installed${NC}"
    echo "Please install jq: https://stedolan.github.io/jq/download/"
    exit 1
fi

# Check if required environment variables are set
if [ -z "$GITHUB_TOKEN" ]; then
    echo -e "${RED}Error: GITHUB_TOKEN environment variable is not set${NC}"
    echo "Please set GITHUB_TOKEN with a valid GitHub token"
    exit 1
fi

if [ -z "$GITHUB_REPOSITORY" ]; then
    echo -e "${RED}Error: GITHUB_REPOSITORY environment variable is not set${NC}"
    echo "Please set GITHUB_REPOSITORY in format 'owner/repo'"
    exit 1
fi

# Parse arguments
AUTO_MODE=false
EXCLUDE_CURRENT=false

for arg in "$@"; do
    case "$arg" in
        --help|-h)
            show_help
            ;;
        --auto)
            AUTO_MODE=true
            ;;
        --exclude-current)
            EXCLUDE_CURRENT=true
            ;;
        *)
            echo -e "${RED}Unknown argument: $arg${NC}"
            show_help
            ;;
    esac
done

# Get repository info
REPO_OWNER=$(echo "$GITHUB_REPOSITORY" | cut -d'/' -f1)
REPO_NAME=$(echo "$GITHUB_REPOSITORY" | cut -d'/' -f2)

echo -e "${BLUE}Repository: $REPO_OWNER/$REPO_NAME${NC}"
echo ""

# Get current workflow run ID if we're running in GitHub Actions
CURRENT_RUN_ID="${GITHUB_RUN_ID:-}"

if [ "$EXCLUDE_CURRENT" = true ] && [ -n "$CURRENT_RUN_ID" ]; then
    echo -e "${YELLOW}Will exclude current workflow run ID: $CURRENT_RUN_ID${NC}"
    echo ""
fi

# Get all running and queued workflows
echo -e "${BLUE}Fetching running and queued workflows...${NC}"
RUNNING_WORKFLOWS=$(curl -s \
    -H "Accept: application/vnd.github+json" \
    -H "Authorization: Bearer $GITHUB_TOKEN" \
    -H "X-GitHub-Api-Version: 2022-11-28" \
    "https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/actions/runs?status=in_progress&per_page=100" \
    | jq -c '[.workflow_runs[]]')

QUEUED_WORKFLOWS=$(curl -s \
    -H "Accept: application/vnd.github+json" \
    -H "Authorization: Bearer $GITHUB_TOKEN" \
    -H "X-GitHub-Api-Version: 2022-11-28" \
    "https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/actions/runs?status=queued&per_page=100" \
    | jq -c '[.workflow_runs[]]')

# Combine both lists
ALL_WORKFLOWS=$(echo "$RUNNING_WORKFLOWS $QUEUED_WORKFLOWS" | jq -s 'add')
TOTAL_COUNT=$(echo "$ALL_WORKFLOWS" | jq 'length')

if [ "$TOTAL_COUNT" -eq 0 ]; then
    echo -e "${GREEN}✅ No workflows are currently running or queued${NC}"
    exit 0
fi

# Filter out current workflow if requested
if [ "$EXCLUDE_CURRENT" = true ] && [ -n "$CURRENT_RUN_ID" ]; then
    WORKFLOWS_TO_STOP=$(echo "$ALL_WORKFLOWS" | jq --arg current "$CURRENT_RUN_ID" '[.[] | select(.id != ($current | tonumber))]')
else
    WORKFLOWS_TO_STOP="$ALL_WORKFLOWS"
fi

STOP_COUNT=$(echo "$WORKFLOWS_TO_STOP" | jq 'length')

if [ "$STOP_COUNT" -eq 0 ]; then
    echo -e "${GREEN}✅ No workflows to stop (only current workflow is running)${NC}"
    exit 0
fi

echo -e "${BLUE}Found $STOP_COUNT workflow(s) to stop:${NC}"
echo ""
echo "$WORKFLOWS_TO_STOP" | jq -r '.[] | "  • [\(.id)] \(.name) - \(.status) (\(.head_branch))"'
echo ""

# Confirm if not in auto mode
if [ "$AUTO_MODE" = false ]; then
    echo -e "${YELLOW}Do you want to stop all these workflows? (yes/no)${NC}"
    read -r response
    if [[ ! "$response" =~ ^[Yy][Ee][Ss]$ ]] && [[ ! "$response" =~ ^[Yy]$ ]]; then
        echo "Cancelled."
        exit 0
    fi
fi

echo -e "${BLUE}Stopping $STOP_COUNT workflow(s)...${NC}"
echo ""

# Stop each workflow
STOPPED_COUNT=0
FAILED_COUNT=0

while read -r workflow_id; do
    workflow_name=$(echo "$WORKFLOWS_TO_STOP" | jq -r --arg id "$workflow_id" '.[] | select(.id == ($id | tonumber)) | .name')
    
    echo -n "Stopping [$workflow_id] $workflow_name... "
    
    if curl -s -o /dev/null -w "%{http_code}" \
        -X POST \
        -H "Accept: application/vnd.github+json" \
        -H "Authorization: Bearer $GITHUB_TOKEN" \
        -H "X-GitHub-Api-Version: 2022-11-28" \
        "https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/actions/runs/$workflow_id/cancel" \
        | grep -q "^20"; then
        echo -e "${GREEN}✓${NC}"
        ((STOPPED_COUNT++))
    else
        echo -e "${RED}✗${NC}"
        ((FAILED_COUNT++))
    fi
done < <(echo "$WORKFLOWS_TO_STOP" | jq -r '.[].id')

echo ""
echo -e "${GREEN}✅ Successfully stopped $STOPPED_COUNT workflow(s)${NC}"

if [ "$FAILED_COUNT" -gt 0 ]; then
    echo -e "${YELLOW}⚠️  Failed to stop $FAILED_COUNT workflow(s)${NC}"
fi

echo ""
echo -e "${BLUE}Next steps:${NC}"
echo "  • Check workflow status: https://github.com/$GITHUB_REPOSITORY/actions"
echo "  • Or run: curl -s -H \"Authorization: Bearer \$GITHUB_TOKEN\" https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/actions/runs | jq '.workflow_runs[] | select(.status == \"in_progress\") | {id, name, status}'"
