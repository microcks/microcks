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

root_dir=$(pwd)

# Need 2 arguments: first is version we just release, second is container tag for next iteration.
if [[ $# -eq 2 ]]; then
  # Update container image version in docker-compose, podman-compose and helm chart files.
  if [ "$(uname)" == "Darwin" ]; then
    sed -i '' 's=quay.io/microcks/microcks:'"$1"'=quay.io/microcks/microcks:'"$2"'=g' install/docker-compose/docker-compose*.yml
    sed -i '' 's=quay.io/microcks/microcks-async-minion:'"$1"'=quay.io/microcks/microcks-async-minion:'"$2"'=g' install/docker-compose/docker-compose*.yml
    sed -i '' 's=quay.io/microcks/microcks:'"$1"'=quay.io/microcks/microcks:'"$2"'=g' install/docker-compose/*-addon.yml
    sed -i '' 's=quay.io/microcks/microcks-async-minion:'"$1"'=quay.io/microcks/microcks-async-minion:'"$2"'=g' install/docker-compose/*-addon.yml
    #sed -i '' 's=quay.io/microcks/microcks:'"$1"'=quay.io/microcks/microcks:'"$2"'=g' install/kubernetes/microcks/values.yaml
    #sed -i '' 's=quay.io/microcks/microcks-async-minion:'"$1"'=quay.io/microcks/microcks-async-minion:'"$2"'=g' install/kubernetes/microcks/values.yaml
    sed -i '' 's=tag: '"$1"'=tag: '"$2"'=g' install/kubernetes/microcks/values.yaml
    sed -i '' 's=quay.io/microcks/microcks:'"$1"'=quay.io/microcks/microcks:'"$2"'=g' install/podman-compose/podman-compose*.yml
    sed -i '' 's=quay.io/microcks/microcks-async-minion:'"$1"'=quay.io/microcks/microcks-async-minion:'"$2"'=g' install/podman-compose/podman-compose*.yml
    sed -i '' 's=quay.io/microcks/microcks:'"$1"'=quay.io/microcks/microcks:'"$2"'=g' install/podman-compose/*-addon.yml
    sed -i '' 's=quay.io/microcks/microcks-async-minion:'"$1"'=quay.io/microcks/microcks-async-minion:'"$2"'=g' install/podman-compose/*-addon.yml
  elif [ "$(expr substr $(uname -s) 1 5)" == "Linux" ]; then
    sed -i 's=quay.io/microcks/microcks:'"$1"'=quay.io/microcks/microcks:'"$2"'=g' install/docker-compose/docker-compose.yml
    sed -i 's=quay.io/microcks/microcks-async-minion:'"$1"'=quay.io/microcks/microcks-async-minion:'"$2"'=g' install/docker-compose/docker-compose*.yml
    sed -i 's=quay.io/microcks/microcks:'"$1"'=quay.io/microcks/microcks:'"$2"'=g' install/docker-compose/*-addon.yml
    sed -i 's=quay.io/microcks/microcks-async-minion:'"$1"'=quay.io/microcks/microcks-async-minion:'"$2"'=g' install/docker-compose/*-addon.yml
    #sed -i 's=quay.io/microcks/microcks:'"$1"'=quay.io/microcks/microcks:'"$2"'=g' install/kubernetes/microcks/values.yaml
    #sed -i 's=quay.io/microcks/microcks-async-minion:'"$1"'=quay.io/microcks/microcks-async-minion:'"$2"'=g' install/kubernetes/microcks/values.yaml
    sed -i 's=tag: '"$1"'=tag: '"$2"'=g' install/kubernetes/microcks/values.yaml
    sed -i 's=quay.io/microcks/microcks:'"$1"'=quay.io/microcks/microcks:'"$2"'=g' install/podman-compose/podman-compose*.yml
    sed -i 's=quay.io/microcks/microcks-async-minion:'"$1"'=quay.io/microcks/microcks-async-minion:'"$2"'=g' install/podman-compose/podman-compose*.yml
    sed -i 's=quay.io/microcks/microcks:'"$1"'=quay.io/microcks/microcks:'"$2"'=g' install/podman-compose/*-addon.yml
    sed -i 's=quay.io/microcks/microcks-async-minion:'"$1"'=quay.io/microcks/microcks-async-minion:'"$2"'=g' install/podman-compose/*-addon.yml
  fi
else
  echo "prep-next-iteration.sh must be called with <version> <next-container-tag> as 1st argument. Example:"
  echo "$ ./prep-next-iteration.sh 1.7.1 nightly"
fi