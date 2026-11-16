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
const RESETS_REFRESH_MS = 10000
const BINANCE_DEMO_WALLET_URL = 'https://demo.binance.com/en/my/wallet/demo/main'
const RESET_QUESTION =
  'Sell every coin in the demo account to USDT (USDC too) and close the open positions? ' +
  'Binance Demo then opens in a new tab, where you press Reset.'

const assetList = (balances) => balances.map((balance) => `${balance.free} ${balance.asset}`).join(', ')

function emptiedText(emptied) {
  const sold = emptied.soldBalances.length ? `Sold ${assetList(emptied.soldBalances)}.` : 'Nothing to sell.'
  const kept = emptied.keptBalances.length ? ` Kept ${assetList(emptied.keptBalances)}.` : ''
  return `${sold}${kept} Closed ${emptied.closedPositionCount} open positions. Reset the account on Binance now.`
}

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
  const resets = usePolling(api.getAccountResets, RESETS_REFRESH_MS)
  const [resetResult, setResetResult] = useState(null)
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
  // The tab opens before any await, or the browser blocks it as a popup; it gets Binance's page
  // only once the account is empty.
  const resetAccount = () => {
    if (!window.confirm(RESET_QUESTION)) {
      return
    }
    const binanceTab = window.open('', '_blank')
    act(async () => {
      try {
        setResetResult(emptiedText(await api.emptyAccount()))
        binanceTab?.location.assign(BINANCE_DEMO_WALLET_URL)
      } catch (failure) {
        binanceTab?.close()
        throw failure
      }
    })
  }
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
        {resetResult && !running && <p className="notice">{resetResult}</p>}
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
          {!running && (
            <button type="button" className="button" disabled={busy} onClick={resetAccount}>
              Reset account
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
          markers={(resets.data?.resetTimes ?? []).map((time) => ({ time, label: 'Reset' }))}
          emptyText={chartEmptyText(chartRange, running)}
        />
        {running && <Counts bot={status} />}
      </section>
    </div>
  )
}
