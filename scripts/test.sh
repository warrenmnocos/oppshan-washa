#!/usr/bin/env bash
# Local-dev test runner. Runs the Maven build, then force-removes the Postgres Dev Services
# container(s) it spun up — on success, on failure, or on Ctrl-C — so a dev box never accumulates
# orphaned postgres:18 containers.
#
# The build already disables Dev Services reuse (%test.quarkus.datasource.devservices.reuse=false),
# so Testcontainers' reaper (Ryuk) tears the container down on a clean JVM exit either way. This trap
# is the belt-and-suspenders for a run that is killed before Ryuk fires. It is deliberately NOT wired
# into the Maven build or CI: a GitHub runner is discarded after each job, so teardown there is dead
# code (see .claude/CLAUDE.md — scope defensive cleanup to the environment that needs it).
#
# Usage:  scripts/test.sh                  # ./mvnw -B test
#         scripts/test.sh verify -Dtest=X  # any goals / args pass straight through
set -uo pipefail

pruneDevServices() {
    local ids
    ids=$(docker ps -aq --filter ancestor=postgres:18 2>/dev/null) || return 0
    if [ -n "$ids" ]; then
        echo "Pruning $(echo "$ids" | wc -l | tr -d ' ') Dev Services postgres:18 container(s)."
        docker rm -f $ids >/dev/null 2>&1 || true
    fi
}
trap pruneDevServices EXIT

[ "$#" -eq 0 ] && set -- test
./mvnw -B "$@"
