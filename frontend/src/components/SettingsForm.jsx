import './SettingsForm.css'
import { defaultSettings, TIMEFRAMES, UPDATE_TIME_SECONDS } from '../settings.js'

function SettingField({ definition, value, disabled, onChange }) {
  const hint = definition.required
    ? `Required · ${definition.minimum}–${definition.maximum}`
    : `Optional, empty = off · ${definition.minimum}–${definition.maximum}`
  return (
    <label className="field">
      <span className="field-label">{definition.label}</span>
      <input
        type="number"
        inputMode="decimal"
        step={definition.wholeNumber ? 1 : 'any'}
        min={definition.minimum}
        max={definition.maximum}
        placeholder={definition.defaultValue == null ? 'off' : String(definition.defaultValue)}
        value={value ?? ''}
        disabled={disabled}
        onChange={(event) => onChange(definition.name, event.target.value)}
      />
      <span className="field-hint">{hint}</span>
    </label>
  )
}

/**
 * The settings of an algorithm, built from its setting definitions. `value` is the form state
 * (see settings.js); every change is passed up whole through `onChange`.
 */
export function SettingsForm({ algorithms, value, onChange, disabled = false }) {
  if (!algorithms || !value) {
    return [0, 1, 2].map((row) => <p key={row} className="skeleton skeleton-line" />)
  }
  const algorithm = algorithms.find((candidate) => candidate.id === value.algorithmId)
  const setValue = (name, text) => onChange({ ...value, values: { ...value.values, [name]: text } })
  const selectAlgorithm = (id) =>
    onChange(defaultSettings(algorithms.find((candidate) => candidate.id === id)))
  const definitions = (algorithm?.settings ?? []).filter(
    (definition) => definition.name !== UPDATE_TIME_SECONDS || !value.liveOnTick,
  )

  return (
    <fieldset className="settings-form" disabled={disabled}>
      <label className="field field-wide">
        <span className="field-label">Algorithm</span>
        <select value={value.algorithmId} onChange={(event) => selectAlgorithm(event.target.value)}>
          {algorithms.map((candidate) => (
            <option key={candidate.id} value={candidate.id}>
              {candidate.displayName}
            </option>
          ))}
        </select>
        <span className="field-hint">{value.algorithmId}</span>
      </label>
      <label className="field">
        <span className="field-label">Timeframe</span>
        <select
          value={value.timeframe}
          onChange={(event) => onChange({ ...value, timeframe: event.target.value })}
        >
          {TIMEFRAMES.map((timeframe) => (
            <option key={timeframe.id} value={timeframe.id}>
              {timeframe.label}
            </option>
          ))}
        </select>
        <span className="field-hint">Candle size</span>
      </label>
      <label className="field field-check">
        <input
          type="checkbox"
          checked={value.liveOnTick}
          onChange={(event) => onChange({ ...value, liveOnTick: event.target.checked })}
        />
        <span>
          <span className="field-label">Run on every price</span>
          <span className="field-hint">Off: run on a timer (live only)</span>
        </span>
      </label>
      {definitions.map((definition) => (
        <SettingField
          key={definition.name}
          definition={definition}
          value={value.values[definition.name]}
          disabled={disabled}
          onChange={setValue}
        />
      ))}
    </fieldset>
  )
}
