import { useCallback, useEffect, useState } from 'react'
import { api } from '../api.js'

/** The bot mode, shared by the header switch and the panels that depend on it. */
export function useBotMode() {
  const [mode, setMode] = useState(null)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState(null)

  useEffect(() => {
    api
      .getBotMode()
      .then((setting) => setMode(setting.mode))
      .catch(setError)
  }, [])

  const switchMode = useCallback(
    async (next) => {
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
    },
    [mode, saving],
  )

  return { mode, saving, error, switchMode }
}
