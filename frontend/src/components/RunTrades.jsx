import './RunTrades.css'
import { useState } from 'react'
import { api } from '../api.js'
import { formatDuration, formatPrice, formatUtcClock, formatUtcDate } from '../format.js'
import { HeaderHint } from './HeaderHint.jsx'
import { LineChart } from './LineChart.jsx'

const TRADES_PER_PAGE = 10

function TradeTime({ iso }) {
  return (
    <span className="trade-stack">
      <span>{formatUtcDate(iso)}</span>
      <span className="trade-clock">{formatUtcClock(iso)}</span>
    </span>
  )
}

/** The average buy price over the average sell price; positions stored before V12 have none. */
function TradePrices({ position }) {
  if (position.entryPrice == null) {
    return '–'
  }
  return (
    <span className="trade-stack">
      <span>Buy {formatPrice(position.entryPrice)}</span>
      {position.exitPrice != null && (
        <span className="trade-clock">Sell {formatPrice(position.exitPrice)}</span>
      )}
    </span>
  )
}

/** Each position as a buy at its open and, once sold, a sell at its close, for the price chart. */
function tradeMarkers(positions) {
  return positions
    .filter((position) => position.entryPrice != null)
    .flatMap((position) => [
      { time: position.openedAt, price: position.entryPrice, side: 'BUY' },
      ...(position.closedAt && position.exitPrice != null
        ? [{ time: position.closedAt, price: position.exitPrice, side: 'SELL' }]
        : []),
    ])
}

function PriceChart({ prices, positions }) {
  const [showTrades, setShowTrades] = useState(false)
  if (prices.length === 0) {
    return null
  }
  return (
    <div className="run-price-chart">
      <label className="check-label small">
        <input type="checkbox" checked={showTrades} onChange={(event) => setShowTrades(event.target.checked)} />
        Show trades
      </label>
      <LineChart
        points={prices.map((point) => ({ time: point.time, value: point.price }))}
        trades={showTrades ? tradeMarkers(positions) : []}
        emptyText="No prices for this run."
      />
    </div>
  )
}

const pnlClass = (value) => (value >= 0 ? 'change-up' : 'change-down')

function signed(value, suffix = '') {
  const sign = value > 0 ? '+' : ''
  return `${sign}${formatPrice(value)}${suffix}`
}

function Pager({ page, pageCount, total, onPage }) {
  const first = page * TRADES_PER_PAGE + 1
  const last = Math.min(total, first + TRADES_PER_PAGE - 1)
  return (
    <div className="pager">
      <span className="muted small">
        {first}–{last} of {total}
      </span>
      <div className="pager-buttons">
        <button type="button" className="button" disabled={page === 0} onClick={() => onPage(page - 1)}>
          Newer
        </button>
        <span className="small">
          {page + 1} / {pageCount}
        </span>
        <button
          type="button"
          className="button"
          disabled={page === pageCount - 1}
          onClick={() => onPage(page + 1)}
        >
          Older
        </button>
      </div>
    </div>
  )
}

function TradesPage({ positions }) {
  const [page, setPage] = useState(0)
  if (positions.length === 0) {
    return <p className="empty">This run made no trades.</p>
  }
  const pageCount = Math.ceil(positions.length / TRADES_PER_PAGE)
  const start = page * TRADES_PER_PAGE
  return (
    <>
      <TradesTable positions={positions.slice(start, start + TRADES_PER_PAGE)} />
      {pageCount > 1 && (
        <Pager page={page} pageCount={pageCount} total={positions.length} onPage={setPage} />
      )}
    </>
  )
}

function TradesTable({ positions }) {
  return (
    <div className="table-wrap">
      <table className="trades-table small">
        <thead>
          <tr>
            <th scope="col">Opened (UTC)</th>
            <th scope="col">Closed</th>
            <th scope="col">Held</th>
            <th scope="col" className="numeric">Quantity</th>
            <th scope="col" className="numeric">Price</th>
            <th scope="col" className="numeric">Cost</th>
            <th scope="col" className="numeric">P&amp;L</th>
            <HeaderHint className="numeric" label="MAE" title="Maximum adverse excursion">
              The worst the trade stood while open: its lowest unrealised P&amp;L, measured from the
              entry price on every price seen. It shows how much loss the trade sat through before
              it closed, and helps set a stop loss.
            </HeaderHint>
            <HeaderHint className="numeric" label="MFE" title="Maximum favourable excursion">
              The best the trade stood while open: its highest unrealised P&amp;L. Compare it with
              the P&amp;L to see how much profit was given back before the sell, and to set a take
              profit.
            </HeaderHint>
          </tr>
        </thead>
        <tbody>
          {positions.map((position) => (
            <tr key={position.id}>
              <td>
                <TradeTime iso={position.openedAt} />
              </td>
              <td>
                {position.closedAt ? <TradeTime iso={position.closedAt} /> : <span className="trade-open">open</span>}
              </td>
              <td>{formatDuration(position.durationSeconds)}</td>
              <td className="numeric">{position.quantity ?? '–'}</td>
              <td className="numeric">
                <TradePrices position={position} />
              </td>
              <td className="numeric">{formatPrice(position.cost)}</td>
              <td className={`numeric ${pnlClass(position.realisedPnl)}`}>
                <span className="trade-stack">
                  <span>{signed(position.realisedPnl)}</span>
                  <span className="trade-percent">{signed(position.realisedPnlPercent, '%')}</span>
                </span>
              </td>
              <td className="numeric change-down">{formatPrice(position.mae)}</td>
              <td className="numeric change-up">{formatPrice(position.mfe)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

/** A Trades button that loads the run's positions, newest first, the first time it opens. */
export function RunTrades({ runId }) {
  const [open, setOpen] = useState(false)
  const [positions, setPositions] = useState(null)
  const [prices, setPrices] = useState([])
  const [error, setError] = useState(null)

  const toggle = async () => {
    setOpen(!open)
    if (open || positions) {
      return
    }
    const pricesLoad = api.getRunPrices(runId).catch(() => ({ points: [] }))
    try {
      const list = await api.getRunPositions(runId)
      setPositions(list.positions)
      setError(null)
    } catch (failure) {
      setError(failure)
    }
    setPrices((await pricesLoad).points)
  }

  return (
    <div className="run-trades">
      <button type="button" className="button" aria-expanded={open} onClick={toggle}>
        {open ? 'Hide trades' : 'Trades'}
        {positions && <span className="count">{positions.length}</span>}
      </button>
      {open && (
        <div className="run-trades-body">
          {error && <p className="form-error small">Could not load the trades: {error.message}</p>}
          {!error && !positions && <p className="skeleton skeleton-line" />}
          {positions && <PriceChart prices={prices} positions={positions} />}
          {positions && <TradesPage positions={positions} />}
        </div>
      )}
    </div>
  )
}
