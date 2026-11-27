#!/usr/bin/env bash
# Creates its own cluster; never reads the developer database configuration.
set -euo pipefail
cd "$(dirname "$0")/.."
: "${JAVA_HOME:?Set JAVA_HOME to a Java 21 installation}"
PG_BIN="${PG_BIN:-/Library/PostgreSQL/18/bin}"
test_root=$(mktemp -d /private/tmp/sidequest-pg.XXXXXX)
test_port=$(python3 -c 'import socket; s=socket.socket(); s.bind(("127.0.0.1",0)); print(s.getsockname()[1]); s.close()')
cleanup() { "$PG_BIN/pg_ctl" -D "$test_root/data" -m fast -w stop >/dev/null 2>&1 || true; }
trap cleanup EXIT
"$PG_BIN/initdb" -D "$test_root/data" -U sidequest_test --auth=trust --no-locale --encoding=UTF8 >"$test_root/init.log"
"$PG_BIN/pg_ctl" -D "$test_root/data" -l "$test_root/server.log" -o "-h 127.0.0.1 -p $test_port -k $test_root" -w start
"$PG_BIN/createdb" -h 127.0.0.1 -p "$test_port" -U sidequest_test sidequest_isolated_test
echo "Isolated PostgreSQL logs: $test_root"
# Bound each cached Spring test context pool as the regression suite grows.
mvn -Ppostgres-it -Dsidequest.auth.throttle.ip-limit=10000 -Dspring.datasource.hikari.maximum-pool-size=4 -Dspring.datasource.hikari.minimum-idle=1 -Dsidequest.test.jdbc-url="jdbc:postgresql://127.0.0.1:$test_port/sidequest_isolated_test" verify "$@"
