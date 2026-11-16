import { useState } from 'react'
import { settingsFromResponse, settingsToBody } from '../settings.js'

const MAX_NAME_LENGTH = 60

/**
 * "Save these settings": ticked while the tuned variant is in the saved list. Ticking asks for a
 * name; unticking removes the entry again.
 */
export function SaveTunedSettings({ algorithmId, settings, defaultName, saved, disabled }) {
  const [naming, setNaming] = useState(false)
  const [name, setName] = useState(defaultName)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState(null)
  const entry = (saved.list ?? []).find((candidate) => candidate.algorithmId === algorithmId)

  const act = async (action) => {
    setBusy(true)
    try {
      await action()
      setError(null)
    } catch (failure) {
      setError(failure)
    } finally {
      setBusy(false)
    }
  }

  const toggle = (checked) => {
    if (checked) {
      setNaming(true)
      return
    }
    setNaming(false)
    if (entry) {
      act(() => saved.remove(entry.id))
    }
  }
  const save = (event) => {
    event.preventDefault()
    act(async () => {
      await saved.add(name.trim(), settingsToBody(settingsFromResponse(settings)))
      setNaming(false)
    })
  }

  return (
    <div className={`save-tuned ${entry ? 'is-saved' : ''}`}>
      <label className="keep-tuned">
        <input
          type="checkbox"
          className="switch"
          checked={Boolean(entry) || naming}
          disabled={disabled || busy}
          onChange={(event) => toggle(event.target.checked)}
        />
        <span>
          <strong>Save these settings</strong>
          <span className="muted small">
            {entry
              ? `Saved as "${entry.name}". Pick it from Saved settings on the training or live form.`
              : 'Keep them in the saved list, to pick again later.'}
          </span>
        </span>
      </label>
      {naming && !entry && (
        <form className="save-tuned-name" onSubmit={save}>
          <input
            type="text"
            aria-label="Name for the saved settings"
            maxLength={MAX_NAME_LENGTH}
            value={name}
            autoFocus
            onChange={(event) => setName(event.target.value)}
          />
          <button type="submit" className="button button-primary" disabled={busy || !name.trim()}>
            {busy ? 'Saving…' : 'Save'}
          </button>
        </form>
      )}
      {error && <p className="form-error small">Could not save: {error.message}</p>}
    </div>
  )
}
