#!/bin/bash

#
# Copyright The Microcks Authors.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#  http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

mkdir -p microcks-data || exit 1

# Machine name on macos.
machine_name="podman-machine-default"

# Assuming default template, no addon and no command prefix.
template="";
addon="";
cmd_prefix="";

# Parse argument to adjust template and addon.
if [ -n "$1" ]; then
  if [ "$1" == "dev" ]; then
    template="-devmode"
  elif [ "$1" == "async" ]; then
    addon="-f microcks-template-async-addon.yml"
  fi
fi

# Generate a podman-compose file from the supplied template
cp ./microcks-template"$template".yml ./microcks.yml || exit 1;

if [ "$(uname)" == "Darwin" ]; then
  # Change permission on microcks-data
  chmod 777 ./microcks-data

  echo
  echo "On macos, need to get the userid and groupid from postman machine."
  echo "Assuming this machine is named 'podman-machine-default'. Change name in script otherwise."
  idStr="$(podman machine ssh $machine_name id)" || exit 1

  # The above command should give something like:
  #idStr="uid=501(core) gid=1000(core) groups=1000(core),4(adm),10(wheel),16(sudo),190(systemd-journal) context=unconfined_u:unconfined_r:unconfined_t:s0-s0:c0.c1023"

  # Parse uid, gid and sed in enable user directive on mongo container.
  machine_uid=$(echo $idStr | awk '{split($1,element,"="); split(element[2],part,"("); print part[1]}')
  machine_gid=$(echo $idStr | awk '{split($2,element,"="); split(element[2],part,"("); print part[1]}')
  sed -i '' 's=#user: \"501:1000\"=user: \"'"$machine_uid"':'"$machine_gid"'\"=g' ./microcks.yml || exit 1

  cmd_prefix="--podman-run-args='--userns=keep-id:uid=$machine_uid,gid=$machine_gid'"
fi

echo
echo "Starting Microcks using podman-compose ..."
echo "------------------------------------------"
echo "Stop it with: podman-compose -f microcks.yml $addon $cmd_prefix stop"
echo "Re-launch it with: podman-compose -f microcks.yml $addon $cmd_prefix start"
echo "Clean everything with: podman-compose -f microcks.yml $addon $cmd_prefix down"
echo "------------------------------------------"
echo "Go to https://localhost:8080 - first login with admin/microcks123"
echo "Having issues? Check you have changed microcks.yml to your platform"
echo

podman-compose -f microcks.yml $addon $cmd_prefix up -d
