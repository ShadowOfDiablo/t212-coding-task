import { useState } from 'react'
import { formMatchesSettings, settingsToBody } from '../settings.js'

const MAX_NAME_LENGTH = 60

/**
 * "Save current settings" for the training form. Settings already in the list just say so. Settings
 * picked from an entry and then changed offer "Save as new" or "Replace" that entry; the entry
 * itself never changes while the form is edited.
 */
export function SaveCurrentSettings({ form, saved, pickedEntryId, disabled }) {
  const [naming, setNaming] = useState(false)
  const [name, setName] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState(null)
  const list = saved.list ?? []
  const matching = list.find((entry) => formMatchesSettings(form, entry.settings))
  const picked = list.find((entry) => entry.id === pickedEntryId)

  const act = async (action) => {
    setBusy(true)
    try {
      await action()
      setError(null)
      setNaming(false)
    } catch (failure) {
      setError(failure)
    } finally {
      setBusy(false)
    }
  }
  const startNaming = () => {
    setName(picked ? `${picked.name} (edited)` : '')
    setError(null)
    setNaming(true)
  }
  const saveAsNew = (event) => {
    event.preventDefault()
    act(() => saved.add(name.trim(), settingsToBody(form)))
  }
  const replacePicked = () => act(() => saved.replace(picked.id, settingsToBody(form)))

  if (matching && !naming) {
    return <p className="field-hint save-current-note">Saved as "{matching.name}".</p>
  }
  if (!naming) {
    return (
      <button type="button" className="button save-current-open" disabled={disabled} onClick={startNaming}>
        Save current settings
      </button>
    )
  }
  return (
    <form className="save-current" onSubmit={saveAsNew}>
      <input
        type="text"
        aria-label="Name for the saved settings"
        placeholder="Name, e.g. BTC hourly"
        maxLength={MAX_NAME_LENGTH}
        value={name}
        autoFocus
        onChange={(event) => setName(event.target.value)}
      />
      <div className="save-current-actions">
        <button type="submit" className="button button-primary" disabled={busy || !name.trim()}>
          {picked ? 'Save as new' : 'Save'}
        </button>
        {picked && (
          <button type="button" className="button" disabled={busy} onClick={replacePicked}>
            Replace "{picked.name}"
          </button>
        )}
        <button type="button" className="button" disabled={busy} onClick={() => setNaming(false)}>
          Cancel
        </button>
      </div>
      {error && <p className="form-error small">{error.message}</p>}
    </form>
  )
}
