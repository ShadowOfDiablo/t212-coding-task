import './TimeRangePicker.css'
import { TIME_RANGES } from './timeRanges.js'

export function TimeRangePicker({ value, onChange }) {
  return (
    <div className="time-range-picker" role="radiogroup" aria-label="Time range">
      {TIME_RANGES.map(({ id }) => (
        <button
          key={id}
          type="button"
          role="radio"
          aria-checked={value === id}
          className={value === id ? 'is-active' : ''}
          onClick={() => onChange(id)}
        >
          {id}
        </button>
      ))}
    </div>
  )
}
