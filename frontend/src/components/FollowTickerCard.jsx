import { useState } from 'react'
import { api } from '../api.js'

/** Adds a ticker to the followed list; the backend checks that Binance lists it. */
export function FollowTickerCard({ onFollowed }) {
  const [ticker, setTicker] = useState('')
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState(null)

  const follow = async (event) => {
    event.preventDefault()
    const symbol = ticker.trim().toUpperCase()
    if (!symbol) {
      return
    }
    setSaving(true)
    try {
      await api.followTicker(symbol)
      setTicker('')
      setError(null)
      onFollowed()
    } catch (failure) {
      setError(failure)
    } finally {
      setSaving(false)
    }
  }

  return (
    <form className="card ticker-card follow-card" onSubmit={follow}>
      <span className="field-label">Follow a ticker</span>
      <input
        type="text"
        placeholder="e.g. SOLUSDT"
        value={ticker}
        disabled={saving}
        onChange={(event) => setTicker(event.target.value.toUpperCase())}
        aria-label="Ticker to follow"
      />
      <button type="submit" className="button button-primary" disabled={saving || !ticker.trim()}>
        {saving ? 'Checking…' : 'Follow'}
      </button>
      {error && (
        <p className="form-error small" role="alert">
          {error.message}
        </p>
      )}
    </form>
  )
}
