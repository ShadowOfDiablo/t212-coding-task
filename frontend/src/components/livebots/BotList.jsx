import './BotList.css'
import { api } from '../../api.js'
import { formatPrice, QUOTE_ASSET } from '../../format.js'
import { settingsSummary } from '../../settings.js'
import { Dropdown } from '../Dropdown.jsx'

const togglePause = (bot) => (bot.paused ? api.resumeBot(bot.ticker) : api.pauseBot(bot.ticker))

function botPill(bot) {
  if (!bot) {
    return { className: 'pill', text: 'Stopped' }
  }
  if (bot.paused) {
    return { className: 'pill pill-paused', text: `Paused · run ${bot.runId}` }
  }
  return { className: 'pill pill-on', text: `Running · run ${bot.runId}` }
}

function budgetText(tickerSettings) {
  if (tickerSettings.budget == null) {
    return 'No budget'
  }
  return `${formatPrice(tickerSettings.budget)} ${QUOTE_ASSET}`
}

function statusText(bot) {
  if (!bot) {
    return 'stopped'
  }
  return bot.paused ? 'paused' : 'running'
}

/** A dropdown of every followed ticker's bot, and the chosen one's state and controls below it. */
export function BotList({ tickers, bots, selectedTicker, onSelect, onAct, busy, canStart }) {
  const botOf = (ticker) => bots.find((bot) => bot.ticker === ticker)
  const options = tickers.map(({ ticker }) => ({
    value: ticker,
    label: `${ticker} · ${statusText(botOf(ticker))}`,
  }))
  const chosen = tickers.find((tickerSettings) => tickerSettings.ticker === selectedTicker)
  if (!chosen) {
    return null
  }
  const { ticker, settings } = chosen
  const bot = botOf(ticker)
  const pill = botPill(bot)

  return (
    <div className="bot-list">
      <Dropdown ariaLabel="Live bot" value={ticker} options={options} onChange={onSelect} />
      <div className="bot-card">
        <div className="bot-list-head">
          <strong>{ticker}</strong>
          <span className={pill.className}>{pill.text}</span>
        </div>
        <span className="muted small">
          {budgetText(chosen)} · {settings ? settingsSummary(settings) : 'No settings'}
        </span>
        <div className="bot-list-actions">
          {!bot && (
            <button
              type="button"
              className="button button-primary"
              disabled={busy || !canStart || !settings}
              onClick={() => onAct(() => api.startBot(ticker))}
            >
              Start
            </button>
          )}
          {bot && (
            <>
              <button type="button" className="button" disabled={busy} onClick={() => onAct(() => togglePause(bot))}>
                {bot.paused ? 'Resume' : 'Pause'}
              </button>
              <button
                type="button"
                className="button button-danger"
                disabled={busy}
                onClick={() => onAct(() => api.stopBot(ticker))}
              >
                Stop
              </button>
            </>
          )}
        </div>
      </div>
    </div>
  )
}
