#!/usr/bin/env bash
# Scripted walk-through of the Keycloak demo, driven through the web UI BFF (http://localhost:13000).
here="$(cd "$(dirname "$0")" && pwd)"; UI=http://localhost:13000
H=(-H "Origin: $UI" -H "content-type: application/json")
step() { printf '\n\033[1m== %s\033[0m\n' "$1"; }
as() { local u=$1; shift; curl -s -b /tmp/jar-$u "${H[@]}" "$@"; }
code() { local u=$1; shift; curl -s -o /dev/null -w '%{http_code}' -b /tmp/jar-$u "${H[@]}" "$@"; }

step "1. Before any sign-in, the user list is empty and the API is protected"
echo "anonymous -> $(curl -s -o /dev/null -w '%{http_code}' $UI/api/admin/users)"

step "2. alice (member of the Keycloak group microcks-admin) signs in: her account is created"
"$here/login.sh" alice >/dev/null
as alice $UI/api/auth/session; echo
as alice $UI/api/admin/users | python3 -c 'import sys,json; [print(" ", u["id"], u["roles"], "active" if not u["disabled"] else "disabled") for u in json.load(sys.stdin)]'

step "3. alice creates group payments, adds bob as manager and invites dave by email (never signed in)"
as alice -X POST $UI/api/admin/groups -d '{"name":"payments","description":"Payments APIs"}' >/dev/null
as alice -X POST $UI/api/admin/groups/payments/members -d '{"who":"bob","role":"manager"}' >/dev/null
as alice -X POST $UI/api/admin/groups/payments/members -d '{"who":"dave@example.com","role":"user"}' >/dev/null
as alice $UI/api/admin/users | python3 -c 'import sys,json; [print(" ", u["id"], "pending" if u["pending"] else "known", [m["group"]+":"+m["role"] for m in u["memberships"]]) for u in json.load(sys.stdin)]'

step "4. bob, carol, dave sign in; dave's pending membership is claimed through his verified email"
for u in bob carol dave; do "$here/login.sh" $u >/dev/null; done
as alice $UI/api/admin/users | python3 -c 'import sys,json; [print("  %-6s roles=%-10s groups=%s" % (u["id"], u["roles"], [m["group"]+":"+m["role"] for m in u["memberships"]])) for u in json.load(sys.stdin)]'

step "5. Role enforcement: manager bob can list users but not disable one"
echo "bob  list users    -> $(code bob $UI/api/admin/users)"
carol=$(as alice $UI/api/admin/users | python3 -c 'import sys,json; print([u["userId"] for u in json.load(sys.stdin) if u["id"]=="carol"][0])')
echo "bob  disable carol -> $(code bob -X PUT $UI/api/admin/users/$carol -d '{"disabled":true}')"
echo "carol list users   -> $(code carol $UI/api/admin/users)"

step "6. alice disables carol: her already-issued session stops working at once, and she cannot sign in again"
echo "alice disable carol -> $(code alice -X PUT $UI/api/admin/users/$carol -d '{"disabled":true}')"
echo "carol (old session) calls the API -> $(code carol $UI/api/admin/groups)"
"$here/login.sh" carol >/dev/null 2>&1; echo "carol signs in again (backend answers 403) -> session:"; as carol $UI/api/auth/session; echo

step "7. alice enables carol again, then cannot disable herself"
echo "enable carol -> $(code alice -X PUT $UI/api/admin/users/$carol -d '{"disabled":false}')"
me=$(as alice $UI/api/admin/users | python3 -c 'import sys,json; print([u["userId"] for u in json.load(sys.stdin) if u["id"]=="alice"][0])')
echo "alice disables alice -> $(code alice -X PUT $UI/api/admin/users/$me -d '{"disabled":true}')"
