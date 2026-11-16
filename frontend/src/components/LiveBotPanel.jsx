import './Panels.css'
import { useState } from 'react'
import { api } from '../api.js'
import { usePolling } from '../hooks/usePolling.js'
import {
  defaultSettings,
  settingsFromResponse,
  settingsToBody,
  tickerOptions,
  timeframeLabel,
} from '../settings.js'
import { Dropdown } from './Dropdown.jsx'
import { LineChart } from './LineChart.jsx'
import { SettingsForm } from './SettingsForm.jsx'

const BOT_REFRESH_MS = 2000

const CHART_RANGES = [
  { id: 'all', label: 'All live runs' },
  { id: 'run', label: 'This run' },
]

const byTime = (left, right) => new Date(left.time) - new Date(right.time)

/** Every live run as its start and end value, then the running run's own points. */
function allLiveRunPoints(runs, status) {
  const finishedRuns = (runs ?? []).filter((run) => run.mode !== 'TRAINING' && run.id !== status?.runId)
  const runEnds = finishedRuns.flatMap((run) => [
    { time: run.startedAt, value: run.startingValue },
    ...(run.finishedAt && run.finalValue != null ? [{ time: run.finishedAt, value: run.finalValue }] : []),
  ])
  return [...runEnds, ...(status?.points ?? [])].sort(byTime)
}

function chartEmptyText(chartRange, running) {
  if (chartRange === 'all') {
    return 'No live runs yet. Start the bot to record the first one.'
  }
  if (running) {
    return 'The first point appears with the first live price.'
  }
  return 'Start the bot to see its portfolio value.'
}

function statusPill(status, running) {
  if (!running) {
    return { className: 'pill', text: 'Stopped' }
  }
  if (status.paused) {
    return { className: 'pill pill-paused', text: `Paused · run ${status.runId}` }
  }
  return { className: 'pill pill-on', text: `Running · run ${status.runId}` }
}

function Counts({ bot }) {
  const counts = [
    ['Buys', bot.buyCount],
    ['Sells', bot.sellCount],
    ['Stop-loss sells', bot.stopLossCount],
    ['Take-profit sells', bot.takeProfitCount],
    ['Refused', bot.refusedOrderCount],
  ]
  return (
    <dl className="counts">
      {counts.map(([label, count]) => (
        <div key={label}>
          <dt>{label}</dt>
          <dd>{count}</dd>
        </div>
      ))}
    </dl>
  )
}

/**
 * The live bot. Stopped: pick a ticker and settings, then start. Running: the settings are read
 * only until Edit; Save asks for confirmation before they reach the running bot.
 */
export function LiveBotPanel({ algorithms, tickers, mode, runs, saved, currentSettings }) {
  const bot = usePolling(api.getBot, BOT_REFRESH_MS)
  const [chartRange, setChartRange] = useState('all')
  const [ticker, setTicker] = useState('')
  const [draft, setDraft] = useState(null)
  const [editStep, setEditStep] = useState('readOnly')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState(null)

  const status = bot.data
  const running = status?.runId != null
  const chosenTicker = ticker || tickers?.[0]?.ticker || ''
  const startForm = draft ?? (algorithms?.[0] ? defaultSettings(algorithms[0]) : null)
  const chartPoints = chartRange === 'all' ? allLiveRunPoints(runs, status) : status?.points
  const pill = statusPill(status, running)
  const shownSettings =
    running && editStep === 'readOnly' ? settingsFromResponse(status.currentParameters) : draft

  const act = async (action) => {
    setBusy(true)
    try {
      await action()
      setError(null)
      await bot.refresh()
    } catch (failure) {
      setError(failure)
    } finally {
      setBusy(false)
    }
  }

  const start = () => act(() => api.startBot(chosenTicker, settingsToBody(startForm)))
  const stop = () =>
    act(async () => {
      await api.stopBot()
      setDraft(null)
      setEditStep('readOnly')
    })
  const togglePause = () => act(() => (status.paused ? api.resumeBot() : api.pauseBot()))
  const beginEdit = () => {
    setDraft(settingsFromResponse(status.currentParameters))
    setEditStep('editing')
    setError(null)
  }
  const cancelEdit = () => {
    setDraft(null)
    setEditStep('readOnly')
    setError(null)
  }
  const confirmEdit = () =>
    act(async () => {
      await api.updateBotParameters(settingsToBody(draft))
      setDraft(null)
      setEditStep('readOnly')
    })

  return (
    <div className="panel-grid">
      <section className="card">
        <div className="card-head">
          <h2>Live bot</h2>
          <span className={pill.className}>{pill.text}</span>
        </div>
        {mode !== 'TRADING' && !running && (
          <p className="notice">The live bot runs in Trading mode. Switch the mode at the top.</p>
        )}
        {running ? (
          <p className="muted small">
            Trading <strong>{status.ticker}</strong> on{' '}
            {timeframeLabel(status.currentParameters.timeframe)} candles. Parameters editable:{' '}
            {running ? 'yes' : 'no'}.
          </p>
        ) : (
          <div className="field">
            <span className="field-label">Ticker</span>
            <Dropdown ariaLabel="Ticker" value={chosenTicker} options={tickerOptions(tickers)} onChange={setTicker} />
          </div>
        )}
        <SettingsForm
          algorithms={algorithms}
          value={running ? shownSettings : startForm}
          onChange={setDraft}
          disabled={busy || (running && editStep !== 'editing')}
          saved={saved}
          current={currentSettings}
        />
        {running && status.paused && (
          <p className="notice">Paused: no orders are placed, not even a stop loss or take profit.</p>
        )}
        {editStep === 'confirm' && (
          <p className="notice">
            Apply these settings to the running bot? They are recorded and take effect from the
            next price.
          </p>
        )}
        {error && (
          <p className="form-error" role="alert">
            {error.message}
          </p>
        )}
        <div className="actions">
          {!running && (
            <button
              type="button"
              className="button button-primary"
              disabled={busy || mode !== 'TRADING' || !startForm || !chosenTicker}
              onClick={start}
            >
              Start bot
            </button>
          )}
          {running && editStep === 'readOnly' && (
            <>
              <button type="button" className="button" disabled={busy} onClick={beginEdit}>
                Edit parameters
              </button>
              <button type="button" className="button" disabled={busy} onClick={togglePause}>
                {status.paused ? 'Resume' : 'Pause'}
              </button>
              <button type="button" className="button button-danger" disabled={busy} onClick={stop}>
                Stop bot
              </button>
            </>
          )}
          {running && editStep === 'editing' && (
            <>
              <button
                type="button"
                className="button button-primary"
                onClick={() => setEditStep('confirm')}
              >
                Save
              </button>
              <button type="button" className="button" onClick={cancelEdit}>
                Cancel
              </button>
            </>
          )}
          {running && editStep === 'confirm' && (
            <>
              <button
                type="button"
                className="button button-primary"
                disabled={busy}
                onClick={confirmEdit}
              >
                Confirm
              </button>
              <button
                type="button"
                className="button"
                disabled={busy}
                onClick={() => setEditStep('editing')}
              >
                Back
              </button>
            </>
          )}
        </div>
      </section>
      <section className="card">
        <div className="card-head">
          <h2>How the bot is doing</h2>
          <div
            className={`segmented segmented-small ${chartRange === 'run' ? 'is-second' : ''}`}
            role="radiogroup"
            aria-label="Chart range"
          >
            <span className="segmented-thumb" aria-hidden="true" />
            {CHART_RANGES.map(({ id, label }) => (
              <button
                key={id}
                type="button"
                role="radio"
                aria-checked={chartRange === id}
                className={chartRange === id ? 'is-active' : ''}
                onClick={() => setChartRange(id)}
              >
                {label}
              </button>
            ))}
          </div>
        </div>
        <LineChart
          key={chartRange}
          points={chartPoints}
          baseline={chartPoints?.[0]?.value ?? null}
          emptyText={chartEmptyText(chartRange, running)}
        />
        {running && <Counts bot={status} />}
      </section>
    </div>
  )
}
