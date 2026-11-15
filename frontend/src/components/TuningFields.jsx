import './Tuning.css'
import { MAX_TRIALS } from '../tuning.js'

/** How the range is split for a tuning, and how many tries it gets. */
export function TuningFields({ value, onChange, disabled }) {
  if (!value) {
    return null
  }
  return (
    <fieldset className="tuning-fields" disabled={disabled}>
      <legend className="field-label">Tune automatically</legend>
      <p className="muted small">
        Fill the blue circle <span className="tune-toggle-sample" aria-hidden="true" /> beside each
        setting the tuner may change. The others keep their value.
      </p>
      <div className="tune-numbers">
        <label className="field">
          <span className="field-label">Tune on the first (% of the range)</span>
          <input
            type="number"
            min="1"
            max="99"
            step="1"
            value={value.tuneSharePercent}
            onChange={(event) => onChange({ ...value, tuneSharePercent: event.target.value })}
          />
          <span className="field-hint">The rest checks the best settings on unseen data</span>
        </label>
        <label className="field">
          <span className="field-label">Tries</span>
          <input
            type="number"
            min="1"
            max={MAX_TRIALS}
            step="1"
            value={value.maxTrials}
            onChange={(event) => onChange({ ...value, maxTrials: event.target.value })}
          />
          <span className="field-hint">1–{MAX_TRIALS}</span>
        </label>
      </div>
    </fieldset>
  )
}
