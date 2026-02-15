#!/bin/bash

# admin-reset-tasks.sh
# Administrative utility to reset tasks stuck in 'assigned' status back to 'ready'
#
# Usage:
#   ./tools/admin-reset-tasks.sh              # Interactive mode: shows stuck tasks and confirms
#   ./tools/admin-reset-tasks.sh --auto       # Auto mode: resets all stuck tasks without confirmation
#   ./tools/admin-reset-tasks.sh task-001     # Reset specific task by ID
#   ./tools/admin-reset-tasks.sh --help       # Show help

set -e

REGISTRY="TASK_REGISTRY.json"

# Color codes for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Show help
show_help() {
    echo "Admin Task Reset Utility"
    echo ""
    echo "Usage:"
    echo "  $0              Interactive mode: shows stuck tasks and confirms"
    echo "  $0 --auto       Auto mode: resets all stuck tasks without confirmation"
    echo "  $0 TASK_ID      Reset specific task by ID"
    echo "  $0 --help       Show this help message"
    echo ""
    echo "Description:"
    echo "  Resets tasks stuck in 'assigned' status back to 'ready' status."
    echo "  This is useful when workflow dispatch fails but tasks are marked as assigned."
    echo ""
    echo "Examples:"
    echo "  $0                    # Interactive mode"
    echo "  $0 --auto             # Reset all stuck tasks automatically"
    echo "  $0 data-001           # Reset task 'data-001'"
    echo "  $0 data-001 data-002  # Reset multiple specific tasks"
    exit 0
}

# Check if registry exists
if [ ! -f "$REGISTRY" ]; then
    echo -e "${RED}Error: TASK_REGISTRY.json not found${NC}"
    echo "Please run this script from the repository root directory"
    exit 1
fi

# Check if jq is installed
if ! command -v jq &> /dev/null; then
    echo -e "${RED}Error: jq is not installed${NC}"
    echo "Please install jq: https://stedolan.github.io/jq/download/"
    exit 1
fi

# Parse arguments
AUTO_MODE=false
SPECIFIC_TASKS=()

if [ $# -eq 0 ]; then
    # Interactive mode (default)
    AUTO_MODE=false
else
    for arg in "$@"; do
        case "$arg" in
            --help|-h)
                show_help
                ;;
            --auto)
                AUTO_MODE=true
                ;;
            *)
                SPECIFIC_TASKS+=("$arg")
                ;;
        esac
    done
fi

# Get assigned tasks
ASSIGNED_TASKS=$(jq -c '[.tasks[] | select(.status == "assigned")]' "$REGISTRY")
ASSIGNED_COUNT=$(echo "$ASSIGNED_TASKS" | jq 'length')

if [ "$ASSIGNED_COUNT" -eq 0 ]; then
    echo -e "${GREEN}✅ No tasks are stuck in 'assigned' status${NC}"
    exit 0
fi

echo -e "${BLUE}Found $ASSIGNED_COUNT task(s) in 'assigned' status:${NC}"
echo ""
echo "$ASSIGNED_TASKS" | jq -r '.[] | "  • \(.id) - \(.name) (assigned at: \(.assignedAt // "unknown"))"'
echo ""

# Determine which tasks to reset
TASKS_TO_RESET=""

if [ ${#SPECIFIC_TASKS[@]} -gt 0 ]; then
    # Reset specific tasks
    echo -e "${YELLOW}Resetting specific task(s): ${SPECIFIC_TASKS[*]}${NC}"
    
    # Build JSON array of task IDs to reset
    TASK_IDS_JSON=$(printf '%s\n' "${SPECIFIC_TASKS[@]}" | jq -R . | jq -s .)
    
    # Verify all specified tasks exist and are in assigned status
    for task_id in "${SPECIFIC_TASKS[@]}"; do
        if ! echo "$ASSIGNED_TASKS" | jq -e --arg id "$task_id" '.[] | select(.id == $id)' > /dev/null; then
            echo -e "${RED}Error: Task '$task_id' is not in 'assigned' status${NC}"
            exit 1
        fi
    done
    
    TASKS_TO_RESET="$TASK_IDS_JSON"
else
    # Reset all assigned tasks
    if [ "$AUTO_MODE" = false ]; then
        echo -e "${YELLOW}Do you want to reset all these tasks to 'ready' status? (yes/no)${NC}"
        read -r response
        if [[ ! "$response" =~ ^[Yy][Ee][Ss]$ ]] && [[ ! "$response" =~ ^[Yy]$ ]]; then
            echo "Cancelled."
            exit 0
        fi
    fi
    
    TASKS_TO_RESET=$(echo "$ASSIGNED_TASKS" | jq '[.[].id]')
fi

RESET_COUNT=$(echo "$TASKS_TO_RESET" | jq 'length')
echo -e "${BLUE}Resetting $RESET_COUNT task(s)...${NC}"

# Create backup
BACKUP_FILE="${REGISTRY}.backup-$(date +%Y%m%d-%H%M%S)"
cp "$REGISTRY" "$BACKUP_FILE"
echo -e "${GREEN}✅ Created backup: $BACKUP_FILE${NC}"

# Reset tasks
TMP_REGISTRY=$(mktemp)
jq --argjson task_ids "$TASKS_TO_RESET" \
   '.tasks |= map(
     if IN($task_ids[]; .id) then
       .status = "ready" | del(.assignedAt)
     else
       .
     end
   )' "$REGISTRY" > "$TMP_REGISTRY"

mv "$TMP_REGISTRY" "$REGISTRY"

echo -e "${GREEN}✅ Successfully reset $RESET_COUNT task(s) to 'ready' status${NC}"
echo ""
echo -e "${BLUE}Next steps:${NC}"
echo "  1. Review the changes: git diff TASK_REGISTRY.json"
echo "  2. Commit the changes: git add TASK_REGISTRY.json && git commit -m 'chore: reset stuck tasks'"
echo "  3. Push the changes: git push origin master"
echo ""
echo "To restore from backup if needed:"
echo "  cp $BACKUP_FILE $REGISTRY"
