import './Panels.css'
import { useState } from 'react'
import { api } from '../api.js'
import { defaultSettings, settingsToBody } from '../settings.js'
import { RunDetails } from './RunDetails.jsx'
import { SettingsForm } from './SettingsForm.jsx'

const DEFAULT_FROM = '2021-01-01T00:00'
const DEFAULT_TO = '2021-02-01T00:00'
const DEFAULT_BALANCE = '10000'

const toInstant = (localDateTime) => `${localDateTime}:00Z`

/** Runs an algorithm over stored candles on a virtual account and shows the recorded run. */
export function TrainingPanel({ algorithms, tickers, mode, onRunRecorded }) {
  const [ticker, setTicker] = useState('')
  const [from, setFrom] = useState(DEFAULT_FROM)
  const [to, setTo] = useState(DEFAULT_TO)
  const [balance, setBalance] = useState(DEFAULT_BALANCE)
  const [draft, setDraft] = useState(null)
  const [running, setRunning] = useState(false)
  const [error, setError] = useState(null)
  const [result, setResult] = useState(null)

  const chosenTicker = ticker || tickers?.[0]?.ticker || ''
  const form = draft ?? (algorithms?.[0] ? defaultSettings(algorithms[0]) : null)

  const runTraining = async () => {
    setRunning(true)
    setError(null)
    try {
      const details = await api.runTraining({
        candles: { ticker: chosenTicker, from: toInstant(from), to: toInstant(to) },
        settings: settingsToBody(form),
        startingBalance: Number(balance),
      })
      setResult(details)
      onRunRecorded()
    } catch (failure) {
      setError(failure)
    } finally {
      setRunning(false)
    }
  }

  return (
    <div className="panel-grid">
      <section className="card">
        <div className="card-head">
          <h2>Training</h2>
          <span className={`pill ${running ? 'pill-on' : ''}`}>
            {running ? 'Running…' : 'Settings editable'}
          </span>
        </div>
        {mode !== 'TRAINING' && (
          <p className="notice">Training runs in Training mode. Switch the mode at the top.</p>
        )}
        <fieldset className="date-fields" disabled={running}>
          <label className="field">
            <span className="field-label">Ticker</span>
            <select value={chosenTicker} onChange={(event) => setTicker(event.target.value)}>
              {(tickers ?? []).map(({ ticker: option }) => (
                <option key={option} value={option}>
                  {option}
                </option>
              ))}
            </select>
          </label>
          <label className="field">
            <span className="field-label">From (UTC)</span>
            <input type="datetime-local" value={from} onChange={(event) => setFrom(event.target.value)} />
          </label>
          <label className="field">
            <span className="field-label">To (UTC)</span>
            <input type="datetime-local" value={to} onChange={(event) => setTo(event.target.value)} />
          </label>
          <label className="field">
            <span className="field-label">Starting balance (USDT)</span>
            <input
              type="number"
              min="1"
              value={balance}
              onChange={(event) => setBalance(event.target.value)}
            />
          </label>
        </fieldset>
        <SettingsForm algorithms={algorithms} value={form} onChange={setDraft} disabled={running} />
        {error && (
          <p className="form-error" role="alert">
            {error.message}
          </p>
        )}
        <div className="actions">
          <button
            type="button"
            className="button button-primary"
            disabled={running || mode !== 'TRAINING' || !form || !chosenTicker}
            onClick={runTraining}
          >
            {running ? 'Running…' : 'Run training'}
          </button>
          <span className="muted small">Missing candles are downloaded first, then stored.</span>
        </div>
      </section>
      <section className="card">
        <div className="card-head">
          <h2>Result</h2>
          <span className="muted small">portfolio value, USDT</span>
        </div>
        {result ? (
          <RunDetails details={result} />
        ) : (
          <p className="empty chart-empty">Run a training to see how the algorithm would have done.</p>
        )}
      </section>
    </div>
  )
}
