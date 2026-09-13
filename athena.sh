#!/usr/bin/env bash
# Launch backend (Spring Boot, :7332) + frontend (Vite, :7331) together.
# Ctrl+C (or any exit) stops both. Safe to re-run even if a previous
# instance was left running or killed uncleanly.
set -uo pipefail

cd "$(dirname "$0")"

BACKEND_PORT=7332
FRONTEND_PORT=7331
LOCK_FILE=".athena.pid"

# If a previous athena.sh is still running, stop it and wait for its own
# script process to fully exit before we touch anything. Two athena.sh
# instances racing on the same ports is what made consecutive runs flaky:
# instance B's free_port() would kill instance A's mvn/vite, which made
# instance A's watchdog loop notice and run ITS OWN cleanup — including a
# free_port sweep — concurrently with B trying to start up, so B's
# freshly-spawned servers got killed too. Waiting for A to fully exit
# first makes the handoff sequential instead of a race.
if [[ -f "$LOCK_FILE" ]]; then
    prev_pid=$(cat "$LOCK_FILE" 2>/dev/null)
    if [[ -n "$prev_pid" ]] && kill -0 "$prev_pid" 2>/dev/null; then
        echo "Previous athena.sh instance (pid $prev_pid) is still running — stopping it..."
        kill -TERM "$prev_pid" 2>/dev/null
        waited=0
        while kill -0 "$prev_pid" 2>/dev/null && [[ $waited -lt 20 ]]; do
            sleep 0.5
            waited=$((waited + 1))
        done
        if kill -0 "$prev_pid" 2>/dev/null; then
            echo "Previous instance (pid $prev_pid) didn't exit in time — force killing..."
            kill -KILL "$prev_pid" 2>/dev/null
        fi
    fi
fi
echo $$ > "$LOCK_FILE"

port_pids() {
    local port=$1
    if command -v lsof >/dev/null 2>&1; then
        lsof -ti "tcp:$port" 2>/dev/null
    elif command -v fuser >/dev/null 2>&1; then
        fuser -n tcp "$port" 2>/dev/null
    elif command -v ss >/dev/null 2>&1; then
        ss -tlnp "sport = :$port" 2>/dev/null | grep -oP '(?<=pid=)\d+'
    fi
}

# Kill a PID and every process descended from it (mvn's java child,
# npm's vite child, etc), since none of them may share a process group.
kill_tree() {
    local sig=$1 pid=$2 child
    for child in $(pgrep -P "$pid" 2>/dev/null); do
        kill_tree "$sig" "$child"
    done
    kill "-$sig" "$pid" 2>/dev/null
}

free_port() {
    local port=$1
    local pids
    pids=$(port_pids "$port")
    [[ -z "$pids" ]] && return

    echo "Port $port is in use (pid(s): $pids) — stopping previous instance..."
    kill -TERM $pids 2>/dev/null

    # Poll (rather than a single fixed sleep) until the port is actually
    # free — a previous mvn/vite instance can take longer than a second to
    # release it, which is what made consecutive runs flaky.
    local waited=0
    while [[ $waited -lt 10 ]]; do
        pids=$(port_pids "$port")
        [[ -z "$pids" ]] && return
        sleep 0.5
        waited=$((waited + 1))
    done

    echo "Port $port still held by pid(s): $pids — force killing..."
    kill -KILL $pids 2>/dev/null
    # Give the OS a moment to actually release the socket after SIGKILL.
    waited=0
    while [[ $waited -lt 10 ]]; do
        [[ -z "$(port_pids "$port")" ]] && return
        sleep 0.5
        waited=$((waited + 1))
    done
}

free_port "$BACKEND_PORT"
free_port "$FRONTEND_PORT"

pids=()
stopping=false

cleanup() {
    $stopping && return
    stopping=true
    trap - EXIT INT TERM HUP
    echo ""
    echo "Stopping..."
    for pid in "${pids[@]}"; do
        kill_tree TERM "$pid"
    done
    sleep 1
    for pid in "${pids[@]}"; do
        kill_tree KILL "$pid"
    done
    # Belt and suspenders: whatever ended up bound to our ports, gone too —
    # covers a child that got reparented away from its parent.
    free_port "$BACKEND_PORT"
    free_port "$FRONTEND_PORT"
    # Only remove the lock file if it's still ours — a newer instance may
    # have already claimed it after force-killing us.
    [[ "$(cat "$LOCK_FILE" 2>/dev/null)" == "$$" ]] && rm -f "$LOCK_FILE"
    exit 0
}
trap cleanup EXIT INT TERM HUP

(cd athena-app && mvn -q spring-boot:run) &
pids+=($!)

(cd frontend && npm run dev) &
pids+=($!)

port_up() {
    local port=$1
    if command -v nc >/dev/null 2>&1; then
        nc -z localhost "$port" >/dev/null 2>&1
    else
        [[ -n "$(port_pids "$port")" ]]
    fi
}

open_browser_when_ready() {
    while :; do
        for pid in "${pids[@]}"; do
            kill -0 "$pid" 2>/dev/null || return
        done
        if port_up "$BACKEND_PORT" && port_up "$FRONTEND_PORT"; then
            echo "Both servers ready — opening http://localhost:$FRONTEND_PORT"
            if command -v open >/dev/null 2>&1; then
                open "http://localhost:$FRONTEND_PORT"
            elif command -v xdg-open >/dev/null 2>&1; then
                xdg-open "http://localhost:$FRONTEND_PORT"
            fi
            return
        fi
        sleep 0.5
    done
}
# Not added to pids[]: it's expected to exit on its own once the browser
# opens, and the main loop below treats any pid in that array exiting as a
# reason to tear everything down.
open_browser_when_ready &

# Portable stand-in for `wait -n` (not available on bash 3.2 / macOS default
# bash): poll until either child has exited, then tear both down. A plain
# foreground `sleep` (not backgrounded) is interrupted immediately by a
# trapped signal, so Ctrl+C never waits out this loop's tick.
while :; do
    for pid in "${pids[@]}"; do
        kill -0 "$pid" 2>/dev/null || cleanup
    done
    sleep 1
done
