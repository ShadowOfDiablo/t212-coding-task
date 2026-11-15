import './Tuning.css'

/** The round checkbox beside a setting: filled means the tuner may change that setting. */
export function TuneToggle({ label, checked, disabled, onChange }) {
  return (
    <input
      type="checkbox"
      className="tune-toggle"
      aria-label={`Tune ${label} automatically`}
      title="Let the tuner change this setting"
      checked={checked}
      disabled={disabled}
      onChange={(event) => onChange(event.target.checked)}
    />
  )
}
