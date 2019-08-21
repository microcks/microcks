#!/bin/bash

echo "Creating docker network for nomad job ..."
docker network create --driver bridge nomad_main

echo "Replacing mount paths with current path"
export pwd=`pwd`
export tild=~
perl -i.bak -pe 's|/Users/lbroudou/Development/github/microcks/install/nomad|${pwd}|' microcks.nomad
perl -i.bak -pe 's|/Users/lbroudou/|${tild}|' microcks.nomad
