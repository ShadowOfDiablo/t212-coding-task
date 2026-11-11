import { useCallback, useEffect, useState } from 'react'

/**
 * Calls `load` now and then every `intervalMs`, keeping the last good data on errors. `load` must
 * be a stable function, such as one of the `api` calls.
 */
export function usePolling(load, intervalMs) {
  const [data, setData] = useState(null)
  const [error, setError] = useState(null)

  const refresh = useCallback(async () => {
    try {
      setData(await load())
      setError(null)
    } catch (failure) {
      setError(failure)
    }
  }, [load])

  useEffect(() => {
    const firstLoad = setTimeout(refresh, 0)
    const timer = setInterval(refresh, intervalMs)
    return () => {
      clearTimeout(firstLoad)
      clearInterval(timer)
    }
  }, [refresh, intervalMs])

  return { data, error, loading: data === null && error === null, refresh }
}
