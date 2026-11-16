import { useCallback, useEffect, useState } from 'react'
import { api } from '../api.js'

/**
 * The saved settings, shared by the training and live bot forms. `add` and `remove` change the
 * list on the backend, then load it again so every form shows the same entries.
 */
export function useSavedAlgorithms() {
  const [list, setList] = useState(null)
  const [error, setError] = useState(null)

  const refresh = useCallback(async () => {
    try {
      const loaded = await api.getSavedAlgorithms()
      setList(loaded.savedAlgorithms)
      setError(null)
    } catch (failure) {
      setError(failure)
    }
  }, [])

  useEffect(() => {
    const firstLoad = setTimeout(refresh, 0)
    return () => clearTimeout(firstLoad)
  }, [refresh])

  const add = async (name, settings) => {
    await api.saveAlgorithm(name, settings)
    await refresh()
  }
  const replace = async (id, settings) => {
    await api.replaceSavedSettings(id, settings)
    await refresh()
  }
  const remove = async (id) => {
    await api.deleteSavedAlgorithm(id)
    await refresh()
  }

  return { list, error, add, replace, remove }
}
