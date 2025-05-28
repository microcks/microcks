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

# Update container image version in docker-compose, podman-compose and helm chart files.
if [[ $# -eq 1 ]]; then
  if [ "$(uname)" == "Darwin" ]; then
    sed -i '' 's=quay.io/microcks/microcks:nightly=quay.io/microcks/microcks:'"$1"'=g' install/docker-compose/docker-compose*.yml
    sed -i '' 's=quay.io/microcks/microcks-async-minion:nightly=quay.io/microcks/microcks-async-minion:'"$1"'=g' install/docker-compose/docker-compose*.yml
    sed -i '' 's=quay.io/microcks/microcks:nightly=quay.io/microcks/microcks:'"$1"'=g' install/docker-compose/*-addon.yml
    sed -i '' 's=quay.io/microcks/microcks-async-minion:nightly=quay.io/microcks/microcks-async-minion:'"$1"'=g' install/docker-compose/*-addon.yml
    #sed -i '' 's=quay.io/microcks/microcks:nightly=quay.io/microcks/microcks:'"$1"'=g' install/kubernetes/microcks/values.yaml
    #sed -i '' 's=quay.io/microcks/microcks-async-minion:nightly=quay.io/microcks/microcks-async-minion:'"$1"'=g' install/kubernetes/microcks/values.yaml
    sed -i '' 's=tag: nightly=tag: '"$1"'=g' install/kubernetes/microcks/values.yaml
    sed -i '' 's=quay.io/microcks/microcks:nightly=quay.io/microcks/microcks:'"$1"'=g' install/podman-compose/podman-compose*.yml
    sed -i '' 's=quay.io/microcks/microcks-async-minion:nightly=quay.io/microcks/microcks-async-minion:'"$1"'=g' install/podman-compose/podman-compose*.yml
    sed -i '' 's=quay.io/microcks/microcks:nightly=quay.io/microcks/microcks:'"$1"'=g' install/podman-compose/*-addon.yml
    sed -i '' 's=quay.io/microcks/microcks-async-minion:nightly=quay.io/microcks/microcks-async-minion:'"$1"'=g' install/podman-compose/*-addon.yml
  elif [ "$(expr substr $(uname -s) 1 5)" == "Linux" ]; then
    sed -i 's=quay.io/microcks/microcks:nightly=quay.io/microcks/microcks:'"$1"'=g' install/docker-compose/docker-compose*.yml
    sed -i 's=quay.io/microcks/microcks-async-minion:nightly=quay.io/microcks/microcks-async-minion:'"$1"'=g' install/docker-compose/docker-compose*.yml
    sed -i 's=quay.io/microcks/microcks:nightly=quay.io/microcks/microcks:'"$1"'=g' install/docker-compose/*-addon.yml
    sed -i 's=quay.io/microcks/microcks-async-minion:nightly=quay.io/microcks/microcks-async-minion:'"$1"'=g' install/docker-compose/*-addon.yml
    #sed -i 's=quay.io/microcks/microcks:nightly=quay.io/microcks/microcks:'"$1"'=g' install/kubernetes/microcks/values.yaml
    #sed -i 's=quay.io/microcks/microcks-async-minion:nightly=quay.io/microcks/microcks-async-minion:'"$1"'=g' install/kubernetes/microcks/values.yaml
    sed -i 's=tag: nightly=tag: '"$1"'=g' install/kubernetes/microcks/values.yaml
    sed -i 's=quay.io/microcks/microcks:nightly=quay.io/microcks/microcks:'"$1"'=g' install/podman-compose/podman-compose*.yml
    sed -i 's=quay.io/microcks/microcks-async-minion:nightly=quay.io/microcks/microcks-async-minion:'"$1"'=g' install/podman-compose/podman-compose*.yml
    sed -i 's=quay.io/microcks/microcks:nightly=quay.io/microcks/microcks:'"$1"'=g' install/podman-compose/*-addon.yml
    sed -i 's=quay.io/microcks/microcks-async-minion:nightly=quay.io/microcks/microcks-async-minion:'"$1"'=g' install/podman-compose/*-addon.yml
  fi
else
  echo "pre-release.sh must be called with <version> as 1st argument. Example:"
  echo "$ ./pre-release.sh 1.7.1"
fi