import './BotList.css'
import { api } from '../../api.js'
import { formatPrice, QUOTE_ASSET } from '../../format.js'
import { settingsSummary } from '../../settings.js'

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

/** One row per followed ticker with its bot's state and controls; a click on a row selects it. */
export function BotList({ tickers, bots, selectedTicker, onSelect, onAct, busy, canStart }) {
  const botOf = (ticker) => bots.find((bot) => bot.ticker === ticker)
  const runAction = (event, action) => {
    event.stopPropagation()
    onAct(action)
  }

  return (
    <ul className="bot-list">
      {tickers.map((tickerSettings) => {
        const { ticker, settings } = tickerSettings
        const bot = botOf(ticker)
        const pill = botPill(bot)
        return (
          <li
            key={ticker}
            className={ticker === selectedTicker ? 'is-selected' : ''}
            onClick={() => onSelect(ticker)}
          >
            <div className="bot-list-head">
              <strong>{ticker}</strong>
              <span className={pill.className}>{pill.text}</span>
            </div>
            <span className="muted small">
              {budgetText(tickerSettings)} · {settings ? settingsSummary(settings) : 'No settings'}
            </span>
            <div className="bot-list-actions">
              {!bot && (
                <button
                  type="button"
                  className="button button-primary"
                  disabled={busy || !canStart || !settings}
                  onClick={(event) => runAction(event, () => api.startBot(ticker))}
                >
                  Start
                </button>
              )}
              {bot && (
                <>
                  <button
                    type="button"
                    className="button"
                    disabled={busy}
                    onClick={(event) => runAction(event, () => togglePause(bot))}
                  >
                    {bot.paused ? 'Resume' : 'Pause'}
                  </button>
                  <button
                    type="button"
                    className="button button-danger"
                    disabled={busy}
                    onClick={(event) => runAction(event, () => api.stopBot(ticker))}
                  >
                    Stop
                  </button>
                </>
              )}
            </div>
          </li>
        )
      })}
    </ul>
  )
}
