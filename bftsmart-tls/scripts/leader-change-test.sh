#!/usr/bin/env bash
#
# Manual fault-tolerance test for the BFT-SMaRt TLS/Netty transport.
#
# It starts 4 counter replicas (n=4, f=1), runs a baseline client, KILLS the
# current leader (replica 0), waits for the leader-change protocol to elect a new
# leader, then runs a second client to verify the system keeps ordering requests
# with its state preserved. This exercises both the replica-to-replica control
# plane (LCMessage STOP/STOPDATA/SYNC over ServerCommunicationLayer) and the
# client-to-server data plane (Netty) through the networking SPI.
#
# Prerequisite: build the runnable distribution first, from the repo root:
#     ./gradlew :bftsmart-tls:installDist
#
# Usage:
#     bftsmart-tls/scripts/leader-change-test.sh
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

# Fresh start: stop leftovers and drop the cached view so the group is rebuilt.
cleanup
sleep 1
rm -f config/currentView

# --- start 4 replicas -------------------------------------------------------
for i in 0 1 2 3; do
  java $JOPTS -cp "lib/*" bftsmart.demo.counter.CounterServer "$i" > "$LOG_DIR/rep$i.log" 2>&1 &
done

echo "Waiting for all 4 replicas to be ready..."
for t in $(seq 1 60); do
  n=$(grep -l "Ready to process operations" "$LOG_DIR"/rep*.log 2>/dev/null | wc -l)
  if [ "$n" -eq 4 ]; then echo "All 4 replicas ready after ${t}s"; break; fi
  sleep 1
done
ready=$(grep -l "Ready to process operations" "$LOG_DIR"/rep*.log 2>/dev/null | wc -l)
if [ "$ready" -ne 4 ]; then echo "ERROR: only $ready/4 replicas became ready"; exit 1; fi

# --- baseline (leader == replica 0) ----------------------------------------
echo "--- baseline client 1001 (5 increments) ---"
timeout 30 java $JOPTS -cp "lib/*" bftsmart.demo.counter.CounterClient 1001 1 5 > "$LOG_DIR/cli_before.log" 2>&1
grep "returned value" "$LOG_DIR/cli_before.log" | tail -2

# --- kill the leader --------------------------------------------------------
echo "=== KILLING LEADER (replica 0) ==="
pkill -9 -f "CounterServer 0"
echo "Leader killed; waiting 15s for the leader-change protocol to settle..."
sleep 15

# --- after the kill: a new leader must serve requests -----------------------
echo "--- client 1002 AFTER kill (5 increments) ---"
timeout 45 java $JOPTS -cp "lib/*" bftsmart.demo.counter.CounterClient 1002 1 5 > "$LOG_DIR/cli_after.log" 2>&1
after_rc=$?
grep "returned value" "$LOG_DIR/cli_after.log"

echo "=== leader-change evidence on surviving replicas (1,2,3) ==="
grep -hiE "leader change|regency|new leader|Current leader|STOP" \
  "$LOG_DIR"/rep1.log "$LOG_DIR"/rep2.log "$LOG_DIR"/rep3.log 2>/dev/null | tail -15

# --- verdict ----------------------------------------------------------------
after_ok=$(grep -c "returned value" "$LOG_DIR/cli_after.log")
if [ "$after_rc" -eq 0 ] && [ "$after_ok" -eq 5 ]; then
  echo "RESULT: PASS - the system survived the leader kill and kept serving requests."
  exit 0
else
  echo "RESULT: FAIL - client after the kill returned $after_ok/5 replies (rc=$after_rc)."
  exit 1
fi
