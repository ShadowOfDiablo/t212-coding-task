import { timeframeLabel } from '../settings.js'

const OFF = 'off'

const percent = (value) => (value == null ? OFF : `${Number(value)}%`)

const SHARED_ROWS = [
  { label: 'Timeframe', show: (settings) => timeframeLabel(settings.timeframe) },
  { label: 'Time period', show: (settings) => `${settings.timePeriod} candles` },
  { label: 'Each buy spends', show: (settings) => `${percent(settings.orderSizePercent)} of cash` },
  { label: 'Stop loss', show: (settings) => percent(settings.stopLossPercent) },
  { label: 'Take profit', show: (settings) => percent(settings.takeProfitPercent) },
]

function optionRows(oldSettings, tunedSettings) {
  const names = new Set([...Object.keys(oldSettings.options ?? {}), ...Object.keys(tunedSettings.options ?? {})])
  return [...names].map((name) => ({ label: name, show: (settings) => String(settings.options?.[name] ?? OFF) }))
}

/** Each setting as it is now and as the tuning would set it; changed ones are highlighted. */
export function SettingsPreview({ oldSettings, tunedSettings }) {
  const rows = [...SHARED_ROWS, ...optionRows(oldSettings, tunedSettings)].map(({ label, show }) => ({
    label,
    before: show(oldSettings),
    after: show(tunedSettings),
  }))
  const changedCount = rows.filter((row) => row.before !== row.after).length

  return (
    <div className="settings-preview">
      <p className="muted small">
        {changedCount === 0 ? 'The tuning kept every setting.' : `${changedCount} of ${rows.length} settings change.`}
      </p>
      <table className="preview-table small">
        <thead>
          <tr>
            <th scope="col">Setting</th>
            <th scope="col">Yours</th>
            <th scope="col" aria-hidden="true" />
            <th scope="col">Tuned</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <tr key={row.label} className={row.before === row.after ? '' : 'is-changed'}>
              <th scope="row">{row.label}</th>
              <td className="preview-before">{row.before}</td>
              <td className="preview-arrow" aria-hidden="true">
                →
              </td>
              <td className="preview-after">{row.after}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
