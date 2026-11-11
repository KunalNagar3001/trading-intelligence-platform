# Trading Intelligence Platform

![CI](https://github.com/KunalNagar3001/trading-intelligence-platform/actions/workflows/ci.yml/badge.svg)

A Spring Boot backend for Indian equities and commodity ETFs. It streams live prices through Kafka,
fires price alerts, scores news sentiment, tracks portfolios with real-time P&L, and pushes live prices
to clients over WebSocket.

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

 MySQL: users, instruments, holdings, transactions, alerts      MongoDB: news, price history, stock DNA
```

Alert and WebSocket consumers use separate consumer groups, so each receives every price event independently.
Malformed messages go straight to a dead-letter topic.

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
WebSocket price stream, buy and sell, and manage price alerts.

```bash
cd frontend
npm install
npm run dev          # http://localhost:5173
```

The backend must be running on port 8080 (set `BACKEND_URL` to use another). The dev server proxies `/api` and
`/ws` to it, so no CORS setup is needed. Prices only stream for symbols you hold or have alerts on, so buy
something or add an alert to see live updates.

## Live price WebSocket

Connect to `ws://localhost:8080/ws/prices` and authenticate with the first message:

```json
{"type":"auth","token":"<JWT from /api/auth/login>"}
```

The server replies `{"type":"ready"}`, then sends `{"symbol":"INFY.NS","price":1500.0}` only for symbols that user
holds or has an active alert on. Clients that don't authenticate within 5 seconds, or send a bad token, are
disconnected (close code 1008) and never receive prices.

## API overview

| Area | Endpoints |
|---|---|
| Auth | `POST /api/auth/register`, `POST /api/auth/login` (returns a JWT) |
| Stocks | `GET /api/stocks/quote/{symbol}`, `/price/{symbol}?limit=100` (stored snapshots, newest first), `/price/{symbol}/latest`, `/search`, `/cache-stats` |
| Portfolio | `GET /api/portfolio`, `/summary` (live P&L), `/transactions`; `POST /buy`, `/sell` |
| Alerts | `POST/GET /api/alerts`, `DELETE /api/alerts/{id}`: target price, stop-loss, percentage change |
| News | `GET /api/news/feed`, `/api/news/{symbol}` |
| Stock DNA | `GET /api/dna/{symbol}` |

All endpoints except `/api/auth/**` and the `/ws/**` WebSocket need `Authorization: Bearer <token>`.

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
- **The JWT signing key is hard-coded** in `JwtUtil` and must move to configuration before any real deployment.
- **No integration tests yet.** Kafka, MySQL, Mongo and Redis paths are exercised manually, not in CI.
- **The dashboard is minimal:** no charts, no news view and no allocation breakdown.
- **WebSocket auth is checked once, at connect.** A token that expires while a connection is open keeps working until the client disconnects, and the per-user watchlist is cached for 10 seconds, so a new holding or alert can take that long to start streaming. The upgrade request itself (`/ws/**`) is still open and any origin is allowed; only authenticated messages receive data.
- **Single-node setup:** Kafka replication factor is 1.
