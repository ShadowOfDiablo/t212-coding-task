import { useEffect, useRef, useState } from 'react'
import { baseAsset, formatPrice, QUOTE_ASSET } from '../format.js'

export function TickerCard({ ticker, quote, headAction }) {
  const lastPrice = quote?.lastPrice ?? null
  const previousPrice = useRef(lastPrice)
  const [move, setMove] = useState({ direction: 'flat', tick: 0 })

  useEffect(() => {
    const previous = previousPrice.current
    previousPrice.current = lastPrice
    if (previous == null || lastPrice == null || previous === lastPrice) {
      return
    }
    setMove((current) => ({
      direction: lastPrice > previous ? 'up' : 'down',
      tick: current.tick + 1,
    }))
  }, [lastPrice])

  return (
    <article className="card ticker-card">
      <div className="ticker-head">
        <span className="ticker-symbol">
          {baseAsset(ticker)}
          <span className="ticker-quote">/{QUOTE_ASSET}</span>
        </span>
        <span className="ticker-head-actions">
          <span className={`trend trend-${move.direction}`} aria-hidden="true">
            {move.direction === 'down' ? '▼' : '▲'}
          </span>
          {headAction}
        </span>
      </div>
      {quote ? (
        <>
          <p key={move.tick} className={`ticker-price flash-${move.direction}`}>
            {formatPrice(quote.lastPrice)}
          </p>
          <dl className="ticker-book">
            <div>
              <dt>Bid</dt>
              <dd>{formatPrice(quote.bid)}</dd>
            </div>
            <div>
              <dt>Ask</dt>
              <dd>{formatPrice(quote.ask)}</dd>
            </div>
          </dl>
        </>
      ) : (
        <>
          <p className="skeleton skeleton-price" />
          <p className="muted small">Waiting for market data…</p>
        </>
      )}
    </article>
  )
}
