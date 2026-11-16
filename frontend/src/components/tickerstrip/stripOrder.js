import { baseAsset } from '../../format.js'

export const STRIP_SLOTS = 4

function heldValue(ticker, quote, balances) {
  const balance = balances?.find((candidate) => candidate.asset === baseAsset(ticker))
  if (!balance || !quote) {
    return 0
  }
  return (balance.free + balance.locked) * quote.lastPrice
}

/**
 * Pinned tickers first, in pin order, then the rest by the value held in them, biggest first.
 * The first `STRIP_SLOTS` are shown in the strip, the others behind "More tickers".
 */
export function stripOrder(tickers, pinned, balances) {
  const pinnedTickers = pinned
    .map((ticker) => tickers.find((candidate) => candidate.ticker === ticker))
    .filter(Boolean)
  const others = tickers
    .filter(({ ticker }) => !pinned.includes(ticker))
    .map((entry) => ({ entry, value: heldValue(entry.ticker, entry.quote, balances) }))
    .sort((left, right) => right.value - left.value)
    .map(({ entry }) => entry)
  const ordered = [...pinnedTickers, ...others]
  return { shown: ordered.slice(0, STRIP_SLOTS), hidden: ordered.slice(STRIP_SLOTS) }
}
