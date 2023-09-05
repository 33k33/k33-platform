#!/usr/bin/env bash

#
#  Run acceptance tests
#

set -e

./gradlew installDist --parallel

docker compose up --build --abort-on-container-exit
# docker compose up --build

# without ESP
# docker compose -f docker-compose.no-esp.yaml up --build --abort-on-container-exit
# docker compose -f docker-compose.no-esp.yaml up --build