import './SavedSettingsPicker.css'
import { useRef, useState } from 'react'
import { useDismiss } from '../hooks/useDismiss.js'
import { formMatchesSettings, settingsSummary, settingsToBody } from '../settings.js'

const NONE = null
const NOT_FOUND = -1

const sameForm = (left, right) =>
  JSON.stringify(settingsToBody(left)) === JSON.stringify(settingsToBody(right))

function triggerText(list, selectedIndex, currentSelected) {
  if (selectedIndex !== NOT_FOUND) {
    return `${list[selectedIndex].number}. ${list[selectedIndex].name}`
  }
  if (currentSelected) {
    return 'Current settings'
  }
  if (list.length === 0) {
    return 'No saved settings yet'
  }
  return `Pick saved settings (${list.length})`
}

function hintText(list, selectedIndex, current) {
  if (selectedIndex !== NOT_FOUND) {
    return settingsSummary(list[selectedIndex].settings)
  }
  if (list.length === 0 && !current) {
    return 'Saved settings show up here, to pick again later.'
  }
  return 'Pick one to fill the form.'
}

function CurrentRow({ current, selected, onPick }) {
  return (
    <li className={`saved-row saved-current ${selected ? 'is-selected' : ''}`} aria-selected={selected}>
      <button type="button" className="saved-pick" onClick={onPick}>
        <strong>Current settings</strong>
        <span className="muted small">
          From the Training form · {settingsSummary(settingsToBody(current))}
        </span>
      </button>
    </li>
  )
}

function SavedRow({ number, saved, selected, confirming, onPick, onAskDelete, onDelete, onKeep }) {
  if (confirming) {
    return (
      <li className="saved-row is-confirming">
        <span className="small">
          Delete <strong>{saved.name}</strong>?
        </span>
        <span className="saved-row-actions">
          <button type="button" className="button button-sell saved-small" onClick={onDelete}>
            Delete
          </button>
          <button type="button" className="button saved-small" onClick={onKeep}>
            Keep
          </button>
        </span>
      </li>
    )
  }
  return (
    <li className={`saved-row ${selected ? 'is-selected' : ''}`} aria-selected={selected}>
      <button type="button" className="saved-pick" onClick={onPick}>
        <strong>
          {number}. {saved.name}
        </strong>
        <span className="muted small">{settingsSummary(saved.settings)}</span>
      </button>
      <button
        type="button"
        className="saved-delete"
        aria-label={`Delete ${saved.name}`}
        title="Delete"
        onClick={onAskDelete}
      >
        ×
      </button>
    </li>
  )
}

/**
 * Saved settings as "1. name", "2. name", … in a scrollable list, with "Current settings" (another
 * form's settings, `current`) on top when given. The box shows the entry the form matches right
 * now. Picking hands the entry to `onPickSaved` or the form to `onPickCurrent`; × asks once, then
 * deletes the entry (its runs keep their settings).
 */
export function SavedSettingsPicker({
  saved,
  form,
  current = null,
  onPickSaved,
  onPickCurrent,
  disabled = false,
}) {
  const [open, setOpen] = useState(false)
  const [confirmingId, setConfirmingId] = useState(NONE)
  const [error, setError] = useState(null)
  const rootRef = useRef(null)
  const list = saved.list ?? []
  const selectedIndex = list.findIndex((entry) => formMatchesSettings(form, entry.settings))
  const currentSelected = Boolean(current) && sameForm(form, current)

  const close = () => {
    setOpen(false)
    setConfirmingId(NONE)
  }
  const toggle = () => {
    if (open) {
      close()
      return
    }
    setOpen(true)
  }
  useDismiss(rootRef, open, close)

  const pickSaved = (entry) => {
    onPickSaved(entry)
    close()
  }
  const pickCurrent = () => {
    onPickCurrent(current)
    close()
  }
  const remove = async (entry) => {
    try {
      await saved.remove(entry.id)
      setError(null)
    } catch (failure) {
      setError(failure)
    } finally {
      setConfirmingId(NONE)
    }
  }

  return (
    <>
      <div ref={rootRef} className={`dropdown saved-picker ${open ? 'is-open' : ''}`}>
        <button
          type="button"
          className="dropdown-trigger"
          aria-expanded={open}
          disabled={disabled || (list.length === 0 && !current)}
          onClick={toggle}
        >
          <span className="dropdown-value">{triggerText(list, selectedIndex, currentSelected)}</span>
          <svg className="dropdown-chevron" viewBox="0 0 16 16" aria-hidden="true">
            <path d="M4 6l4 4 4-4" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
          </svg>
        </button>
        <ul className="dropdown-list saved-list">
          {current && <CurrentRow current={current} selected={currentSelected} onPick={pickCurrent} />}
          {list.map((entry, index) => (
            <SavedRow
              key={entry.id}
              number={entry.number}
              saved={entry}
              selected={index === selectedIndex}
              confirming={confirmingId === entry.id}
              onPick={() => pickSaved(entry)}
              onAskDelete={() => setConfirmingId(entry.id)}
              onDelete={() => remove(entry)}
              onKeep={() => setConfirmingId(NONE)}
            />
          ))}
        </ul>
      </div>
      <span className="field-hint saved-hint">{hintText(list, selectedIndex, current)}</span>
      {error && <p className="form-error small">Could not delete: {error.message}</p>}
    </>
  )
}
