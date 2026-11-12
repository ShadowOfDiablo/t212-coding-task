import './Panels.css'
import { useState } from 'react'
import { api } from '../api.js'
import { usePolling } from '../hooks/usePolling.js'
import { defaultSettings, settingsFromResponse, settingsToBody, timeframeLabel } from '../settings.js'
import { LineChart } from './LineChart.jsx'
import { SettingsForm } from './SettingsForm.jsx'

const BOT_REFRESH_MS = 2000

function Counts({ bot }) {
  const counts = [
    ['Buys', bot.buyCount],
    ['Sells', bot.sellCount],
    ['Stop-loss sells', bot.stopLossCount],
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
export function LiveBotPanel({ algorithms, tickers, mode }) {
  const bot = usePolling(api.getBot, BOT_REFRESH_MS)
  const [ticker, setTicker] = useState('')
  const [draft, setDraft] = useState(null)
  const [editStep, setEditStep] = useState('readOnly')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState(null)

  const status = bot.data
  const running = status?.running ?? false
  const chosenTicker = ticker || tickers?.[0]?.ticker || ''
  const startForm = draft ?? (algorithms?.[0] ? defaultSettings(algorithms[0]) : null)
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
          <span className={`pill ${running ? 'pill-on' : ''}`}>
            {running ? `Running · run ${status.runId}` : 'Stopped'}
          </span>
        </div>
        {mode !== 'TRADING' && !running && (
          <p className="notice">The live bot runs in Trading mode. Switch the mode at the top.</p>
        )}
        {running ? (
          <p className="muted small">
            Trading <strong>{status.ticker}</strong> on{' '}
            {timeframeLabel(status.currentParameters.timeframe)} candles. Parameters editable:{' '}
            {status.areParametersEditable ? 'yes' : 'no'}.
          </p>
        ) : (
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
        )}
        <SettingsForm
          algorithms={algorithms}
          value={running ? shownSettings : startForm}
          onChange={setDraft}
          disabled={busy || (running && editStep !== 'editing')}
        />
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
          <span className="muted small">portfolio value, USDT</span>
        </div>
        <LineChart
          points={status?.points}
          baseline={status?.points?.[0]?.value ?? null}
          emptyText={
            running
              ? 'The first point appears with the first live price.'
              : 'Start the bot to see its portfolio value.'
          }
        />
        {running && <Counts bot={status} />}
      </section>
    </div>
  )
}
