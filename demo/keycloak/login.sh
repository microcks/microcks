#!/usr/bin/env bash
# Signs a Keycloak user in through the web UI (password = username) and stores the session cookie in /tmp/jar-<user>.
u=$1; jar=/tmp/jar-$u; rm -f $jar
loc=$(curl -s -o /dev/null -w '%{redirect_url}' -c $jar -b $jar localhost:13000/api/auth/login)
loc=$(curl -s -o /dev/null -w '%{redirect_url}' -c $jar -b $jar "$loc")
page=$(curl -s -c $jar -b $jar "$loc")
if [ "${loc:0:4}" != http ]; then echo "no idp redirect: $loc"; exit 1; fi
# loc may be the Keycloak login page itself
action=$(echo "$page" | grep -o 'action="[^"]*"' | head -1 | sed 's/action="//;s/"$//;s/&amp;/\&/g')
back=$(curl -s -o /dev/null -w '%{redirect_url}' -c $jar -b $jar -d "username=$u&password=$u&credentialId=" "$action")
back=$(curl -s -o /dev/null -w '%{redirect_url}' -c $jar -b $jar "$back")   # backend callback -> UI callback?token=
curl -s -o /dev/null -w '%{http_code}\n' -c $jar -b $jar "$back"            # UI sets cookie
