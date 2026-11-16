import './Panels.css'
import { useState } from 'react'
import { api } from '../api.js'
import { formatPrice, QUOTE_ASSET } from '../format.js'
import { usePolling } from '../hooks/usePolling.js'
import { defaultSettings, settingsFromResponse, settingsToBody, timeframeLabel } from '../settings.js'
import { LineChart } from './LineChart.jsx'
import { SettingsForm } from './SettingsForm.jsx'
import { BotList } from './livebots/BotList.jsx'
import { TimeRangePicker } from './timerange/TimeRangePicker.jsx'
import { DEFAULT_TIME_RANGE, pointsInRange } from './timerange/timeRanges.js'

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

/** Every live run of the ticker as its start and end value, then the running run's own points. */
function allLiveRunPoints(runs, ticker, status) {
  const finishedRuns = (runs ?? []).filter(
    (run) => run.mode !== 'TRAINING' && run.ticker === ticker && run.id !== status?.runId,
  )
  const runEnds = finishedRuns.flatMap((run) => [
    { time: run.startedAt, value: run.startingValue },
    ...(run.finishedAt && run.finalValue != null ? [{ time: run.finishedAt, value: run.finalValue }] : []),
  ])
  return [...runEnds, ...(status?.points ?? [])].sort(byTime)
}

function chartEmptyText(chartRange, timeRange, running) {
  if (timeRange !== DEFAULT_TIME_RANGE) {
    return 'Nothing recorded in this time range.'
  }
  if (chartRange === 'all') {
    return 'No live runs yet. Start the bot to record the first one.'
  }
  if (running) {
    return 'The first point appears with the first live price.'
  }
  return 'Start the bot to see its portfolio value.'
}

function storedForm(tickerSettings, algorithms) {
  if (tickerSettings?.settings) {
    return settingsFromResponse(tickerSettings.settings)
  }
  return algorithms?.[0] ? defaultSettings(algorithms[0]) : null
}

const budgetToBody = (budget) => (budget === '' || budget == null ? null : Number(budget))

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
 * One live bot per followed ticker. A stopped ticker's settings and budget are saved for its next
 * start; a running bot's settings are read only until Edit, and Save asks for confirmation.
 */
export function LiveBotPanel({ algorithms, mode, runs, saved, currentSettings }) {
  const tickerSettings = usePolling(api.getTickerSettings, BOT_REFRESH_MS)
  const bots = usePolling(api.getBots, BOT_REFRESH_MS)
  const resets = usePolling(api.getAccountResets, RESETS_REFRESH_MS)
  const [resetResult, setResetResult] = useState(null)
  const [chartRange, setChartRange] = useState('all')
  const [timeRange, setTimeRange] = useState(DEFAULT_TIME_RANGE)
  const [ticker, setTicker] = useState('')
  const [draft, setDraft] = useState(null)
  const [budgetDraft, setBudgetDraft] = useState(null)
  const [editStep, setEditStep] = useState('readOnly')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState(null)

  const tickerRows = tickerSettings.data?.tickers ?? []
  const runningBots = bots.data?.bots ?? []
  const chosenTicker = ticker || tickerRows[0]?.ticker || ''
  const chosenSettings = tickerRows.find((row) => row.ticker === chosenTicker)
  const status = runningBots.find((bot) => bot.ticker === chosenTicker)
  const running = status != null
  const anyRunning = runningBots.length > 0
  const trading = mode === 'TRADING'
  const stoppedForm = draft ?? storedForm(chosenSettings, algorithms)
  const budget = budgetDraft ?? chosenSettings?.budget ?? ''
  const chartPoints = pointsInRange(
    chartRange === 'all' ? allLiveRunPoints(runs, chosenTicker, status) : status?.points,
    timeRange,
  )
  const shownSettings =
    running && editStep === 'readOnly' ? settingsFromResponse(status.currentParameters) : draft

  const clearDrafts = () => {
    setDraft(null)
    setBudgetDraft(null)
    setEditStep('readOnly')
  }

  const act = async (action) => {
    setBusy(true)
    try {
      await action()
      setError(null)
      await Promise.all([bots.refresh(), tickerSettings.refresh()])
    } catch (failure) {
      setError(failure)
    } finally {
      setBusy(false)
    }
  }

  const selectTicker = (nextTicker) => {
    setTicker(nextTicker)
    clearDrafts()
    setError(null)
  }
  const saveSettings = () =>
    act(async () => {
      await api.assignTickerSettings(chosenTicker, settingsToBody(stoppedForm), budgetToBody(budget))
      clearDrafts()
    })
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
    clearDrafts()
    setError(null)
  }
  const confirmEdit = () =>
    act(async () => {
      await api.updateBotParameters(chosenTicker, settingsToBody(draft))
      clearDrafts()
    })

  return (
    <div className="panel-grid">
      <section className="card">
        <div className="card-head">
          <h2>Live bots</h2>
          <div className="actions">
            <button
              type="button"
              className="button button-primary"
              disabled={busy || !trading}
              onClick={() => act(api.startAllBots)}
            >
              Start all
            </button>
            <button
              type="button"
              className="button button-danger"
              disabled={busy || !anyRunning}
              onClick={() => act(api.stopAllBots)}
            >
              Stop all
            </button>
          </div>
        </div>
        {!trading && !anyRunning && (
          <p className="notice">The live bots run in Trading mode. Switch the mode at the top.</p>
        )}
        {tickerRows.length === 0 && <p className="muted small">Follow a ticker to give it a bot.</p>}
        <BotList
          tickers={tickerRows}
          bots={runningBots}
          selectedTicker={chosenTicker}
          onSelect={selectTicker}
          onAct={act}
          busy={busy}
          canStart={trading}
        />
        {chosenTicker && (
          <>
            {running ? (
              <p className="muted small">
                Trading on {timeframeLabel(status.currentParameters.timeframe)} candles with its own{' '}
                {QUOTE_ASSET}.
              </p>
            ) : (
              <div className="field">
                <label className="field-label" htmlFor="live-bot-budget">
                  Budget ({QUOTE_ASSET})
                </label>
                <input
                  id="live-bot-budget"
                  type="number"
                  min="0"
                  step="any"
                  value={budget}
                  disabled={busy}
                  onChange={(event) => setBudgetDraft(event.target.value)}
                />
              </div>
            )}
            {running && chosenSettings?.budget != null && (
              <p className="muted small">
                Budget at start: {formatPrice(chosenSettings.budget)} {QUOTE_ASSET}
              </p>
            )}
            <SettingsForm
              algorithms={algorithms}
              value={running ? shownSettings : stoppedForm}
              onChange={setDraft}
              disabled={busy || (running && editStep !== 'editing')}
              saved={saved}
              current={currentSettings}
            />
          </>
        )}
        {running && status.paused && (
          <p className="notice">Paused: no orders are placed, not even a stop loss or take profit.</p>
        )}
        {resetResult && !anyRunning && <p className="notice">{resetResult}</p>}
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
          {chosenTicker && !running && (
            <button
              type="button"
              className="button button-primary"
              disabled={busy || !stoppedForm}
              onClick={saveSettings}
            >
              Save settings
            </button>
          )}
          {!anyRunning && (
            <button type="button" className="button" disabled={busy} onClick={resetAccount}>
              Reset account
            </button>
          )}
          {running && editStep === 'readOnly' && (
            <button type="button" className="button" disabled={busy} onClick={beginEdit}>
              Edit parameters
            </button>
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
          <h2>How {chosenTicker || 'the bot'} is doing</h2>
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
          key={`${chosenTicker}-${chartRange}-${timeRange}`}
          points={chartPoints}
          baseline={chartPoints?.[0]?.value ?? null}
          markers={(resets.data?.resetTimes ?? []).map((time) => ({ time, label: 'Reset' }))}
          emptyText={chartEmptyText(chartRange, timeRange, running)}
        />
        <TimeRangePicker value={timeRange} onChange={setTimeRange} />
        {running && <Counts bot={status} />}
      </section>
    </div>
  )
}
