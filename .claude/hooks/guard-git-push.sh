#!/bin/bash
# PreToolUse hook for Bash. Blocks force-pushes and pushes to main.
# The deny rules in .claude/settings.json catch the usual forms; this catches the
# rest ("git -C . push", "git -c x=y push", bare "git push" while on main).
# Exit 2 blocks the tool call in every permission mode, and stderr goes to Claude.

input=$(cat)
command=$(jq -r '.tool_input.command // empty' <<<"$input")
cwd=$(jq -r '.cwd // empty' <<<"$input")

block() {
  echo "Blocked by .claude/hooks/guard-git-push.sh: $1. Push your own branch and open a PR instead." >&2
  exit 2
}

# Check each part of a compound command on its own, so a commit message that
# says "main" does not block the push after it.
while IFS= read -r part; do
  # Only parts that run "git ... push".
  grep -Eq '(^|[^[:alnum:]_-])git([[:space:]]+[^[:space:]]+)*[[:space:]]+push([[:space:]]|$)' <<<"$part" || continue

  # Force flags: --force, --force-with-lease, -f, or a combined short flag like -uf.
  if grep -Eq '[[:space:]](--force[^[:space:]]*|-[[:alnum:]]*f[[:alnum:]]*)([[:space:]]|$)' <<<"$part"; then
    block "force-push is not allowed"
  fi

  # A refspec that starts with + is a force-push too.
  if grep -Eq '[[:space:]]\+[^[:space:]]+' <<<"$part"; then
    block "force-push (+refspec) is not allowed"
  fi

  # Pushing to main: "main", "x:main", "refs/heads/main", or "--delete main".
  if grep -Eq '(^|[[:space:]:/])main([[:space:]]|$)' <<<"$part"; then
    block "pushing to main is not allowed"
  fi

  # Bare "git push" or "git push origin" (no refspec) while main is checked out.
  branch=$(git -C "${cwd:-.}" branch --show-current 2>/dev/null)
  if [ "$branch" = "main" ] && ! grep -Eq 'push([[:space:]]+-[^[:space:]]+)*[[:space:]]+[^[:space:]-][^[:space:]]*[[:space:]]+[^[:space:]-]' <<<"$part"; then
    block "a push without a branch name while on main is not allowed"
  fi
done < <(sed -E 's/(&&|\|\||;|\|)/\n/g' <<<"$command")

exit 0
