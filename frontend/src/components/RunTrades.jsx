import './RunTrades.css'
import { useState } from 'react'
import { api } from '../api.js'
import { formatDuration, formatPrice, formatUtcDateTimeShort } from '../format.js'

const pnlClass = (value) => (value >= 0 ? 'change-up' : 'change-down')

function signed(value, suffix = '') {
  const sign = value > 0 ? '+' : ''
  return `${sign}${formatPrice(value)}${suffix}`
}

function TradesTable({ positions }) {
  if (positions.length === 0) {
    return <p className="empty">This run made no trades.</p>
  }
  return (
    <div className="table-wrap">
      <table className="trades-table small">
        <thead>
          <tr>
            <th scope="col">Opened (UTC)</th>
            <th scope="col">Closed</th>
            <th scope="col">Held</th>
            <th scope="col" className="numeric">Cost</th>
            <th scope="col" className="numeric">P&amp;L</th>
            <th scope="col" className="numeric">MAE</th>
            <th scope="col" className="numeric">MFE</th>
          </tr>
        </thead>
        <tbody>
          {positions.map((position) => (
            <tr key={position.id}>
              <td>{formatUtcDateTimeShort(position.openedAt)}</td>
              <td>
                {position.closedAt ? formatUtcDateTimeShort(position.closedAt) : <span className="trade-open">open</span>}
              </td>
              <td>{formatDuration(position.durationSeconds)}</td>
              <td className="numeric">{formatPrice(position.cost)}</td>
              <td className={`numeric ${pnlClass(position.realisedPnl)}`}>
                {signed(position.realisedPnl)}
                <span className="trade-percent"> {signed(position.realisedPnlPercent, '%')}</span>
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
  const [error, setError] = useState(null)

  const toggle = async () => {
    setOpen(!open)
    if (open || positions) {
      return
    }
    try {
      const list = await api.getRunPositions(runId)
      setPositions(list.positions)
      setError(null)
    } catch (failure) {
      setError(failure)
    }
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
          {positions && <TradesTable positions={positions} />}
        </div>
      )}
    </div>
  )
}
