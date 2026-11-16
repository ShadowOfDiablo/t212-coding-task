import { useLayoutEffect, useState } from 'react'

/**
 * How many more rows like the list's last one fit in the free space at the bottom of `container`,
 * up to `available`. Grows when the container grows; starts again from none when `resetKey`
 * changes, so rows that no longer fit are dropped.
 */
export function useRowsThatFit(containerRef, listRef, available, resetKey) {
  const [fitted, setFitted] = useState({ key: resetKey, count: 0 })
  const count = fitted.key === resetKey ? Math.min(fitted.count, available) : 0

  useLayoutEffect(() => {
    const container = containerRef.current
    const list = listRef.current
    if (!container || !list || available === 0) {
      return undefined
    }
    const measure = () => {
      const lastRow = list.lastElementChild
      if (!lastRow) {
        return
      }
      const rowGap = parseFloat(getComputedStyle(list).rowGap) || 0
      const rowStep = lastRow.offsetHeight + rowGap
      const paddingBottom = parseFloat(getComputedStyle(container).paddingBottom) || 0
      const free =
        container.getBoundingClientRect().bottom - paddingBottom - list.getBoundingClientRect().bottom
      const more = Math.floor(free / rowStep)
      if (more > 0) {
        setFitted((current) => {
          const base = current.key === resetKey ? current.count : 0
          return { key: resetKey, count: Math.min(available, base + more) }
        })
      }
    }
    measure()
    const observer = new ResizeObserver(measure)
    observer.observe(container)
    return () => observer.disconnect()
  }, [containerRef, listRef, available, resetKey, count])

  return count
}
