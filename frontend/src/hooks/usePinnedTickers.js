import { useEffect, useState } from 'react'

const STORAGE_KEY = 'trading-bot-pinned-tickers'

function savedPins() {
  try {
    const saved = JSON.parse(localStorage.getItem(STORAGE_KEY) ?? '[]')
    return Array.isArray(saved) ? saved.filter((ticker) => typeof ticker === 'string') : []
  } catch {
    // Storage can be blocked or hold something else; start without pins.
    return []
  }
}

/** The tickers pinned to the top strip, in pin order, kept in this browser. */
export function usePinnedTickers() {
  const [pinned, setPinned] = useState(savedPins)

  useEffect(() => {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(pinned))
    } catch {
      // Not saving the pins is fine.
    }
  }, [pinned])

  const togglePin = (ticker) =>
    setPinned((current) =>
      current.includes(ticker) ? current.filter((pinnedTicker) => pinnedTicker !== ticker) : [...current, ticker],
    )
  return { pinned, togglePin }
}
