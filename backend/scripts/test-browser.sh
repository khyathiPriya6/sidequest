#!/usr/bin/env bash
# Run browser tests against a disposable PostgreSQL database and API.
# On failure, report the failing stage and show logs from the disposable instance.
set -euo pipefail
cd "$(dirname "$0")/.."
: "${JAVA_HOME:?Set JAVA_HOME to Java 21}"
PG_BIN="${PG_BIN:-/Library/PostgreSQL/18/bin}"
test_root=$(mktemp -d /private/tmp/sidequest-browser.XXXXXX)
stage="environment setup"
api_pid=''

finish() {
  local status="$1"
  trap - EXIT
  if (( status != 0 )); then
    printf '\nBrowser test harness failed (exit %s) during: %s\n' "$status" "$stage" >&2
    printf 'Diagnostic logs: %s\n' "$test_root" >&2
    local log
    for log in init.log postgres.log api.log; do
      if [[ -s "$test_root/$log" ]]; then
        printf '\n--- %s (last 80 lines) ---\n' "$log" >&2
        tail -n 80 "$test_root/$log" >&2
      fi
    done
  fi
  if [[ -n "$api_pid" ]]; then
    kill "$api_pid" 2>/dev/null || true
    wait "$api_pid" 2>/dev/null || true
  fi
  if [[ -d "$test_root/data" && -x "$PG_BIN/pg_ctl" ]]; then
    "$PG_BIN/pg_ctl" -D "$test_root/data" -m fast -w stop >/dev/null 2>&1 || true
  fi
  exit "$status"
}
trap 'finish $?' EXIT

for executable in initdb pg_ctl createdb psql; do
  if [[ ! -x "$PG_BIN/$executable" ]]; then
    printf 'Missing PostgreSQL executable: %s\n' "$PG_BIN/$executable" >&2
    exit 1
  fi
done
if [[ ! -x "$JAVA_HOME/bin/java" ]]; then
  printf 'JAVA_HOME does not contain an executable Java runtime: %s\n' "$JAVA_HOME" >&2
  exit 1
fi
for executable in python3 mvn npm; do
  if ! command -v "$executable" >/dev/null 2>&1; then
    printf 'Required executable not found in PATH: %s\n' "$executable" >&2
    exit 1
  fi
done

stage="allocating isolated ports"
read -r test_port api_port < <(python3 - <<'PY'
import socket
sockets = [socket.socket(), socket.socket()]
for sock in sockets:
    sock.bind(('127.0.0.1', 0))
print(*(sock.getsockname()[1] for sock in sockets))
for sock in sockets:
    sock.close()
PY
)
if [[ -z "${test_port:-}" || -z "${api_port:-}" ]]; then
  echo 'Could not allocate isolated PostgreSQL/API ports.' >&2
  exit 1
fi

printf 'Browser logs and results: %s\n' "$test_root"
stage="initializing isolated PostgreSQL"
printf 'Browser setup: %s\n' "$stage"
"$PG_BIN/initdb" -D "$test_root/data" -U sidequest_test --auth=trust --no-locale --encoding=UTF8 >"$test_root/init.log" 2>&1

stage="starting isolated PostgreSQL"
printf 'Browser setup: %s\n' "$stage"
"$PG_BIN/pg_ctl" -D "$test_root/data" -l "$test_root/postgres.log" -o "-h 127.0.0.1 -p $test_port -k $test_root" -w start

stage="creating isolated browser database"
printf 'Browser setup: %s\n' "$stage"
"$PG_BIN/createdb" -h 127.0.0.1 -p "$test_port" -U sidequest_test sidequest_browser_test

stage="packaging backend"
printf 'Browser setup: %s\n' "$stage"
mvn -q -DskipTests package

stage="starting isolated API"
printf 'Browser setup: %s\n' "$stage"
"$JAVA_HOME/bin/java" -jar target/sidequest-api-0.1.0-SNAPSHOT.jar --spring.profiles.active=local --spring.config.import= \
  --server.address=127.0.0.1 --server.port="$api_port" \
  --spring.datasource.url="jdbc:postgresql://127.0.0.1:$test_port/sidequest_browser_test" \
  --spring.datasource.username=sidequest_test --spring.datasource.password= >"$test_root/api.log" 2>&1 &
api_pid=$!

stage="waiting for isolated API health"
printf 'Browser setup: %s\n' "$stage"
python3 - "$api_port" <<'PY'
import sys, time, urllib.request
for _ in range(60):
    try:
        with urllib.request.urlopen(f'http://127.0.0.1:{sys.argv[1]}/api/v1/health', timeout=1) as response:
            if response.status == 200:
                break
    except OSError:
        pass
    time.sleep(0.5)
else:
    raise SystemExit('Isolated API did not become ready.')
PY

stage="running Playwright browser suite"
printf 'Browser setup: %s\n' "$stage"
cd ../ui
PG_E2E_URL="postgresql://sidequest_test@127.0.0.1:$test_port/sidequest_browser_test" \
PG_E2E_BIN="$PG_BIN" \
API_ORIGIN="http://127.0.0.1:$api_port" \
AUTH_E2E=1 \
PLAYWRIGHT_CHANNEL="${PLAYWRIGHT_CHANNEL:-chrome}" \
npm run test:e2e -- --workers=1 --output="$test_root/results" "$@"
