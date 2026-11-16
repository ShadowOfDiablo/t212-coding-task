import './Dropdown.css'
import { useId, useRef, useState } from 'react'
import { useDismiss } from '../hooks/useDismiss.js'

const CLOSED = -1

function Chevron() {
  return (
    <svg className="dropdown-chevron" viewBox="0 0 16 16" aria-hidden="true">
      <path d="M4 6l4 4 4-4" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
    </svg>
  )
}

/**
 * A select with an animated list. `options` are `{ value, label, hint? }`; arrow keys move the
 * highlight, Enter or Space picks it, Escape closes. Inside a disabled fieldset it is disabled too.
 */
export function Dropdown({ value, options, onChange, id, disabled = false, ariaLabel, compact = false }) {
  const [activeIndex, setActiveIndex] = useState(CLOSED)
  const rootRef = useRef(null)
  const listId = useId()
  const open = activeIndex !== CLOSED
  const selectedIndex = Math.max(
    0,
    options.findIndex((option) => option.value === value),
  )
  const selected = options[selectedIndex]

  const close = () => setActiveIndex(CLOSED)
  useDismiss(rootRef, open, close)

  const pick = (option) => {
    onChange(option.value)
    close()
  }

  const onKeyDown = (event) => {
    if (event.key === 'Escape') {
      close()
      return
    }
    if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
      event.preventDefault()
      const step = event.key === 'ArrowDown' ? 1 : -1
      const from = open ? activeIndex : selectedIndex
      setActiveIndex((from + step + options.length) % options.length)
      return
    }
    if ((event.key === 'Enter' || event.key === ' ') && open) {
      event.preventDefault()
      pick(options[activeIndex])
    }
  }

  return (
    <div ref={rootRef} className={`dropdown ${open ? 'is-open' : ''} ${compact ? 'is-compact' : ''}`}>
      <button
        id={id}
        type="button"
        className="dropdown-trigger"
        aria-haspopup="listbox"
        aria-expanded={open}
        aria-controls={listId}
        aria-label={ariaLabel}
        disabled={disabled}
        onClick={() => setActiveIndex(open ? CLOSED : selectedIndex)}
        onKeyDown={onKeyDown}
      >
        <span className="dropdown-value">{selected?.label ?? '—'}</span>
        <Chevron />
      </button>
      <ul id={listId} className="dropdown-list" role="listbox" aria-label={ariaLabel}>
        {options.map((option, index) => (
          <li
            key={option.value}
            role="option"
            aria-selected={option.value === value}
            className={`dropdown-option ${index === activeIndex ? 'is-active' : ''}`}
            onPointerEnter={() => setActiveIndex(index)}
            onClick={() => pick(option)}
          >
            <span>{option.label}</span>
            {option.hint && <span className="dropdown-hint">{option.hint}</span>}
          </li>
        ))}
      </ul>
    </div>
  )
}
