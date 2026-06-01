#!/usr/bin/env bash
#
# Manual test for non-voting members ("listeners"): a node that replicates state
# from forwarded, proof-carrying decisions WITHOUT taking part in consensus.
#
# It starts 4 voters (ids 0-3) plus 1 listener (id 4), runs a client that issues
# ordered increments against the voters, and checks that the listener's counter
# mirrors the voters' counter even though the listener never votes.
#
# The script provisions the listener purely in the install directory's config
# (it does NOT touch the repository config): it adds host id 4 to hosts.config
# and 'system.servers.listeners = 4' to system.config.
#
# Prerequisite: build the runnable distribution first, from the repo root:
#     ./gradlew :bftsmart-tls:installDist
#
# Usage:
#     bftsmart-tls/scripts/listener-replication-test.sh
#
# Requires: bash, java, and the `timeout` and `pkill` utilities.

set -u

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
INSTALL_DIR="$SCRIPT_DIR/../build/install/bftsmart-tls"
LOG_DIR="$(mktemp -d)"

if [ ! -d "$INSTALL_DIR" ]; then
  echo "ERROR: distribution not found at $INSTALL_DIR"
  echo "Build it first: ./gradlew :bftsmart-tls:installDist"
  exit 1
fi

cd "$INSTALL_DIR"
JOPTS='-Djava.security.properties=./config/java.security -Dlogback.configurationFile=./config/logback.xml'

cleanup() { pkill -9 -f "bftsmart.demo.counter" 2>/dev/null; }
trap cleanup EXIT

echo "Install dir: $INSTALL_DIR"
echo "Logs dir:    $LOG_DIR"

# Provision the listener (id 4) in the install config only.
grep -q "^4 " config/hosts.config || printf "4 127.0.0.1 11040 11041\n" >> config/hosts.config
grep -q "system.servers.listeners" config/system.config || printf "\nsystem.servers.listeners = 4\n" >> config/system.config

cleanup
sleep 1
rm -f config/currentView

# --- start 4 voters (0-3) + 1 listener (4) ---------------------------------
declare -a PID
for i in 0 1 2 3 4; do
  java $JOPTS -cp "lib/*" bftsmart.demo.counter.CounterServer "$i" > "$LOG_DIR/rep$i.log" 2>&1 &
  PID[$i]=$!
done

echo "Waiting for all 5 nodes (4 voters + 1 listener) to be ready..."
for t in $(seq 1 60); do
  n=$(grep -l "Ready to process operations" "$LOG_DIR"/rep*.log 2>/dev/null | wc -l)
  if [ "$n" -eq 5 ]; then echo "All 5 ready after ${t}s"; break; fi
  sleep 1
done
ready=$(grep -l "Ready to process operations" "$LOG_DIR"/rep*.log 2>/dev/null | wc -l)
if [ "$ready" -ne 5 ]; then echo "ERROR: only $ready/5 nodes became ready"; exit 1; fi
echo "Listener (4) started as listener? $(grep -c 'Starting as listener' "$LOG_DIR/rep4.log")"

# --- drive ordered operations against the voters ---------------------------
OPS=6
echo "--- client: $OPS increments ---"
timeout 40 java $JOPTS -cp "lib/*" bftsmart.demo.counter.CounterClient 1001 1 "$OPS" > "$LOG_DIR/cli.log" 2>&1
grep "returned value" "$LOG_DIR/cli.log" | tail -1
sleep 4

# --- verdict: the listener must have replicated the same values ------------
voter_final=$(grep "Current value" "$LOG_DIR/rep0.log" | tail -1)
listener_final=$(grep "Current value" "$LOG_DIR/rep4.log" | tail -1)
echo "voter0   final: $voter_final"
echo "listener final: $listener_final"

listener_count=$(grep -c "Counter was incremented" "$LOG_DIR/rep4.log")
if echo "$listener_final" | grep -q "Current value = $OPS" && [ "$listener_count" -eq "$OPS" ]; then
  echo "RESULT: PASS - the listener replicated all $OPS ordered operations without voting."
  exit 0
else
  echo "RESULT: FAIL - listener replicated $listener_count/$OPS operations."
  exit 1
fi
