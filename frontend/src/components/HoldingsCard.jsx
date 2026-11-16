import './HoldingsCard.css'
import { useRef } from 'react'
import { baseAsset, formatPrice, formatQuantity, QUOTE_ASSET } from '../format.js'
import { useRowsThatFit } from '../hooks/useRowsThatFit.js'

const TRADING_OFF_STATUS = 409

function lastPrices(tickers) {
  const prices = new Map([[QUOTE_ASSET, 1]])
  for (const { ticker, quote } of tickers ?? []) {
    if (quote && ticker.endsWith(QUOTE_ASSET)) {
      prices.set(ticker.slice(0, -QUOTE_ASSET.length), quote.lastPrice)
    }
  }
  return prices
}

function holdingsWithValue(balances, tickers) {
  const prices = lastPrices(tickers)
  return balances
    .map((balance) => {
      const quantity = balance.free + balance.locked
      const price = prices.get(balance.asset)
      return { ...balance, quantity, value: price == null ? null : quantity * price }
    })
    .sort((left, right) => (right.value ?? -1) - (left.value ?? -1))
}

/** A stretched card fills up with followed currencies until it lists this many. */
const MAX_FILLED_HOLDINGS = 5

/** Followed currencies the account holds none of, shown at zero to fill a stretched card. */
function followedNotHeld(tickers, holdings) {
  const held = new Set(holdings.map((holding) => holding.asset))
  return (tickers ?? [])
    .map(({ ticker }) => baseAsset(ticker))
    .filter((asset) => !held.has(asset))
    .map((asset) => ({ asset, quantity: 0, value: 0 }))
    .slice(0, Math.max(0, MAX_FILLED_HOLDINGS - holdings.length))
}

/**
 * The account's balances by value. With `fill`, a card stretched beside a taller one lists followed
 * currencies at zero too, as many as fit, up to five currencies in all.
 */
export function HoldingsCard({ balances, tickers, fill = false }) {
  const cardRef = useRef(null)
  const listRef = useRef(null)
  const loadedHoldings = balances.data ? holdingsWithValue(balances.data.balances, tickers) : []
  const extras = fill ? followedNotHeld(tickers, loadedHoldings) : []
  const extraCount = useRowsThatFit(
    cardRef,
    listRef,
    extras.length,
    `${loadedHoldings.length}:${extras.map((extra) => extra.asset).join(',')}`,
  )

  if (balances.error) {
    return (
      <section className="card holdings">
        <h2>Holdings</h2>
        <p className="empty">
          {balances.error.status === TRADING_OFF_STATUS
            ? 'Trading is off: set the demo API key to see the account.'
            : 'Could not load the balances.'}
        </p>
      </section>
    )
  }
  if (!balances.data) {
    return (
      <section className="card holdings">
        <h2>Holdings</h2>
        {[0, 1, 2].map((row) => (
          <p key={row} className="skeleton skeleton-line" />
        ))}
      </section>
    )
  }

  const holdings = [...loadedHoldings, ...extras.slice(0, extraCount)]
  const total = holdings.reduce((sum, holding) => sum + (holding.value ?? 0), 0)

  return (
    <section ref={cardRef} className="card holdings">
      <div className="card-head">
        <h2>Holdings</h2>
        <span className="muted small">valued in {QUOTE_ASSET}</span>
      </div>
      <p className="total">
        {formatPrice(total)} <span className="muted">{QUOTE_ASSET}</span>
      </p>
      {holdings.length === 0 ? (
        <p className="empty">No holdings yet.</p>
      ) : (
        <ul ref={listRef} className="holding-list">
          {holdings.map((holding) => {
            const share = holding.value == null || total === 0 ? 0 : holding.value / total
            return (
              <li key={holding.asset} className="holding">
                <div className="holding-row">
                  <span className="asset-badge">{holding.asset.slice(0, 1)}</span>
                  <div className="holding-name">
                    <strong>{holding.asset}</strong>
                    <span className="muted small">{formatQuantity(holding.quantity)}</span>
                  </div>
                  <div className="holding-value">
                    <strong>{holding.value == null ? '—' : formatPrice(holding.value)}</strong>
                    <span className="muted small">
                      {holding.value == null ? 'not traded' : `${(share * 100).toFixed(1)}%`}
                    </span>
                  </div>
                </div>
                <div className="share-bar" aria-hidden="true">
                  <span style={{ transform: `scaleX(${share})` }} />
                </div>
              </li>
            )
          })}
        </ul>
      )}
    </section>
  )
}
