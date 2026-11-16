import './TickerStrip.css'
import { useState } from 'react'
import { usePinnedTickers } from '../hooks/usePinnedTickers.js'
import { FollowTickerCard } from './FollowTickerCard.jsx'
import { TickerCard } from './TickerCard.jsx'
import { PinButton } from './tickerstrip/PinButton.jsx'
import { STRIP_SLOTS, stripOrder } from './tickerstrip/stripOrder.js'

const PLACEHOLDER_CARDS = 2

/**
 * The followed tickers: pinned ones first, then the biggest holdings, up to four. The rest open
 * below with "More tickers".
 */
export function TickerStrip({ tickers, balances, onFollowed }) {
  const { pinned, togglePin } = usePinnedTickers()
  const [showMore, setShowMore] = useState(false)

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
  const { shown, hidden } = stripOrder(tickers, pinned, balances)
  const pinsFull = pinned.filter((ticker) => tickers.some((entry) => entry.ticker === ticker)).length >= STRIP_SLOTS
  const card = ({ ticker, quote }) => (
    <TickerCard
      key={ticker}
      ticker={ticker}
      quote={quote}
      headAction={
        <PinButton
          ticker={ticker}
          pinned={pinned.includes(ticker)}
          disabled={pinsFull && !pinned.includes(ticker)}
          onToggle={togglePin}
        />
      }
    />
  )

  return (
    <section className="ticker-strip-wrap" aria-label="Followed tickers">
      <div className="ticker-strip">
        {shown.map(card)}
        <FollowTickerCard onFollowed={onFollowed} />
      </div>
      {hidden.length > 0 && (
        <button
          type="button"
          className="button more-tickers"
          aria-expanded={showMore}
          onClick={() => setShowMore(!showMore)}
        >
          {showMore ? 'Hide other tickers' : `More tickers (${hidden.length})`}
        </button>
      )}
      {showMore && hidden.length > 0 && <div className="ticker-strip">{hidden.map(card)}</div>}
    </section>
  )
}
