import './Tuning.css'
import { formatPrice, formatUtcDateTime } from '../format.js'
import { timeframeLabel } from '../settings.js'
import { useState } from 'react'
import { LineChart } from './LineChart.jsx'
import { RunTrades } from './RunTrades.jsx'
import { SettingsPreview } from './SettingsPreview.jsx'

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

/**
 * The tuned run next to the old settings, both on the same check range. The form keeps the old
 * settings until Apply settings is clicked.
 */
export function TuningComparison({ tuning, oldSettings, applied, disabled, onApply }) {
  const [previewOpen, setPreviewOpen] = useState(false)
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
      <div className="apply-tuned">
        {applied ? (
          <p className="small apply-tuned-done">
            <strong>Tuned settings applied.</strong> The form now holds them; run a training to use them.
          </p>
        ) : (
          <>
            <p className="small">
              <strong>Your settings are unchanged.</strong> Apply the tuned ones to put them in the form.
            </p>
            <div className="apply-tuned-actions">
              <button
                type="button"
                className="button"
                aria-expanded={previewOpen}
                onClick={() => setPreviewOpen(!previewOpen)}
              >
                {previewOpen ? 'Hide preview' : 'Preview'}
              </button>
              <button type="button" className="button button-primary" disabled={disabled} onClick={onApply}>
                Apply settings
              </button>
            </div>
          </>
        )}
      </div>
      {previewOpen && !applied && <SettingsPreview oldSettings={oldSettings} tunedSettings={settings} />}
      <p className="small">
        Both runs cover {formatUtcDateTime(run.from)} →{' '}
        {formatUtcDateTime(run.to)}, which the tuner never saw. {tuning.trialCount} tries, best tune return{' '}
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
        <span className="legend-tuned">Tuned ({signedPercent(tunedReturn)})</span>
        <span className="legend-old">Old settings, dashed</span>
      </p>
      <RunTrades key={run.id} runId={run.id} />
    </div>
  )
}
