import './HoldingsCard.css'
import { formatPrice, formatQuantity, QUOTE_ASSET } from '../format.js'

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

export function HoldingsCard({ balances, tickers }) {
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

  const holdings = holdingsWithValue(balances.data.balances, tickers)
  const total = holdings.reduce((sum, holding) => sum + (holding.value ?? 0), 0)

  return (
    <section className="card holdings">
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
        <ul className="holding-list">
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
