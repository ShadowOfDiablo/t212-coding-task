import './Panels.css'
import { useEffect, useId, useState } from 'react'
import { api } from '../api.js'
import { defaultSettings, settingsFromResponse, settingsToBody, tickerOptions } from '../settings.js'
import {
  checkFromInstant,
  defaultTuning,
  hasAnythingToTune,
  isTuned,
  tuningToBody,
  withDefaults,
  withTuned,
} from '../tuning.js'
import { DateTimePicker } from './DateTimePicker.jsx'
import { Dropdown } from './Dropdown.jsx'
import { RunDetails } from './RunDetails.jsx'
import { SaveCurrentSettings } from './SaveCurrentSettings.jsx'
import { SettingsForm } from './SettingsForm.jsx'
import { TuningComparison } from './TuningComparison.jsx'
import { TuningFields } from './TuningFields.jsx'
import { TuningProgress } from './TuningProgress.jsx'
import { ReviewColumn } from './review/ReviewColumn.jsx'

const DEFAULT_FROM = '2021-01-01T00:00'
const DEFAULT_TO = '2021-02-01T00:00'
const DEFAULT_BALANCE = '10000'
const PROGRESS_POLL_MS = 1000
const DONE_PERCENT = 100

const toInstant = (localDateTime) => `${localDateTime}:00Z`

function statusText(running, tuning) {
  if (tuning) {
    return 'Tuning…'
  }
  if (running) {
    return 'Running…'
  }
  return 'Settings editable'
}

export function TrainingPanel({
  algorithms,
  tickers,
  mode,
  onRunRecorded,
  saved,
  onFormChange,
  reviewAside,
  onReviewingChange,
}) {
  const tickerId = useId()
  const fromId = useId()
  const toId = useId()
  const [ticker, setTicker] = useState('')
  const [from, setFrom] = useState(DEFAULT_FROM)
  const [to, setTo] = useState(DEFAULT_TO)
  const [balance, setBalance] = useState(DEFAULT_BALANCE)
  const [draft, setDraft] = useState(null)
  const [pickedEntryId, setPickedEntryId] = useState(null)
  const [tuningDraft, setTuningDraft] = useState(null)
  const [running, setRunning] = useState(false)
  const [tuning, setTuning] = useState(false)
  const [progress, setProgress] = useState(0)
  const [error, setError] = useState(null)
  const [result, setResult] = useState(null)
  const [tuningResult, setTuningResult] = useState(null)

  const chosenTicker = ticker || tickers?.[0]?.ticker || ''
  const form = draft ?? (algorithms?.[0] ? defaultSettings(algorithms[0]) : null)
  const formKey = JSON.stringify(form)

  useEffect(() => {
    onFormChange(JSON.parse(formKey))
  }, [formKey, onFormChange])
  const algorithm = algorithms?.find((candidate) => candidate.type === form?.algorithmType)
  const tuningForm =
    tuningDraft?.algorithmType === form?.algorithmType ? tuningDraft : algorithm && defaultTuning(algorithm)
  const busy = running || tuning
  const canStart = !busy && mode === 'TRAINING' && form && chosenTicker
  const tuningControls = tuningForm && {
    isTuned: (name) => isTuned(tuningForm, name),
    onToggle: (name, checked) => setTuningDraft(withTuned(tuningForm, name, checked)),
    disabled: busy,
  }

  useEffect(() => {
    if (!tuning) {
      return undefined
    }
    const timer = setInterval(async () => {
      try {
        const status = await api.getTraining()
        if (status.tuningProgressPercent != null) {
          setProgress(status.tuningProgressPercent)
        }
      } catch {
        // The bar keeps its last value; the tuning request itself reports failures.
      }
    }, PROGRESS_POLL_MS)
    return () => clearInterval(timer)
  }, [tuning])

  const applyTunedSettings = () => {
    setDraft(settingsFromResponse(tuningResult.tuned.run.settings))
    setTuningResult({ ...tuningResult, applied: true })
  }

  const candles = () => ({ ticker: chosenTicker, from: toInstant(from), to: toInstant(to) })

  const runTraining = async () => {
    setRunning(true)
    setError(null)
    try {
      const details = await api.runTraining({
        candles: candles(),
        settings: settingsToBody(form),
        startingBalance: Number(balance),
      })
      setResult(details)
      setTuningResult(null)
      onRunRecorded()
    } catch (failure) {
      setError(failure)
    } finally {
      setRunning(false)
    }
  }

  const runTuning = async () => {
    setTuning(true)
    setProgress(0)
    setError(null)
    const oldSettings = withDefaults(settingsToBody(form), algorithm)
    const checkFrom = checkFromInstant(toInstant(from), toInstant(to), tuningForm.tuneSharePercent)
    try {
      const tuned = await api.runTuning({
        candles: candles(),
        checkFrom,
        settings: settingsToBody(form),
        startingBalance: Number(balance),
        ...tuningToBody(tuningForm, algorithm),
      })
      setProgress(DONE_PERCENT)
      setTuningResult({ tuned, oldSettings, checkFrom, applied: false })
      setResult(null)
      onRunRecorded()
    } catch (failure) {
      setError(failure)
    } finally {
      setTuning(false)
    }
  }

  const reviewing = Boolean(result || tuningResult)
  useEffect(() => onReviewingChange?.(reviewing), [reviewing, onReviewingChange])

  return (
    <div className="panel-grid">
      <ReviewColumn reviewing={reviewing} aside={reviewAside}>
        <section className="card">
          <div className="card-head">
            <h2>Training</h2>
            <span className={`pill ${busy ? 'pill-on' : ''}`}>{statusText(running, tuning)}</span>
          </div>
          {mode !== 'TRAINING' && (
            <p className="notice">Training runs in Training mode. Switch the mode at the top.</p>
          )}
          <fieldset className="date-fields" disabled={busy}>
            <div className="field">
              <label className="field-label" htmlFor={tickerId}>
                Ticker
              </label>
              <Dropdown id={tickerId} value={chosenTicker} options={tickerOptions(tickers)} onChange={setTicker} />
            </div>
            <label className="field">
              <span className="field-label">Starting balance (USDT)</span>
              <input
                type="number"
                min="1"
                value={balance}
                onChange={(event) => setBalance(event.target.value)}
              />
            </label>
            <div className="field">
              <label className="field-label" htmlFor={fromId}>
                From (UTC)
              </label>
              <DateTimePicker id={fromId} value={from} onChange={setFrom} disabled={busy} />
            </div>
            <div className="field">
              <label className="field-label" htmlFor={toId}>
                To (UTC)
              </label>
              <DateTimePicker id={toId} value={to} onChange={setTo} disabled={busy} />
            </div>
          </fieldset>
          <SettingsForm
            algorithms={algorithms}
            value={form}
            onChange={setDraft}
            disabled={busy}
            tuning={tuningControls}
            saved={saved}
            onPickSaved={(entry) => setPickedEntryId(entry.id)}
          >
            {form && (
              <SaveCurrentSettings form={form} saved={saved} pickedEntryId={pickedEntryId} disabled={busy} />
            )}
          </SettingsForm>
          <TuningFields value={tuningForm} onChange={setTuningDraft} disabled={busy} />
          {error && (
            <p className="form-error" role="alert">
              {error.message}
            </p>
          )}
          <div className="actions">
            <button
              type="button"
              className="button button-primary"
              disabled={!canStart}
              onClick={runTraining}
            >
              {running ? 'Running…' : 'Run backtest'}
            </button>
            <button
              type="button"
              className="button"
              disabled={!canStart || !tuningForm || !hasAnythingToTune(tuningForm)}
              onClick={runTuning}
            >
              {tuning ? 'Tuning…' : 'Tune settings'}
            </button>
            <span className="muted small">Missing candles are downloaded first, then stored.</span>
          </div>
          {tuning && <TuningProgress percent={progress} />}
        </section>
      </ReviewColumn>
      <section className={`card ${!tuningResult && !result ? 'card-fill' : ''}`}>
        <div className="card-head">
          <h2>{tuningResult ? 'Old vs tuned settings' : 'Result'}</h2>
          <span className="muted small">portfolio value, USDT</span>
        </div>
        {tuningResult && (
          <TuningComparison
            tuning={tuningResult.tuned}
            oldSettings={tuningResult.oldSettings}
            checkFrom={tuningResult.checkFrom}
            applied={tuningResult.applied}
            disabled={busy}
            onApply={applyTunedSettings}
            saved={saved}
          />
        )}
        {!tuningResult && result && <RunDetails details={result} />}
        {!tuningResult && !result && (
          <p className="empty chart-empty">
            Run a training to see how the algorithm would have done, or tune its settings.
          </p>
        )}
      </section>
    </div>
  )
}
