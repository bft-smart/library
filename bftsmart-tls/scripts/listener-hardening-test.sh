#!/usr/bin/env bash
#
# Robustness test for non-voting members ("listeners"). Two scenarios:
#
#   1. Late join / catch-up: voters process operations BEFORE the listener starts;
#      the listener must catch up via state transfer and then keep replicating.
#   2. Leader change: with a listener present, the current leader is killed; after
#      the leader-change protocol the listener must keep replicating from the new leader.
#
# Provisions the listener (id 4) in the install config only (not the repo config).
#
# Prerequisite: ./gradlew :bftsmart-tls:installDist
# Usage:        bftsmart-tls/scripts/listener-hardening-test.sh
# Requires:     bash, java, timeout, pkill

set -u
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
INSTALL_DIR="$SCRIPT_DIR/../build/install/bftsmart-tls"
[ -d "$INSTALL_DIR" ] || { echo "Build first: ./gradlew :bftsmart-tls:installDist"; exit 1; }
cd "$INSTALL_DIR"
J='-Djava.security.properties=./config/java.security -Dlogback.configurationFile=./config/logback.xml'
cleanup() { pkill -9 -f "bftsmart.demo.counter" 2>/dev/null; }
trap cleanup EXIT

grep -q "^4 " config/hosts.config || printf "4 127.0.0.1 11040 11041\n" >> config/hosts.config
grep -q "system.servers.listeners" config/system.config || printf "\nsystem.servers.listeners = 4\n" >> config/system.config

client() { timeout 40 java $J -cp "lib/*" bftsmart.demo.counter.CounterClient "$1" 1 "$2" >/dev/null 2>&1; }
final_val() { grep "Current value" "$1" 2>/dev/null | tail -1 | sed 's/.*= //'; }
rc=0

# ---------- Scenario 1: late join + state-transfer catch-up ----------
echo "=== Scenario 1: late-joining listener ==="
L=$(mktemp -d); cleanup; sleep 1; rm -f config/currentView
declare -a P
for i in 0 1 2 3; do java $J -cp "lib/*" bftsmart.demo.counter.CounterServer $i > "$L/rep$i.log" 2>&1 & P[$i]=$!; done
for t in $(seq 1 40); do [ "$(grep -l 'Ready to process operations' "$L"/rep[0-3].log 2>/dev/null | wc -l)" -eq 4 ] && break; sleep 1; done
client 1001 5                                   # 5 ops before the listener exists
java $J -cp "lib/*" bftsmart.demo.counter.CounterServer 4 > "$L/rep4.log" 2>&1 & P[4]=$!
for t in $(seq 1 40); do grep -q "Ready to process operations" "$L/rep4.log" 2>/dev/null && break; sleep 1; done
client 1002 3                                   # 3 more after it joined
sleep 4
v=$(final_val "$L/rep0.log"); l=$(final_val "$L/rep4.log")
echo "voter=$v listener=$l (expected 8)"
[ "$v" = "8" ] && [ "$l" = "8" ] && echo "scenario 1: PASS" || { echo "scenario 1: FAIL"; rc=1; }
for i in 0 1 2 3 4; do kill -9 ${P[$i]} 2>/dev/null; done

# ---------- Scenario 2: leader change with a listener present ----------
echo "=== Scenario 2: leader change with listener ==="
L=$(mktemp -d); cleanup; sleep 1; rm -f config/currentView
declare -a Q
for i in 0 1 2 3 4; do java $J -cp "lib/*" bftsmart.demo.counter.CounterServer $i > "$L/rep$i.log" 2>&1 & Q[$i]=$!; done
for t in $(seq 1 50); do [ "$(grep -l 'Ready to process operations' "$L"/rep*.log 2>/dev/null | wc -l)" -eq 5 ] && break; sleep 1; done
client 1001 3                                   # counter -> 3
kill -9 ${Q[0]}                                 # kill the leader
echo "leader killed; waiting for leader change..."; sleep 12
client 1002 3                                   # counter -> 6 under the new leader
sleep 4
v=$(final_val "$L/rep1.log"); l=$(final_val "$L/rep4.log")
echo "surviving voter=$v listener=$l (expected 6)"
[ "$v" = "6" ] && [ "$l" = "6" ] && echo "scenario 2: PASS" || { echo "scenario 2: FAIL"; rc=1; }
for i in 1 2 3 4; do kill -9 ${Q[$i]} 2>/dev/null; done

echo "=============================="
[ "$rc" -eq 0 ] && echo "RESULT: PASS - listener is robust to late join (state transfer) and leader change." \
                || echo "RESULT: FAIL"
exit $rc
