import { useEffect, useState } from 'react'
import { api } from '../api.js'

const MODES = ['TRADING', 'TRAINING']

export function ModeSwitch() {
  const [mode, setMode] = useState(null)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState(null)

  useEffect(() => {
    api
      .getBotMode()
      .then((setting) => setMode(setting.mode))
      .catch(setError)
  }, [])

  const switchTo = async (next) => {
    if (next === mode || saving) {
      return
    }
    const previous = mode
    setMode(next)
    setSaving(true)
    try {
      const setting = await api.setBotMode(next)
      setMode(setting.mode)
      setError(null)
    } catch (failure) {
      setMode(previous)
      setError(failure)
    } finally {
      setSaving(false)
    }
  }

  return (
    <div
      className={`segmented ${mode === 'TRAINING' ? 'is-second' : ''} ${mode ? '' : 'is-loading'}`}
      role="radiogroup"
      aria-label="Bot mode"
      title={error ? `Could not change the mode: ${error.message}` : 'Bot mode'}
    >
      <span className="segmented-thumb" aria-hidden="true" />
      {MODES.map((option) => (
        <button
          key={option}
          type="button"
          role="radio"
          aria-checked={mode === option}
          className={mode === option ? 'is-active' : ''}
          disabled={saving}
          onClick={() => switchTo(option)}
        >
          {option === 'TRADING' ? 'Trading' : 'Training'}
        </button>
      ))}
    </div>
  )
}
