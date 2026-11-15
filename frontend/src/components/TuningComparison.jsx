import './Tuning.css'
import { formatPrice, formatTime } from '../format.js'
import { timeframeLabel } from '../settings.js'
import { LineChart } from './LineChart.jsx'

const PERCENT = 100

const returnPercent = (finalValue, startingValue) =>
  ((finalValue - startingValue) / startingValue) * PERCENT

function signedPercent(value) {
  const sign = value >= 0 ? '+' : ''
  return `${sign}${value.toFixed(2)}%`
}

function settingsText(settings) {
  const parts = [timeframeLabel(settings.timeframe), `period ${settings.timePeriod}`, `buy ${settings.orderSizePercent}%`]
  if (settings.stopLossPercent != null) {
    parts.push(`stop loss ${settings.stopLossPercent}%`)
  }
  if (settings.takeProfitPercent != null) {
    parts.push(`take profit ${settings.takeProfitPercent}%`)
  }
  return parts.join(' · ')
}

/** The tuned run next to the old settings, both on the same check range. */
export function TuningComparison({ tuning, oldSettings }) {
  const { run, settings, points } = tuning.run
  const tunedReturn = returnPercent(run.finalValue, run.startingValue)
  const baseRun = tuning.baseRun
  const rows = [
    {
      name: 'Old settings',
      settings: oldSettings,
      finalValue: baseRun?.finalValue,
      counts: baseRun,
    },
    { name: 'Tuned settings', settings, finalValue: run.finalValue, counts: run },
  ]

  return (
    <div className="tuning-comparison">
      <p className="small">
        <strong>Tuned settings applied.</strong> Both runs cover {formatTime(run.from)} →{' '}
        {formatTime(run.to)}, which the tuner never saw. {tuning.trialCount} tries, best tune return{' '}
        {signedPercent(Number(tuning.bestTuneReturnPercent))}.
      </p>
      <table className="comparison-table small">
        <thead>
          <tr>
            <th scope="col">Run</th>
            <th scope="col">Final value</th>
            <th scope="col">Return</th>
            <th scope="col">Buys / sells</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <tr key={row.name}>
              <th scope="row">
                {row.name}
                <span className="muted comparison-settings">{settingsText(row.settings)}</span>
              </th>
              {row.finalValue == null ? (
                <td colSpan="3" className="muted">
                  Not available
                </td>
              ) : (
                <>
                  <td>{formatPrice(row.finalValue)} USDT</td>
                  <td className={returnPercent(row.finalValue, run.startingValue) >= 0 ? 'change-up' : 'change-down'}>
                    {signedPercent(returnPercent(row.finalValue, run.startingValue))}
                  </td>
                  <td>
                    {row.counts.buyCount} / {row.counts.sellCount}
                  </td>
                </>
              )}
            </tr>
          ))}
        </tbody>
      </table>
      <LineChart points={points} comparePoints={baseRun?.points} baseline={run.startingValue} />
      <p className="muted small comparison-legend">
        <span className={`legend-tuned ${tunedReturn < 0 ? 'legend-down' : ''}`}>Tuned ({signedPercent(tunedReturn)})</span>
        <span className="legend-old">Old settings, dashed</span>
      </p>
    </div>
  )
}
