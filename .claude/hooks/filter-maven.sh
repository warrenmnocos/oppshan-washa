#!/bin/bash
# .claude/hooks/filter-maven.sh — rewrite Maven commands to surface only failures/errors
input=$(cat)
cmd=$(echo "$input" | jq -r '.tool_input.command')
if [[ "$cmd" =~ mvnw|native-image|mvn ]]; then
  filtered_cmd="${cmd} 2>&1 | grep -E '(BUILD (SUCCESS|FAILURE)|\[ERROR\]|Tests run:|FAILED|PASSED|Caused by:|at com\.oppshan\.washa)' | head -200"
  jq -n --arg fc "$filtered_cmd" \
    '{hookSpecificOutput: {hookEventName: "PreToolUse", permissionDecision: "allow", updatedInput: {command: $fc}}}'
else
  echo "{}"
fi