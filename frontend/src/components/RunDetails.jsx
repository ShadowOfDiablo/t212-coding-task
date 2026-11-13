import { formatPrice, formatTime } from '../format.js'
import { timeframeLabel } from '../settings.js'
import { LineChart } from './LineChart.jsx'

function SettingsSummary({ settings }) {
  const entries = [
    settings.algorithmType,
    timeframeLabel(settings.timeframe),
    `period ${settings.timePeriod}`,
    settings.liveOnTick ? 'every price' : `every ${settings.updateTimeSeconds}s`,
    `buy ${settings.orderSizePercent}% of cash`,
    settings.riskPerTradePercent == null
      ? 'no risk limit'
      : `risk ${settings.riskPerTradePercent}% · stop ${settings.stopLossPercent}%`,
    ...Object.entries(settings.options ?? {}).map(([name, value]) => `${name} ${value}`),
  ]
  return (
    <ul className="settings-summary">
      {entries.map((entry) => (
        <li key={entry}>{entry}</li>
      ))}
    </ul>
  )
}

export function RunDetails({ details }) {
  const { run, settings, points } = details
  const result =
    run.finalValue == null ? null : ((run.finalValue - run.startingValue) / run.startingValue) * 100
  return (
    <div className="run-details">
      <p className="small">
        <strong>
          {run.mode === 'TRAINING' ? 'Training' : 'Live'} run {run.id}
        </strong>{' '}
        · {run.ticker} · {run.status}
        {run.from && (
          <span className="muted">
            {' '}
            · {formatTime(run.from)} → {formatTime(run.to)}
          </span>
        )}
      </p>
      <p className="small">
        {formatPrice(run.startingValue)} → {formatPrice(run.finalValue)} USDT
        {result != null && (
          <span className={result >= 0 ? 'change-up' : 'change-down'}>
            {' '}
            ({result >= 0 ? '+' : ''}
            {result.toFixed(2)}%)
          </span>
        )}
        <span className="muted">
          {' '}
          · {run.buyCount} buys · {run.sellCount} sells · {run.stopLossCount} stop-loss ·{' '}
          {run.refusedOrderCount} refused
        </span>
      </p>
      {run.failureReason && <p className="form-error">{run.failureReason}</p>}
      <LineChart
        points={points}
        baseline={run.startingValue}
        emptyText="This run has no chart points."
      />
      <p className="muted small">Algorithm {run.algorithmId}</p>
      <SettingsSummary settings={settings} />
    </div>
  )
}
