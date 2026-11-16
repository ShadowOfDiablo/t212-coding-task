import './TickerStrip.css'
import { TickerCard } from './TickerCard.jsx'
import { PinButton } from './tickerstrip/PinButton.jsx'
import { arePinsFull, stripOrder } from './tickerstrip/stripOrder.js'

const PLACEHOLDER_CARDS = 2

/** The four tickers on top: pinned ones first, then the biggest holdings. */
export function TickerStrip({ tickers, balances, pinned, onTogglePin }) {
  if (!tickers) {
    return (
      <section className="ticker-strip" aria-label="Followed tickers">
        {Array.from({ length: PLACEHOLDER_CARDS }, (_, index) => (
          <article key={index} className="card ticker-card">
            <p className="skeleton skeleton-line" />
            <p className="skeleton skeleton-price" />
          </article>
        ))}
      </section>
    )
  }
  const { shown } = stripOrder(tickers, pinned, balances)
  const pinsFull = arePinsFull(tickers, pinned)

  return (
    <section className="ticker-strip" aria-label="Followed tickers">
      {shown.map(({ ticker, quote }) => (
        <TickerCard
          key={ticker}
          ticker={ticker}
          quote={quote}
          headAction={
            <PinButton
              ticker={ticker}
              pinned={pinned.includes(ticker)}
              disabled={pinsFull && !pinned.includes(ticker)}
              onToggle={onTogglePin}
            />
          }
        />
      ))}
    </section>
  )
}
