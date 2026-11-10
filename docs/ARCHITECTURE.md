# Architecture

## 1. Overview

The application simulates an autonomous crypto trading bot. It reads market data from Binance, generates trading signals and executes **simulated** orders against a virtual account. No real orders are ever sent to an exchange.

| Mode | Data source | Purpose |
|---|---|---|
| `TRAINING` (backtest) | Historical Binance data (REST) | Run the bot over a past period to analyse it |
| `TRADING` (live-sim) | Live Binance streams (WebSocket) | Run the bot on live prices |

Both modes run **the same pipeline and the same logic**. Only the source of events differs. No look-ahead: an event only ever sees the data the candle feed has delivered up to that event's execution time.

## 2. Components

| Component | Responsibility |
|---|---|
| `Broker` | One exchange: name, tickers, candle feeder and ticker price map. `AbstractBroker` is the abstract factory; `BinanceBroker` is the first implementation. One singleton per exchange |
| `BrokerRegistryService` | Holds every broker by name, so several brokers can run at once. Rejects duplicate names |
| `CandleFeeder` | One shared class for all brokers: WebSocket connections, grouping streams by connection, reconnect, history paging. Writes the broker's ticker price map and hands `CandleUpdate`s to listeners. Never touches the account |
| `ExchangeMarketDataProtocol` | The exchange-specific part of market data: stream names, URLs, limits, message parsing |
| `TradingBotService` | Owns the event loop, the bot state and the runs |
| `SignalGenerator` | Interface for trading logic, with several implementations chosen by config. Returns `Buy`, `Sell` or `Hold` |
| `OrderValidator` | Turns a signal into a valid `OrderRequest`, or rejects it |
| `FeeService` | Provides fee rates (exact Binance rates with an API key, the standard schedule otherwise) and calculates fees |
| `OrderExecutionService` | Fills a valid `OrderRequest` and produces an `ExecutedOrder`. No validation or fee logic |
| `AccountService` | Applies `ExecutedOrder`s to cash and positions, and calculates P&L and portfolio value |
| `TickerPriceMap` | Latest quote per ticker, per broker. Written by the candle feeder (`TickerPriceWriter`), read by everyone else (`TickerPriceReader`) |
| Repositories | Raw SQL persistence (`JdbcClient`), with no ORM |
| REST API | Controls and read endpoints for the React dashboard |

Components exchange immutable DTOs (Java records): `CandleUpdate`, `Signal`, `OrderRequest`, `ExecutedOrder`, `TradeHistoryEntry`, `PortfolioSnapshot`.

## 3. Event pipeline

```
 Binance REST (1s candles)     Binance WebSocket (candle + bookTicker)
          │ TRAINING                      │ TRADING
          └──────────────┬────────────────┘
          Broker.candleFeeder()  (writes the broker's ticker price map)
                         │ CandleUpdate
          PriorityBlockingQueue  (ordered by execution time)
                         │
                 TradingBotService  (per event)
   1. SignalGenerator.onUpdate(update, context)   → Buy | Sell | Hold
   2. OrderValidator.validate(signal, account)    → OrderRequest | Rejected
   3. FeeService.fee(orderRequest)
   4. OrderExecutionService.execute(orderRequest) → ExecutedOrder
   5. AccountService.apply(executedOrder)
   6. Persistence (trade, position, snapshot)
                         │
                     REST API  →  React dashboard
```

## 4. Market data

**Brokers**
```
BrokerRegistryService  (singleton, Map<name, Broker>)
   └── «interface» Broker: name(), tickers(), candleFeeder(), tickerPrices()
         └── «abstract» AbstractBroker  (abstract factory: shared state, builds the CandleFeeder
               │                        from createMarketDataProtocol())
               ├── BinanceBroker   (singleton)
               └── KrakenBroker    (later, singleton)
```
- Every broker is a Spring singleton. The registry fails at startup if two brokers share a name.
- Each broker *has* a `CandleFeeder` (composition). The feeder logic is shared; only the `ExchangeMarketDataProtocol` differs per exchange.
- Planned: a portfolio per broker, transfers between brokers (which take time), and an `OrderGateway` factory method. Orders go to the Binance Spot Testnet first, then to real Binance's validate-only `POST /api/v3/order/test`.

**`CandleFeeder`**
- Streams all of a broker's tickers over **combined streams**: one connection carries up to 1,024 streams (Binance's limit). With 2 streams per ticker (candle + bookTicker), that's about 500 tickers per connection. Further connections are opened only beyond that.
- The receive path stays fast: parse the message, update the price map (lock-free), put the `CandleUpdate` on a bounded queue for a separate dispatcher thread, then ask the socket for the next message. A full queue drops the update and logs a warning; the socket is never blocked.
- Event times are on the **exchange's clock**. Messages without one (Binance's spot `@bookTicker`) are stamped with the receive time, corrected by the clock offset the feeder measures on start (`GET /api/v3/time` on Binance). If that fails it falls back to the local clock and logs a warning.
- `history(ticker, interval, from, to)` pages historical candles over REST.

**Binance (public endpoints, no key needed)**
- REST `GET /api/v3/klines` on `data-api.binance.vision` (Binance calls candles "klines"), with up to 1,000 candles per request, paged by `startTime`.
- WebSocket `<symbol>@kline_<interval>` and `<symbol>@bookTicker` on `data-stream.binance.vision`, using the JDK `java.net.http.WebSocket`.
  - After a drop it waits a fixed 5 seconds and reconnects (Binance drops connections after 24 hours, and allows 300 connection attempts per 5 minutes per IP).
- `GET /api/v3/exchangeInfo` gives the trading rules per symbol (minimum quantity, step size, minimum order value).

**Candle updates.** A `CandleUpdate` is the current state of the forming candle (open, high, low, close, volume) plus `isClosed` and its execution time.
- **Trading:** the candle stream sends an update every 1–2 seconds. Each update becomes an event.
- **Training:** the same updates are rebuilt from historical **`1s` candles**. They're aggregated into the forming candle of the chosen interval and emitted one second at a time. A day is about 86,400 `1s` candles, which is 87 requests.
- **Cache:** historical candles are stored in the database after the first download, so re-runs use identical data and don't call Binance again.

## 5. Signals

`SignalGenerator.onUpdate(CandleUpdate, SignalContext)` runs on **every candle update** in both modes. `SignalContext` gives read-only access to recent candles, indicators, the open position and the cash balance.

| Signal | Parameters | Meaning |
|---|---|---|
| `Buy` | size (quantity or quote amount) | Open or add to a position |
| `Sell` | size (quantity, or all) | Reduce or close a position |
| `Hold` | none | Do nothing |

Indicators (SMA, EMA, RSI, …) are separate, reusable classes under `signal/indicator`.

## 6. Orders

### Order lifecycle (first version: market orders only)

```
 Signal (Buy/Sell)
      │
   CREATED ──────────────► REJECTED   (validation failed; logged and kept for the dashboard)
      │ valid
   VALIDATED
      │ fee calculated
   EXECUTING ────────────► FAILED     (e.g. no price available; bot goes to ERROR if it repeats)
      │
    FILLED
      │ account updated
   APPLIED
      │ written to the database
   PERSISTED
```

| State | Set by | Meaning |
|---|---|---|
| `CREATED` | `TradingBotService` | Built from a `Buy` or `Sell` signal |
| `VALIDATED` | `OrderValidator` | Passed all checks |
| `REJECTED` | `OrderValidator` | Failed a check, with a reason |
| `EXECUTING` | `OrderExecutionService` | Fill in progress |
| `FILLED` | `OrderExecutionService` | Price and quantity are final |
| `FAILED` | `OrderExecutionService` | Couldn't be filled |
| `APPLIED` | `AccountService` | Cash and position updated |
| `PERSISTED` | Repository | Saved. Only now is the next event allowed to depend on it |

**Rejection reasons:**
- `INSUFFICIENT_FUNDS`
- `INSUFFICIENT_HOLDINGS` (selling more than is held)
- `NO_POSITION` (selling with nothing open)
- `BELOW_MIN_QUANTITY` / `BELOW_MIN_NOTIONAL` (Binance `exchangeInfo` rules)
- `BOT_NOT_RUNNING`

Quantities are rounded down to the symbol's step size before validation.

### Fill price

| Mode | Buy | Sell |
|---|---|---|
| `TRADING` | Best **ask** at the moment of the signal (`bookTicker`) | Best **bid** |
| `TRAINING` | Open of the next `1s` candle after the signal | Same |

### Fees

`FeeService` uses the exact rates from Binance's signed `GET /api/v3/account/commission` when a **read-only** API key is configured. The key comes from environment variables and is never committed. Without a key, it uses the standard schedule: 0.1% maker and 0.1% taker.
- Market orders pay the taker rate.
- The fee is charged in the received asset: the base coin on a buy, the quote coin on a sell.
- Rates are cached and refreshed periodically. Calculating a fee is a direct call.

### Planned order types

These come later and plug into the same lifecycle:
- `LIMIT`, `STOP_LOSS` and `TAKE_PROFIT` orders, plus their `*_LIMIT` variants.
- OCO brackets and trailing stops.
- Time in force (`GTC`, `IOC`, `FOK`).

They add a `PENDING` state, held in an in-memory open-orders book and checked on every update:

```
 VALIDATED → PENDING → TRIGGERED → EXECUTING → FILLED …
                 ├──► CANCELLED  (user, reset, OCO sibling filled)
                 └──► EXPIRED    (time in force)
```

## 7. Account and positions

- The account has a cash balance in the quote currency (e.g. USDT), plus one position per ticker (quantity and average entry price).
- `AccountService.apply(ExecutedOrder)` follows these rules:
  - **Buy, no position:** opens a position. Cash decreases by price × quantity, and the fee is taken from the coin received.
  - **Buy, existing position:** adds the quantity and recalculates the average entry price.
  - **Sell, partial:** reduces the quantity. Realised P&L = (price − average entry) × quantity − fee.
  - **Sell, full:** closes the position, with the same P&L calculation.

**Position states:** `OPEN` stays `OPEN` through further buys and partial sells, then becomes `CLOSED` on a full sell. A new buy afterwards opens a new position.
- While a position is open, its MAE and MFE are updated on every price update. They feed the position review (§9).

- **Unrealised P&L and portfolio value** (cash + Σ quantity × latest price) use the broker's `TickerPriceReader`.
- **Money:** all amounts and quantities use `BigDecimal`.

## 8. Ticker price map

Each broker has a `TickerPriceMap`: its configured tickers mapped to the latest `TickerQuote` (bid, ask, last price, update time, source).
- The key set is fixed at startup. Each value starts as **null** (no data yet) and is filled only by a valid REST fetch or a WebSocket message for that ticker. Updates for other tickers are ignored.
- Values are `AtomicReference<TickerQuote>`s, so updates are lock-free and readers never see a half-written quote.
- **`TickerPriceWriter`:** only the broker's `CandleFeeder` receives it.
- **`TickerPriceReader`:** every other component receives it. Read-only by type, `Optional<TickerQuote> get(ticker)`.
- **Training mode:** the feeder must not run ahead of the bot, so in Training it releases events one at a time and writes each price only as it releases that event. The map never shows a "future" price.

## 9. Bot and run lifecycle

### Bot states

```
 STOPPED ──start(mode)──► RUNNING ──pause──► PAUSED
    ▲                        │  ▲              │
    │                        │  └───resume─────┘
    ├──────── stop ──────────┤
    │                        ▼
    └──────── reset ──────  ERROR  (repeated failures, lost connection after retries)
```

| Control | Allowed from | Effect |
|---|---|---|
| `start(mode, options)` | `STOPPED` | Creates a run and starts the feed |
| `pause` | `RUNNING` | Stops processing. Live updates arriving while paused are dropped |
| `resume` | `PAUSED` | Continues processing |
| `stop` | `RUNNING`, `PAUSED` | Ends the run |
| `reset` | any | Stops and restores the starting balance. A new run is created on the next start |
| switch mode | `STOPPED`, `PAUSED` | Stops the current run and starts one in the other mode |

### Run states

```
 CREATED → RUNNING ⇄ PAUSED → COMPLETED (training reached the end of the range)
                            → STOPPED   (user stopped it)
                            → FAILED    (error)
 COMPLETED → ARCHIVED (training results written)
```

### Training options

Both options replay **as fast as possible**. A backtest over 1.5 years takes minutes, not 1.5 years.
- **Archived (the default):** keeps everything in memory, and writes all trades and snapshots in one transaction when the run completes. The results are viewable afterwards.
- **`live`:** the run is observable while it's in progress. Every trade is written immediately, and progress is pushed to the dashboard as it happens (see §12):
  - progress: the simulated time reached and the % of the range done
  - the portfolio value chart, growing as the run advances
  - the trades appearing in the history table and on the price chart
  - the current top 10 worst positions (see Position review below), updated as positions close

### Position review (both modes)

- Every closed position is kept as a **position record**:
  - entry and exit trades, with their times and prices
  - realised P&L (absolute and %) and duration
  - **MAE** (maximum adverse excursion: the worst unrealised loss while it was open)
  - **MFE** (maximum favourable excursion: the best unrealised profit while it was open)
- **Top 10 worst positions:** closed positions ranked by realised P&L. Each one can be opened as a chart showing:
  - the price path from a little before the entry to a little after the exit, from the candle cache
  - the entry and exit points marked
  - the MAE and MFE points marked

  This shows where the bot entered, where it sold, what happened in between, and what it could have done instead.

## 10. Concurrency

- **Events:** the `PriorityBlockingQueue` is ordered by **execution time**, the moment each event should be processed.
- **First version:** a single worker thread processes events in order. Every order is validated against the latest account state.
- **Later:** a multi-threaded `TradingBotService`:
  - **In parallel:** signal generation, per ticker.
  - **Serialised:** account changes go through a single applier in execution-time order, so the shared cash balance can't be spent twice.
  - **Results:** they depend only on the execution-time order, never on thread timing.
- **Other threads:** the WebSocket callbacks, the REST controls and the REST reads only touch the queue, the bot state, or concurrent and read-only views.

## 11. Persistence

- **Database:** a relational database, accessed with raw SQL through Spring's `JdbcClient`, with no ORM. The schema is created by SQL scripts in `backend/src/main/resources/db/`.
- **Tables:**

| Table | Contents |
|---|---|
| `bot_run` | id, input hash, mode, symbol, interval, date range, generator and its parameters, starting balance, fee rates, status, timestamps |
| `trade` | deterministic id, run id, sequence, execution time, side, symbol, quantity, price, fee, fee asset, realised P&L |
| `position` | id, run id, symbol, status, quantity, average entry price, opened and closed time, realised P&L, MAE, MFE |
| `portfolio_snapshot` | run id, time, cash, holdings value, total value |
| `candle` | symbol, interval, open time, OHLCV (historical data cache) |

**Write policy:**
- **Trading and live training:** each trade and its position change are written in **one transaction** before the next event is processed, so a crash never loses a confirmed trade. Snapshots are written in batches, and always on pause, stop and reset. They can be rebuilt from trades and candles.
- **Archived training:** everything is written once, at completion.

**Idempotency:**
- **Training:**
  - A run is identified by a hash of its inputs: mode, symbol, interval, date range, generator and parameters, starting balance and fee rates.
  - Processing is deterministic and uses cached candles, so identical inputs always give identical trades.
  - Re-running replaces the archived run instead of duplicating it.
- **Trading:**
  - Trades and events have deterministic ids (run id, execution time, sequence).
  - Unique constraints, plus inserts that ignore conflicts, mean a WebSocket reconnect that re-sends updates never creates duplicate trades.

## 12. REST API

Everything is under `/api`.

| Method | Path | Purpose |
|---|---|---|
| GET | `/bot/status` | State, mode, current run, latest price |
| POST | `/bot/start` | `{ "mode": "TRAINING", "from": …, "to": …, "live": false }` |
| POST | `/bot/pause`, `/bot/resume`, `/bot/stop`, `/bot/reset` | Controls |
| GET | `/runs`, `/runs/{id}` | Runs and their results |
| GET | `/runs/{id}/trades` | Trade history: date, action, quantity, price, P&L |
| GET | `/runs/{id}/portfolio` | Current cash, positions and total value |
| GET | `/runs/{id}/portfolio/history` | Portfolio value over time |
| GET | `/runs/{id}/candles` | Price chart data, with trades marked on it |
| GET | `/runs/{id}/positions/worst?limit=10` | The worst closed positions, with P&L, MAE, MFE and duration |
| GET | `/runs/{id}/positions/{positionId}/chart` | The price path around one position, with the entry, exit, MAE and MFE marked |
| GET | `/runs/{id}/events` | Server-Sent Events stream of progress, trades, snapshots and position closes, for live training and trading |

## 13. Configuration

Everything goes in `application.yaml`:
- Binance endpoints
- symbol and interval
- starting balance
- the active signal generator and its parameters
- the default training range

The optional read-only API key is set through the environment variables `BINANCE_API_KEY` and `BINANCE_API_SECRET`.

## 14. Package layout

```
com.tihomir.t212.tradingbot
├── bot            BotMode, BotState, TradingBotService, BotRun, RunStatus
├── broker         Broker, AbstractBroker, BrokerRegistryService
│   └── binance    BinanceBroker, BinanceConnectionProperties, BinanceMarketDataProtocol
├── marketdata
│   ├── update      MarketDataEvent, CandleUpdate, BidAskUpdate, CandleInterval
│   ├── tickerprice TickerPriceMap, TickerPriceReader, TickerPriceWriter, TickerQuote, TickerQuoteSource
│   ├── candlefeed  CandleFeeder, CandleUpdateListener, ExchangeMarketDataProtocol
│   └── websocket   MarketDataStreamConnection, MarketDataStreamConnectionFactory (JDK WebSocket, reconnect)
├── signal         SignalGenerator, Signal, SignalContext
│   └── indicator  SMA, EMA, RSI, ...
├── order          OrderRequest, OrderStatus, RejectionReason, OrderValidator,
│                  OrderExecutionService, ExecutedOrder
│   └── fee        FeeService
├── account        AccountService, Position, PositionStatus, PortfolioSnapshot
├── persistence    repositories (JdbcClient)
└── api            REST controllers and DTOs
```
