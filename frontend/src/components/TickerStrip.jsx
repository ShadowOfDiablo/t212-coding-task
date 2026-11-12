import './TickerStrip.css'
import { FollowTickerCard } from './FollowTickerCard.jsx'
import { TickerCard } from './TickerCard.jsx'

const PLACEHOLDER_CARDS = 2

export function TickerStrip({ tickers, onFollowed }) {
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
  return (
    <section className="ticker-strip" aria-label="Followed tickers">
      {tickers.map(({ ticker, quote }) => (
        <TickerCard key={ticker} ticker={ticker} quote={quote} />
      ))}
      <FollowTickerCard onFollowed={onFollowed} />
    </section>
  )
}
