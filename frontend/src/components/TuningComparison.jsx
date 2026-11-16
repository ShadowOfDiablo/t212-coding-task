import './Tuning.css'
import { formatPrice, formatUtcDateTime } from '../format.js'
import { timeframeLabel } from '../settings.js'
import { useState } from 'react'
import { LineChart } from './LineChart.jsx'
import { RunTrades } from './RunTrades.jsx'
import { SaveTunedSettings } from './SaveTunedSettings.jsx'
import { SettingsPreview } from './SettingsPreview.jsx'

const PERCENT = 100

const returnPercent = (finalValue, startingValue) =>
  ((finalValue - startingValue) / startingValue) * PERCENT

function signedPercent(value) {
  const sign = value >= 0 ? '+' : ''
  return `${sign}${value.toFixed(2)}%`
}

/** The return from the first point at or after `checkFrom` to the end: data the tuner never saw. */
function checkReturnPercent(points, checkFrom, finalValue) {
  const checkStart = points?.find((point) => new Date(point.time) >= new Date(checkFrom))
  return checkStart ? returnPercent(finalValue, checkStart.value) : null
}

function returnCell(value) {
  if (value == null) {
    return <td className="muted">—</td>
  }
  return <td className={value >= 0 ? 'change-up' : 'change-down'}>{signedPercent(value)}</td>
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
 * The tuned run next to the old settings, both over the whole range; a marker shows where the data
 * the tuner never saw begins. The form keeps the old settings until Apply settings is clicked.
 */
export function TuningComparison({ tuning, oldSettings, checkFrom, applied, disabled, onApply, saved }) {
  const [previewOpen, setPreviewOpen] = useState(false)
  const { run, settings, points } = tuning.run
  const tunedReturn = returnPercent(run.finalValue, run.startingValue)
  const baseRun = tuning.baseRun
  const rows = [
    {
      name: 'Old settings',
      settings: oldSettings,
      finalValue: baseRun?.finalValue,
      checkReturn: baseRun && checkReturnPercent(baseRun.points, checkFrom, baseRun.finalValue),
      counts: baseRun,
    },
    {
      name: 'Tuned settings',
      settings,
      finalValue: run.finalValue,
      checkReturn: checkReturnPercent(points, checkFrom, run.finalValue),
      counts: run,
    },
  ]

  return (
    <div className="tuning-comparison">
      <div className={`apply-tuned ${applied ? 'is-kept' : ''}`}>
        <p className="small">
          {applied ? (
            <>
              <strong>Tuned settings applied.</strong> The form now holds them.
            </>
          ) : (
            <>
              <strong>Your settings are unchanged.</strong> Apply the tuned ones to put them in the
              form.
            </>
          )}
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
          <button
            type="button"
            className="button button-primary"
            disabled={disabled || applied}
            onClick={onApply}
          >
            {applied ? 'Applied' : 'Apply settings'}
          </button>
        </div>
      </div>
      <SaveTunedSettings
        algorithmId={run.algorithmId}
        settings={settings}
        defaultName={`Tuned ${run.ticker} ${timeframeLabel(settings.timeframe)}`}
        saved={saved}
        disabled={disabled}
      />
      {previewOpen && <SettingsPreview oldSettings={oldSettings} tunedSettings={settings} />}
      <p className="small">
        Both runs cover {formatUtcDateTime(run.from)} → {formatUtcDateTime(run.to)}. The tuner only
        saw the data before {formatUtcDateTime(checkFrom)}; the part after it is new to the settings.{' '}
        {tuning.trialCount} tries, best tune return {signedPercent(Number(tuning.bestTuneReturnPercent))}.
      </p>
      <table className="comparison-table small">
        <thead>
          <tr>
            <th scope="col">Run</th>
            <th scope="col">Final value</th>
            <th scope="col">Return</th>
            <th scope="col">Return on new data</th>
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
                <td colSpan="4" className="muted">
                  Not available
                </td>
              ) : (
                <>
                  <td>{formatPrice(row.finalValue)} USDT</td>
                  {returnCell(returnPercent(row.finalValue, run.startingValue))}
                  {returnCell(row.checkReturn)}
                  <td>
                    {row.counts.buyCount} / {row.counts.sellCount}
                  </td>
                </>
              )}
            </tr>
          ))}
        </tbody>
      </table>
      <LineChart
        points={points}
        comparePoints={baseRun?.points}
        baseline={run.startingValue}
        markers={[{ time: checkFrom, label: 'New data' }]}
      />
      <p className="muted small comparison-legend">
        <span className="legend-tuned">Tuned ({signedPercent(tunedReturn)})</span>
        <span className="legend-old">Old settings, dashed</span>
      </p>
      <RunTrades key={run.id} runId={run.id} />
    </div>
  )
}
