import { TickerCard } from './TickerCard.jsx'

const PLACEHOLDER_CARDS = 2

export function TickerStrip({ tickers }) {
  if (!tickers) {
    return (
      <section className="ticker-strip" aria-label="Traded tickers">
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
    <section className="ticker-strip" aria-label="Traded tickers">
      {tickers.map(({ ticker, quote }) => (
        <TickerCard key={ticker} ticker={ticker} quote={quote} />
      ))}
    </section>
  )
}
