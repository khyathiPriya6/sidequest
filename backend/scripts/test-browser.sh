#!/usr/bin/env bash
# Run existing and remediation browser tests against an isolated PostgreSQL/API.
set -euo pipefail
cd "$(dirname "$0")/.."
: "${JAVA_HOME:?Set JAVA_HOME to Java 21}"
PG_BIN="${PG_BIN:-/Library/PostgreSQL/18/bin}"
test_root=$(mktemp -d /private/tmp/sidequest-browser.XXXXXX)
read -r test_port api_port < <(python3 - <<'PY'
import socket
sockets = [socket.socket(), socket.socket()]
for sock in sockets: sock.bind(('127.0.0.1', 0))
print(*(sock.getsockname()[1] for sock in sockets))
for sock in sockets: sock.close()
PY
)
api_pid=''
cleanup() {
  if [[ -n "$api_pid" ]]; then kill "$api_pid" 2>/dev/null || true; wait "$api_pid" 2>/dev/null || true; fi
  "$PG_BIN/pg_ctl" -D "$test_root/data" -m fast -w stop >/dev/null 2>&1 || true
}
trap cleanup EXIT
"$PG_BIN/initdb" -D "$test_root/data" -U sidequest_test --auth=trust --no-locale --encoding=UTF8 >"$test_root/init.log"
"$PG_BIN/pg_ctl" -D "$test_root/data" -l "$test_root/postgres.log" -o "-h 127.0.0.1 -p $test_port -k $test_root" -w start
"$PG_BIN/createdb" -h 127.0.0.1 -p "$test_port" -U sidequest_test sidequest_browser_test
mvn -q -DskipTests package
"$JAVA_HOME/bin/java" -jar target/sidequest-api-0.1.0-SNAPSHOT.jar --spring.profiles.active=local --spring.config.import= \
  --server.address=127.0.0.1 --server.port="$api_port" \
  --spring.datasource.url="jdbc:postgresql://127.0.0.1:$test_port/sidequest_browser_test" \
  --spring.datasource.username=sidequest_test --spring.datasource.password= >"$test_root/api.log" 2>&1 &
api_pid=$!
python3 - "$api_port" <<'PY'
import sys, time, urllib.request
for _ in range(60):
    try:
        with urllib.request.urlopen(f'http://127.0.0.1:{sys.argv[1]}/api/v1/health', timeout=1) as response:
            if response.status == 200: break
    except OSError: pass
    time.sleep(0.5)
else: raise SystemExit('Isolated API did not become ready; inspect the temporary API log.')
PY
echo "Browser logs and results: $test_root"
cd ../ui
API_ORIGIN="http://127.0.0.1:$api_port" AUTH_E2E=1 PLAYWRIGHT_CHANNEL="${PLAYWRIGHT_CHANNEL:-chrome}" \
  npm run test:e2e -- --workers=1 --output="$test_root/results" "$@"
