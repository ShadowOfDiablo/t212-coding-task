import './DateTimePicker.css'
import { useRef, useState } from 'react'
import { useDismiss } from '../hooks/useDismiss.js'
import { Dropdown } from './Dropdown.jsx'

const WEEKDAYS = ['Mo', 'Tu', 'We', 'Th', 'Fr', 'Sa', 'Su']
const DAYS_SHOWN = 42
const DAYS_PER_WEEK = 7
const MONTHS_PER_YEAR = 12
const HOURS_PER_DAY = 24
const MINUTE_STEP = 5
const MINUTES_PER_HOUR = 60
const MONDAY_OFFSET = 6

const monthTitle = new Intl.DateTimeFormat('en-GB', { month: 'long', year: 'numeric', timeZone: 'UTC' })
const triggerFormat = new Intl.DateTimeFormat('en-GB', {
  day: '2-digit',
  month: 'short',
  year: 'numeric',
  timeZone: 'UTC',
})

const pad = (number) => String(number).padStart(2, '0')

/** "2021-01-01T00:00" as UTC parts; the picker never touches the local time zone. */
function parseValue(value) {
  const [date, time] = value.split('T')
  const [year, month, day] = date.split('-').map(Number)
  const [hour, minute] = time.split(':').map(Number)
  return { year, month: month - 1, day, hour, minute }
}

function formatValue({ year, month, day, hour, minute }) {
  return `${year}-${pad(month + 1)}-${pad(day)}T${pad(hour)}:${pad(minute)}`
}

function calendarDays(year, month) {
  const first = new Date(Date.UTC(year, month, 1))
  const leading = (first.getUTCDay() + MONDAY_OFFSET) % DAYS_PER_WEEK
  return Array.from({ length: DAYS_SHOWN }, (_, index) => new Date(Date.UTC(year, month, index - leading + 1)))
}

const numberOptions = (count, step) =>
  Array.from({ length: count / step }, (_, index) => ({ value: index * step, label: pad(index * step) }))

const HOUR_OPTIONS = numberOptions(HOURS_PER_DAY, 1)
const MINUTE_OPTIONS = numberOptions(MINUTES_PER_HOUR, MINUTE_STEP)

function CalendarIcon() {
  return (
    <svg className="picker-icon" viewBox="0 0 20 20" aria-hidden="true">
      <rect x="3" y="4.5" width="14" height="12" rx="2.5" fill="none" stroke="currentColor" strokeWidth="1.6" />
      <path d="M3 8.5h14M7 3v3M13 3v3" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" />
    </svg>
  )
}

function Arrow({ direction }) {
  const path = direction === 'left' ? 'M10 4L6 8l4 4' : 'M6 4l4 4-4 4'
  return (
    <svg viewBox="0 0 16 16" aria-hidden="true">
      <path d={path} fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
    </svg>
  )
}

/** A UTC date and time picker. `value` and `onChange` use the "YYYY-MM-DDTHH:mm" form. */
export function DateTimePicker({ value, onChange, id, disabled = false }) {
  const parts = parseValue(value)
  const [open, setOpen] = useState(false)
  const [shown, setShown] = useState({ year: parts.year, month: parts.month })
  const rootRef = useRef(null)

  useDismiss(rootRef, open, () => setOpen(false))

  const toggle = () => {
    setShown({ year: parts.year, month: parts.month })
    setOpen(!open)
  }
  const moveMonth = (step) => {
    const next = new Date(Date.UTC(shown.year, shown.month + step, 1))
    setShown({ year: next.getUTCFullYear(), month: next.getUTCMonth() })
  }
  const pickDay = (date) =>
    onChange(
      formatValue({ ...parts, year: date.getUTCFullYear(), month: date.getUTCMonth(), day: date.getUTCDate() }),
    )
  const isSelected = (date) =>
    date.getUTCFullYear() === parts.year && date.getUTCMonth() === parts.month && date.getUTCDate() === parts.day
  const [today] = useState(() => new Date())
  const isToday = (date) =>
    date.getUTCFullYear() === today.getUTCFullYear() &&
    date.getUTCMonth() === today.getUTCMonth() &&
    date.getUTCDate() === today.getUTCDate()
  const selectedDate = new Date(Date.UTC(parts.year, parts.month, parts.day))

  return (
    <div ref={rootRef} className={`picker ${open ? 'is-open' : ''}`}>
      <button
        id={id}
        type="button"
        className="picker-trigger"
        aria-haspopup="dialog"
        aria-expanded={open}
        disabled={disabled}
        onClick={toggle}
      >
        <CalendarIcon />
        <span className="picker-date">{triggerFormat.format(selectedDate)}</span>
        <span className="picker-time">
          {pad(parts.hour)}:{pad(parts.minute)}
        </span>
      </button>
      <div className="picker-popover" role="dialog" aria-label="Choose a date and time (UTC)">
        <div className="picker-head">
          <button type="button" className="picker-nav" aria-label="Previous year" onClick={() => moveMonth(-MONTHS_PER_YEAR)}>
            <Arrow direction="left" />
            <Arrow direction="left" />
          </button>
          <button type="button" className="picker-nav" aria-label="Previous month" onClick={() => moveMonth(-1)}>
            <Arrow direction="left" />
          </button>
          <strong key={`${shown.year}-${shown.month}`} className="picker-title">
            {monthTitle.format(new Date(Date.UTC(shown.year, shown.month, 1)))}
          </strong>
          <button type="button" className="picker-nav" aria-label="Next month" onClick={() => moveMonth(1)}>
            <Arrow direction="right" />
          </button>
          <button type="button" className="picker-nav" aria-label="Next year" onClick={() => moveMonth(MONTHS_PER_YEAR)}>
            <Arrow direction="right" />
            <Arrow direction="right" />
          </button>
        </div>
        <div className="picker-grid" key={`${shown.year}-${shown.month}`}>
          {WEEKDAYS.map((weekday) => (
            <span key={weekday} className="picker-weekday">
              {weekday}
            </span>
          ))}
          {calendarDays(shown.year, shown.month).map((date) => (
            <button
              key={date.toISOString()}
              type="button"
              className={[
                'picker-day',
                date.getUTCMonth() === shown.month ? '' : 'is-outside',
                isSelected(date) ? 'is-selected' : '',
                isToday(date) ? 'is-today' : '',
              ].join(' ')}
              onClick={() => pickDay(date)}
            >
              {date.getUTCDate()}
            </button>
          ))}
        </div>
        <div className="picker-foot">
          <span className="field-label">Time (UTC)</span>
          <div className="picker-clock">
            <Dropdown
              compact
              ariaLabel="Hour"
              value={parts.hour}
              options={HOUR_OPTIONS}
              onChange={(hour) => onChange(formatValue({ ...parts, hour }))}
            />
            <span className="picker-colon">:</span>
            <Dropdown
              compact
              ariaLabel="Minute"
              value={parts.minute}
              options={MINUTE_OPTIONS}
              onChange={(minute) => onChange(formatValue({ ...parts, minute }))}
            />
          </div>
          <button type="button" className="button button-primary picker-done" onClick={() => setOpen(false)}>
            Done
          </button>
        </div>
      </div>
    </div>
  )
}
