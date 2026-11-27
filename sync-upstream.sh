#!/usr/bin/env bash
# Catches up the 2.0 branch with the 1.x commits (UPSTREAM) made after BASE, using git's rename detection:
# since the 2.0 branch starts with pure-rename commits, `git cherry-pick` follows moved files by itself.
#
# Usage: sync-upstream.sh <repo-clone> <report|apply> [upstream-ref] [branch] [base-commit]
#   report : dry run in a throwaway worktree, nothing is modified. Prints OK / SKIP / CONFLICT per commit.
#   apply  : cherry-picks onto <branch> (with `-x` trailer, original author kept, package io.github.microcks
#            rewritten to io.microcks in picked changes). Stops at the first conflict: resolve, `git commit`,
#            then re-run (already ported commits are detected through their "cherry picked from" trailer).
# env: REFS (e.g. "Refs #2443") and SIGNOFF (e.g. "Signed-off-by: Name <mail>") are added to the ported messages.
#      DROP_PREFIXES (default "webapp")  conflicts on files deleted from the 2.0 branch under these prefixes
#      are resolved by dropping them (the change has no target; commits left empty are reported as SKIP).
set -uo pipefail

REPO=${1:?usage: $0 <repo-clone> <report|apply> [upstream-ref] [branch] [base-commit]}
MODE=${2:?usage: $0 <repo-clone> <report|apply> [upstream-ref] [branch] [base-commit]}
UPSTREAM=${3:-origin/master}
BRANCH=${4:-v2}
BASE=${5:-1e8b6c60d786363d7c9c85f7985189802f350015}
DROP_PREFIXES=${DROP_PREFIXES:-"webapp"}

cd "$REPO"
git fetch -q origin 2>/dev/null || true
COMMITS=$(git log --no-merges --reverse --format=%H "$BASE..$UPSTREAM")
echo "$(echo "$COMMITS" | wc -l) upstream commits in $BASE..$UPSTREAM"

# Tries to pick $1 on the current worktree (changes left staged). Echoes OK|SKIP|CONFLICT.
try_pick() {
   local c=$1
   if git cherry-pick --no-commit -X find-renames=40% "$c" >/dev/null 2>&1; then
      git diff --cached --quiet && echo SKIP || echo OK
      return
   fi
   local f unresolved=0
   while IFS=$'\t' read -r st f; do
      [ -z "$f" ] && continue
      case "$st" in U*|*U|AA|DD) ;; *) continue ;; esac
      if [ "$st" = UA ] || [ "$st" = AU ]; then git add -- "$f"   # file added upstream, already at its 2.0 path
      elif [[ " $DROP_PREFIXES " == *" ${f%%/*} "* ]]; then git rm -q --cached -- "$f" 2>/dev/null; rm -f -- "$f"
      else unresolved=1; fi
   done < <(git status --porcelain | sed -E 's/^(..) /\1\t/')
   if [ $unresolved = 0 ]; then git diff --cached --quiet && echo SKIP || echo OK; else echo CONFLICT; fi
}

# New files can land at their 1.x path when git finds no rename target (e.g. new tests under a dropped prefix).
orphans() {
   local p
   for p in $DROP_PREFIXES; do git diff --cached --name-only --diff-filter=A -- "$p/"; done
}

fix_packages() {
   git diff --cached --name-only --diff-filter=AM | grep -E '\.(java|properties|xml|yml|yaml|json)$' | while read -r f; do
      sed -i 's/io\.github\.microcks/io.microcks/g; s#io/github/microcks#io/microcks#g' "$f" && git add -- "$f"
   done
}

# Message of a ported commit: original message, optional REFS line, cherry-pick line, then SIGNOFF (unless present).
pick_message() {
   local body; body=$(git log -1 --format=%B "$1" | sed -e :a -e '/^\n*$/{$d;N;ba' -e '}')
   printf '%s\n\n' "$body"
   [ -n "${REFS:-}" ] && printf '%s\n' "$REFS"
   printf '(cherry picked from commit %s)\n' "$1"
   if [ -n "${SIGNOFF:-}" ] && ! grep -qxF "$SIGNOFF" <<<"$body"; then printf '%s\n' "$SIGNOFF"; fi
}

short() { git log -1 --format='%h %ad %an: %s' --date=short "$1" | cut -c1-110; }

if [ "$MODE" = report ]; then
   WT=$(mktemp -d); git worktree add -q --detach "$WT" "$BRANCH"; trap 'cd "$REPO"; git worktree remove --force "$WT"' EXIT
   cd "$WT"; ok=0; skip=0; conf=0
   for c in $COMMITS; do
      r=$(try_pick "$c")
      [ "$r" = OK ] && [ -n "$(orphans)" ] && { r=ORPHAN; orph=$(orphans | tr '\n' ' '); }
      case $r in ORPHAN) conf=$((conf+1)); git reset -q --hard ;;
                 OK) ok=$((ok+1)); git -c user.name=tmp -c user.email=t@l commit -qm "tmp $c" ;;
                 SKIP) skip=$((skip+1)); git reset -q --hard ;;
                 CONFLICT) conf=$((conf+1)); files=$(git diff --name-only --diff-filter=U | head -5 | tr '\n' ' '); git reset -q --hard ;;
      esac
      printf '%-8s %s\n' "$r" "$(short "$c")"
      [ "$r" = CONFLICT ] && echo "           -> $files"
      [ "$r" = ORPHAN ] && echo "           -> new files left under a dropped prefix, to relocate by hand: $orph"
      [ "$r" = OK ] || [ "$r" = SKIP ] || true
      git cherry-pick --abort 2>/dev/null; git reset -q --hard 2>/dev/null; git clean -fdq
   done
   echo "summary: OK=$ok SKIP=$skip CONFLICT=$conf"
   exit 0
fi

git checkout -q "$BRANCH"
[ -z "$(git status --porcelain)" ] || { echo "working tree not clean" >&2; exit 1; }
for c in $COMMITS; do
   git log --format=%B "$BASE..$BRANCH" | grep -q "cherry picked from commit $c" && { echo "already ported  $(short "$c")"; continue; }
   r=$(try_pick "$c")
   case $r in
      SKIP) git reset -q --hard; echo "SKIP            $(short "$c")" ;;
      CONFLICT) echo "CONFLICT        $(short "$c")"; git status --short | grep -E '^(U.|.U|AA|DD)'
                echo "Resolve, 'git commit' (message with the '(cherry picked from commit $c)' line), then re-run."; exit 2 ;;
      OK) fix_packages
          if [ -n "$(orphans)" ]; then
             echo "ORPHAN          $(short "$c")"; orphans | sed 's/^/   /'
             echo "New files left under a dropped prefix: git mv them to their 2.0 module (fix package/imports), then"
             echo "'git commit' (message with '(cherry picked from commit $c)'), and re-run."; exit 2
          fi
          git commit -q --author="$(git log -1 --format='%an <%ae>' "$c")" -F <(pick_message "$c")
          echo "picked          $(short "$c")" ;;
   esac
done
echo "up to date with $UPSTREAM"
