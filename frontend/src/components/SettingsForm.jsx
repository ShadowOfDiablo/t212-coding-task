import './SettingsForm.css'
import { useId } from 'react'
import { defaultSettings, TIMEFRAME_OPTIONS, UPDATE_TIME_SECONDS } from '../settings.js'
import { Dropdown } from './Dropdown.jsx'
import { TuneToggle } from './TuneToggle.jsx'

const TIMEFRAME = 'timeframe'

function FieldLabel({ inputId, label, tuning, name }) {
  return (
    <span className="field-label-row">
      <label className="field-label" htmlFor={inputId}>
        {label}
      </label>
      {tuning && (
        <TuneToggle
          label={label}
          checked={tuning.isTuned(name)}
          disabled={tuning.disabled}
          onChange={(checked) => tuning.onToggle(name, checked)}
        />
      )}
    </span>
  )
}

function SettingField({ definition, value, disabled, onChange, tuning }) {
  const inputId = useId()
  const hint = definition.required
    ? `Required · ${definition.minimum}–${definition.maximum}`
    : `Optional, empty = off · ${definition.minimum}–${definition.maximum}`
  return (
    <div className="field">
      <FieldLabel inputId={inputId} label={definition.label} tuning={tuning} name={definition.name} />
      <input
        id={inputId}
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
    </div>
  )
}

/**
 * The settings of an algorithm, built from its setting definitions. `value` is the form state
 * (see settings.js); every change is passed up whole through `onChange`. With `tuning`
 * ({ isTuned, onToggle, disabled }), each tunable setting gets a round checkbox beside its label.
 */
export function SettingsForm({ algorithms, value, onChange, disabled = false, tuning = null }) {
  const timeframeId = useId()
  const algorithmId = useId()
  if (!algorithms || !value) {
    return [0, 1, 2].map((row) => <p key={row} className="skeleton skeleton-line" />)
  }
  const algorithm = algorithms.find((candidate) => candidate.type === value.algorithmType)
  const setValue = (name, text) => onChange({ ...value, values: { ...value.values, [name]: text } })
  const selectAlgorithm = (type) =>
    onChange(defaultSettings(algorithms.find((candidate) => candidate.type === type)))
  const definitions = (algorithm?.settings ?? []).filter(
    (definition) => definition.name !== UPDATE_TIME_SECONDS || !value.liveOnTick,
  )
  const tuningFor = (definition) => (definition.name === UPDATE_TIME_SECONDS ? null : tuning)

  return (
    <fieldset className="settings-form" disabled={disabled}>
      <div className="field field-wide">
        <label className="field-label" htmlFor={algorithmId}>
          Algorithm
        </label>
        <Dropdown
          id={algorithmId}
          value={value.algorithmType}
          options={algorithms.map((candidate) => ({ value: candidate.type, label: candidate.displayName }))}
          onChange={selectAlgorithm}
          disabled={disabled}
        />
        <span className="field-hint">{algorithm?.name}</span>
      </div>
      <div className="field">
        <FieldLabel inputId={timeframeId} label="Timeframe" tuning={tuning} name={TIMEFRAME} />
        <Dropdown
          id={timeframeId}
          value={value.timeframe}
          options={TIMEFRAME_OPTIONS}
          onChange={(timeframe) => onChange({ ...value, timeframe })}
          disabled={disabled}
        />
        <span className="field-hint">Candle size</span>
      </div>
      <label className="field field-check">
        <input
          type="checkbox"
          className="switch"
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
          tuning={tuningFor(definition)}
        />
      ))}
    </fieldset>
  )
}
