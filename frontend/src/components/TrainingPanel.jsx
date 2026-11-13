import './Panels.css'
import { useEffect, useState } from 'react'
import { api } from '../api.js'
import { defaultSettings, settingsToBody } from '../settings.js'
import { RunDetails } from './RunDetails.jsx'
import { SettingsForm } from './SettingsForm.jsx'

const DEFAULT_FROM = '2021-01-01T00:00'
const DEFAULT_TO = '2021-02-01T00:00'
const DEFAULT_BALANCE = '10000'
const RUN_CHECK_INTERVAL_MS = 2000

const toInstant = (localDateTime) => `${localDateTime}:00Z`

export function TrainingPanel({ algorithms, tickers, mode, onRunRecorded }) {
  const [ticker, setTicker] = useState('')
  const [from, setFrom] = useState(DEFAULT_FROM)
  const [to, setTo] = useState(DEFAULT_TO)
  const [balance, setBalance] = useState(DEFAULT_BALANCE)
  const [draft, setDraft] = useState(null)
  const [starting, setStarting] = useState(false)
  const [runningRunId, setRunningRunId] = useState(null)
  const [error, setError] = useState(null)
  const [result, setResult] = useState(null)

  const running = starting || runningRunId != null
  const chosenTicker = ticker || tickers?.[0]?.ticker || ''
  const form = draft ?? (algorithms?.[0] ? defaultSettings(algorithms[0]) : null)

  useEffect(() => {
    api
      .getTraining()
      .then((status) => status.runId != null && setRunningRunId(status.runId))
      .catch(setError)
  }, [])

  useEffect(() => {
    if (runningRunId == null) {
      return undefined
    }
    const checkRun = async () => {
      try {
        const details = await api.getRun(runningRunId)
        if (details.run.status !== 'STARTED') {
          setResult(details)
          setRunningRunId(null)
          onRunRecorded()
        }
      } catch (failure) {
        setError(failure)
      }
    }
    const timer = setInterval(checkRun, RUN_CHECK_INTERVAL_MS)
    return () => clearInterval(timer)
  }, [runningRunId, onRunRecorded])

  const startTraining = async () => {
    setStarting(true)
    setError(null)
    setResult(null)
    try {
      const { runId } = await api.startTraining({
        candles: { ticker: chosenTicker, from: toInstant(from), to: toInstant(to) },
        settings: settingsToBody(form),
        startingBalance: Number(balance),
      })
      setRunningRunId(runId)
    } catch (failure) {
      setError(failure)
    } finally {
      setStarting(false)
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
            onClick={startTraining}
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
