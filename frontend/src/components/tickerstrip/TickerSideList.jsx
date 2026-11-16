import './TickerSideList.css'
import { baseAsset, formatPrice, QUOTE_ASSET } from '../../format.js'
import { FollowTickerCard } from '../FollowTickerCard.jsx'
import { PinButton } from './PinButton.jsx'
import { arePinsFull, stripOrder } from './stripOrder.js'

/**
 * The followed tickers that don't fit in the top strip, at the left of the page. Pinning one moves
 * it into the strip; the follow box sits at the bottom.
 */
export function TickerSideList({ tickers, balances, pinned, onTogglePin, onFollowed }) {
  const { hidden } = stripOrder(tickers ?? [], pinned, balances)
  const pinsFull = arePinsFull(tickers ?? [], pinned)

  return (
    <aside className="ticker-side-list" aria-label="Other followed tickers">
      <div className="ticker-side-inner">
        <h2 className="ticker-side-title">Tickers</h2>
        {hidden.length === 0 ? (
          <p className="muted small">All followed tickers are in the strip.</p>
        ) : (
          <ul>
            {hidden.map(({ ticker, quote }) => (
              <li key={ticker}>
                <span className="ticker-side-symbol">
                  {baseAsset(ticker)}
                  <span className="ticker-quote">/{QUOTE_ASSET}</span>
                </span>
                <span className="ticker-side-price">{formatPrice(quote?.lastPrice)}</span>
                <PinButton ticker={ticker} pinned={false} disabled={pinsFull} onToggle={onTogglePin} />
              </li>
            ))}
          </ul>
        )}
        <FollowTickerCard onFollowed={onFollowed} />
      </div>
    </aside>
  )
}
