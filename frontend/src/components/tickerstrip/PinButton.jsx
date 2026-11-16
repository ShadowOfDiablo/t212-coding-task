import { STRIP_SLOTS } from './stripOrder.js'

/** A small pin toggle in a ticker card's corner; pinned tickers keep their place in the strip. */
export function PinButton({ ticker, pinned, disabled, onToggle }) {
  const label = pinned ? `Unpin ${ticker}` : `Pin ${ticker} to the top`
  return (
    <button
      type="button"
      className={`pin-button ${pinned ? 'is-pinned' : ''}`}
      aria-pressed={pinned}
      aria-label={label}
      title={disabled ? `The strip holds ${STRIP_SLOTS} pins; unpin one first` : label}
      disabled={disabled}
      onClick={() => onToggle(ticker)}
    >
      <svg viewBox="0 0 16 16" aria-hidden="true">
        <path d="M10.5 1.5l4 4-2 1-2.5 2.5.5 3-1.5 1.5-3-3-4 4-1-1 4-4-3-3L3.5 5l3 .5L9 3z" />
      </svg>
    </button>
  )
}
