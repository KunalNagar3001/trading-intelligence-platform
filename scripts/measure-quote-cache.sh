#!/usr/bin/env bash
# Measures the Redis quote cache. Needs a JWT in $TOKEN and the app running.
#   TOKEN=... ./scripts/measure-quote-cache.sh INFY.NS 20
# Run it once with the cache on (default) and once with
#   --app.market-data.quote-cache-ttl-seconds=0
# to compare latency and Yahoo calls with and without the cache.
SYMBOL="${1:-INFY.NS}"
N="${2:-20}"
BASE="${BASE:-http://localhost:8080}"
AUTH="Authorization: Bearer ${TOKEN:?set TOKEN to a JWT first}"

echo "Stats before:"; curl -s "$BASE/api/stocks/cache-stats" -H "$AUTH"; echo
for i in $(seq 1 "$N"); do
  t=$(curl -s -o /dev/null -w "%{time_total}" "$BASE/api/stocks/quote/$SYMBOL" -H "$AUTH")
  echo "call $i: ${t}s"
done
echo "Stats after:"; curl -s "$BASE/api/stocks/cache-stats" -H "$AUTH"; echo
