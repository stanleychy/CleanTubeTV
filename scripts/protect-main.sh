#!/usr/bin/env bash
# Turns on the repository protection for CleanTube TV. Run it one time after the repository is public
# (GitHub Free does not allow rulesets on private repositories). You can run it again: it updates the rules.
#
# Usage: scripts/protect-main.sh [OWNER/REPO]
# Needs: gh, logged in as a repository admin.
#
# What it sets:
# 1. Ruleset "Protect main" on the default branch:
#    - Changes go in only through a pull request. Nobody can push to main directly.
#    - 1 approval from a code owner (.github/CODEOWNERS), after the last push. Stale approvals are dismissed.
#    - The CI check "build" must pass on the latest main.
#    - No force push, no deletion.
#    - Repository admins can merge a pull request without the approval (bypass mode "pull_request").
#      They cannot push directly.
# 2. Ruleset "Protect release tags": only repository admins can create, move or delete "v*" tags.
#    A "v*" tag starts the signed Release workflow.
# 3. Settings: read-only default token for workflows, approval for workflow runs from all outside
#    contributors, private vulnerability reporting, Dependabot alerts, delete branches after merge.
set -euo pipefail

repo="${1:-$(gh repo view --json nameWithOwner --jq .nameWithOwner)}"
[[ "$repo" =~ ^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$ ]] || { echo "Not a valid OWNER/REPO: $repo"; exit 1; }

visibility=$(gh api "repos/$repo" --jq .visibility)
if [[ "$visibility" != "public" ]]; then
  echo "$repo is $visibility. Make it public first (Settings > General > Danger Zone), then run this script again."
  exit 1
fi

# GitHub Actions app ID. The required check must come from GitHub Actions, not from any other app.
actions_app_id=15368
admin_role_id=5

# Creates the ruleset, or updates the ruleset with the same name.
apply_ruleset() {
  local name="$1" body="$2" id
  id=$(gh api "repos/$repo/rulesets" --jq ".[] | select(.name == \"$name\") | .id")
  if [[ -n "$id" ]]; then
    gh api -X PUT "repos/$repo/rulesets/$id" --input - <<<"$body" > /dev/null
    echo "Updated ruleset: $name"
  else
    gh api -X POST "repos/$repo/rulesets" --input - <<<"$body" > /dev/null
    echo "Created ruleset: $name"
  fi
}

apply_ruleset "Protect main" "$(cat <<EOF
{
  "name": "Protect main",
  "target": "branch",
  "enforcement": "active",
  "conditions": { "ref_name": { "include": ["~DEFAULT_BRANCH"], "exclude": [] } },
  "bypass_actors": [
    { "actor_id": $admin_role_id, "actor_type": "RepositoryRole", "bypass_mode": "pull_request" }
  ],
  "rules": [
    { "type": "deletion" },
    { "type": "non_fast_forward" },
    {
      "type": "pull_request",
      "parameters": {
        "required_approving_review_count": 1,
        "require_code_owner_review": true,
        "require_last_push_approval": true,
        "dismiss_stale_reviews_on_push": true,
        "required_review_thread_resolution": true,
        "allowed_merge_methods": ["squash", "rebase"]
      }
    },
    {
      "type": "required_status_checks",
      "parameters": {
        "strict_required_status_checks_policy": true,
        "required_status_checks": [ { "context": "build", "integration_id": $actions_app_id } ]
      }
    }
  ]
}
EOF
)"

apply_ruleset "Protect release tags" "$(cat <<EOF
{
  "name": "Protect release tags",
  "target": "tag",
  "enforcement": "active",
  "conditions": { "ref_name": { "include": ["refs/tags/v*"], "exclude": [] } },
  "bypass_actors": [
    { "actor_id": $admin_role_id, "actor_type": "RepositoryRole", "bypass_mode": "always" }
  ],
  "rules": [
    { "type": "creation" },
    { "type": "update" },
    { "type": "deletion" },
    { "type": "non_fast_forward" }
  ]
}
EOF
)"

gh api -X PUT "repos/$repo/actions/permissions/workflow" \
  -f default_workflow_permissions=read -F can_approve_pull_request_reviews=false > /dev/null
echo "Set: workflows get a read-only token by default"

gh api -X PUT "repos/$repo/actions/permissions/fork-pr-contributor-approval" \
  -f approval_policy=all_external_contributors > /dev/null
echo "Set: workflow runs from outside contributors need approval"

gh api -X PUT "repos/$repo/private-vulnerability-reporting" > /dev/null
echo "Set: private vulnerability reporting is on"

gh api -X PUT "repos/$repo/vulnerability-alerts" > /dev/null
echo "Set: Dependabot alerts are on"

gh api -X PATCH "repos/$repo" -F delete_branch_on_merge=true -F allow_merge_commit=false \
  -F allow_squash_merge=true -F allow_rebase_merge=true > /dev/null
echo "Set: squash and rebase merges only, delete branches after merge"

echo
echo "Done. Check the rules at https://github.com/$repo/settings/rules"
