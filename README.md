# Trading Intelligence Platform

![CI](https://github.com/KunalNagar3001/trading-intelligence-platform/actions/workflows/ci.yml/badge.svg)

A Spring Boot backend for Indian equities and commodity ETFs. It streams live prices through Kafka,
fires price alerts, keeps named watchlists, scores news sentiment, tracks portfolios with real-time P&L, and
pushes live prices to clients over WebSocket.

## Architecture

```
                 ┌─────────────────┐   quotes    ┌───────────────┐
 Yahoo Finance ─▶│ LivePriceSched. │────────────▶│ Redis         │◀── REST quote API (cache, TTL 10s)
                 └────────┬────────┘             └───────────────┘
                          │ PriceEvent (keyed by symbol, 3 partitions)
                          ▼
                 ┌─────────────────┐
                 │ Kafka           │  price-events        failures ─▶ price-events.DLT
                 └───┬─────────┬───┘
       group: alert  │         │ group: websocket
                     ▼         ▼
          PriceAlertConsumer   PriceWebSocketConsumer ─▶ WebSocket clients
          PriceHistoryConsumer ─▶ MongoDB price_history (group: price-history)
          (MySQL alerts ─▶ email)

 News API ─▶ NewsFetchScheduler ─▶ NewsAggregatorService (tag symbols, score sentiment, save to MongoDB)
                                          └─ strong sentiment only ─▶ Kafka news-events
                                                 ─▶ NewsProcessingConsumer ─▶ email holders

 MySQL: users, instruments, holdings, transactions, alerts,     MongoDB: news, price history, stock DNA
         watchlists, watchlist items
```

Alert and WebSocket consumers use separate consumer groups, so each receives every price event independently.
Malformed messages go straight to a dead-letter topic.

The price scheduler polls each distinct symbol once per cycle: every symbol that any user holds, has an active alert
on, or has on a watchlist. Prices are shared; who receives them is decided per user at delivery time.

## Run it

**Prerequisites:** Java 17, Docker.

Start the four dependencies (adjust to your own setup if you already run them):

```bash
docker run -d --name tip-mysql -p 3306:3306 -e MYSQL_ROOT_PASSWORD=changeme -e MYSQL_DATABASE=trading_platform mysql:8
docker run -d --name tip-mongo -p 27017:27017 mongo:7
docker run -d --name tip-redis -p 6379:6379 redis:7
docker run -d --name tip-kafka -p 9092:9092 apache/kafka:latest
```

**Required environment variables**

| Variable | Purpose |
|---|---|
| `DB_PASSWORD` | MySQL password (must match `MYSQL_ROOT_PASSWORD` above) |
| `NEWS_API_KEY` | API key for the news provider |

Strongly recommended: `JWT_SECRET`, at least 32 characters (for example `openssl rand -base64 48`). It signs login
tokens, and the app refuses to start if it is set but too short. If it is not set, the app generates a random key at
startup and logs a warning, which works locally but logs everyone out on every restart.

Optional: `DB_USERNAME` (default `root`), and `MAIL_USERNAME` / `MAIL_PASSWORD` / `MAIL_HOST` / `MAIL_PORT`
for alert emails. Without `MAIL_USERNAME`, emails are skipped with a warning.

**Start**

```bash
DB_PASSWORD=changeme NEWS_API_KEY=your-key ./mvnw spring-boot:run
```

On first start the app creates its tables and seeds a few NSE instruments (`src/main/resources/data.sql`).

**Tests**

```bash
./mvnw test
```

Unit tests need no running services.

## Dashboard (React)

A small single-page dashboard in `frontend/` (React + Vite): sign in, see your portfolio re-valued live from the
WebSocket price stream, buy and sell, manage price alerts, and keep named watchlists with live prices.

```bash
cd frontend
npm install
npm run dev          # http://localhost:5173
```

The backend must be running on port 8080 (set `BACKEND_URL` to use another). The dev server proxies `/api` and
`/ws` to it, so no CORS setup is needed. Prices only stream for symbols you hold, have alerts on, or have on
a watchlist, so add a symbol to a watchlist, buy something or add an alert to see live updates.

## Live price WebSocket

Connect to `ws://localhost:8080/ws/prices` and authenticate with the first message:

```json
{"type":"auth","token":"<JWT from /api/auth/login>"}
```

The server replies `{"type":"ready"}`, then sends `{"symbol":"INFY.NS","price":1500.0}` only for symbols that user
holds, has an active alert on, or has on a watchlist. Clients that don't authenticate within 5 seconds, or send a bad token, are
disconnected (close code 1008) and never receive prices.

## API overview

| Area | Endpoints |
|---|---|
| Auth | `POST /api/auth/register`, `POST /api/auth/login` (returns a JWT) |
| Stocks | `GET /api/stocks/quote/{symbol}`, `/price/{symbol}?limit=100` (stored snapshots, newest first), `/price/{symbol}/latest`, `/search`, `/cache-stats` |
| Portfolio | `GET /api/portfolio`, `/summary` (live P&L), `/transactions`; `POST /buy`, `/sell` |
| Alerts | `POST/GET /api/alerts`, `DELETE /api/alerts/{id}`: target price, stop-loss, percentage change |
| News | `GET /api/news/feed`, `/api/news/{symbol}` |
| Watchlists | `GET/POST /api/watchlists`, `PATCH/DELETE /api/watchlists/{id}`, `POST /api/watchlists/{id}/items`, `DELETE /api/watchlists/{id}/items/{symbol}` |
| Stock DNA | `GET /api/dna/{symbol}` |

All endpoints except `/api/auth/**` and the `/ws/**` WebSocket need `Authorization: Bearer <token>`.

## Watchlists

A user can keep several named lists of symbols to follow. Every symbol on any list streams live prices, with no need
to buy it or set an alert.

- A list called `Default` is created the first time a user opens their watchlists. It can be renamed but not deleted.
- A symbol can be on several lists, but only once per list. Names are unique per user, ignoring capitals.
- Limits: 10 lists per user, 20 symbols per list, and 50 different symbols per user across all lists (each distinct
  symbol costs one Yahoo request per scheduler cycle). Exceeding a limit returns `400`.
- Lists are always loaded through their owner, so another user's list id returns `404`, exactly like an id that does
  not exist.
- Errors: `409` for a duplicate name or a symbol already on the list, `404` for an unknown list or a symbol not on the
  list, `400` for a blank or too long name, an unknown symbol, or deleting the default list.

```bash
# create a list, then add a symbol (symbols are upper-cased and checked against Yahoo / the instruments table)
curl -X POST localhost:8080/api/watchlists -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"name": "Banking"}'
curl -X POST localhost:8080/api/watchlists/2/items -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"symbol": "sbin.ns"}'
curl localhost:8080/api/watchlists -H "Authorization: Bearer $TOKEN"     # every list with its symbols
```

`WatchlistManagementService` holds the rules. `WatchlistService.symbolsFor(userId)` merges holdings, active alerts and
watchlist symbols for the WebSocket, and the price schedulers include every watchlist symbol in what they poll, so no
Kafka consumer needed to change.

## How I measured the cache

`scripts/measure-quote-cache.sh` calls `GET /api/stocks/quote/{symbol}` 20 times and prints the cache
hit/miss counters from `/api/stocks/cache-stats` before and after.

```bash
TOKEN=<jwt> ./scripts/measure-quote-cache.sh INFY.NS 20
```

With the Redis quote cache on (10 s TTL), 19 of 20 calls were cache hits. A call that went to Yahoo Finance
took about 120 ms; a cache hit took about 5 ms.

Caveat: the 120 ms figure is a single sample, not an average. Setting
`app.market-data.quote-cache-ttl-seconds=0` turns the cache off for a proper baseline run.

## Known limitations

- **Sentiment is a keyword lexicon, not a model.** Matching is by substring, so "enterprise" scores as a
  rise, "again" as a gain and "growth" as a negative "row"; negation ("does not fall") is not understood.
  Each is captured as a `@Disabled` test in `SentimentServiceTest`.
- **Quotes come from Yahoo Finance's unofficial endpoint**, so they can be rate-limited or unavailable.
  Holdings without a quote fall back to buy price and are flagged `priceStale`.
- **Tokens cannot be revoked and carry no roles.** A login token stays valid for 24 hours (`app.jwt.expiration-ms`) and there is no logout or refresh endpoint.
- **No integration tests yet.** Kafka, MySQL, Mongo and Redis paths are exercised manually, not in CI.
- **The dashboard is minimal:** no charts, no news view, no allocation breakdown and no automated tests.
- **WebSocket auth is checked once, at connect.** A token that expires while a connection is open keeps working until the client disconnects, and each connection caches its symbol set for 10 seconds, so a new holding, alert or watchlist symbol can take that long
  to start streaming (a symbol nobody else watches also waits for the next scheduler cycle, up to 15 seconds). The upgrade request itself (`/ws/**`) is still open and any origin is allowed; only authenticated messages receive data.
- **Watchlist limits are checked, not locked.** The 10-list, 20-symbol and 50-symbol limits are read before saving, so a
  burst of simultaneous requests could exceed one by a few. Unique keys still prevent duplicates.
- **Live mode accepts any symbol Yahoo has a quote for,** including non-Indian listings, and the dashboard shows every
  price with a rupee sign. Restricting symbols to the `instruments` table or to `.NS` / `.BO` would fix this.
- **Single-node setup:** Kafka replication factor is 1.
