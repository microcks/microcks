From Microcks `1.7.1`, we changed the purpose of `run-microcks.sh` script.

Before `1.7.1`, this file was about adapting compose templates to root or rootless usages.

However with recent versions of podman for MacOS (and probably M1 architecture as well),
we faced more and more issues for getting podman run on MacOS (see https://github.com/microcks/microcks/issues/568
for details).

As a consequence, we now shifted to rootless mode only (safer) and change `run-microcks.sh` to
manage compose files and command lines tweaking depending on your OS.

### Archived run-microcks.sh

The old content of `run-microcks.sh` is put below for archive concern:

```sh
#!/bin/bash

mkdir -p microcks-data || exit 1

# The chosen podman-compose template depends on who is running this script
if [ "$UID" -eq 0 ]; then
  # We are root !
  template="microcks-template.yml"
  cmd_prefix="sudo"
else
  echo "Running rootless containers..."
  template="microcks-template-rootless.yml"
  cmd_prefix=""
fi

# Find the host ip address on linux systems
host_ip="$(ip -o route get to 8.8.8.8 2>/dev/null | sed -n 's/.*src \([0-9.]\+\).*/\1/p')"
if [ -z "$host_ip" ]; then
  # Fallback method
  iface="$(awk -F "\t" '$2 == "00000000" { print $1 }' /proc/net/route 2>/dev/null)"
  host_ip="$(ifconfig "$iface" 2>/dev/null |awk '$1 == "inet" { print $2 }')"
fi

# Generate a podman-compose file from the supplied template
echo "Discovered host IP address: ${host_ip:-none}" 
sed "s/__HOST__/$host_ip/" "$template" > microcks.yml || exit 1

echo
echo "Starting Microcks using podman-compose ..."
echo "------------------------------------------"
echo "Stop it with: $cmd_prefix podman-compose -f microcks.yml stop"
echo "Re-launch it with: $cmd_prefix podman-compose -f microcks.yml start"
echo "Clean everything with: $cmd_prefix podman-compose -f microcks.yml down"
echo "------------------------------------------"
echo "Go to https://localhost:8080 - first login with admin/microcks123"
echo "Having issues? Check you have changed microcks.yml to your platform"
echo

podman-compose -f microcks.yml up -d
```