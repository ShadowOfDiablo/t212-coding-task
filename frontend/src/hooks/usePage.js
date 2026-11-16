import { useState } from 'react'

export const DEFAULT_PAGE_SIZE = 10

/**
 * Splits rows into pages of `pageSize`. The page is clamped, so a list that shrinks never leaves
 * an empty page selected.
 */
export function usePage(rows, pageSize = DEFAULT_PAGE_SIZE) {
  const [page, setPage] = useState(0)
  const pageCount = Math.max(1, Math.ceil((rows?.length ?? 0) / pageSize))
  const shownPage = Math.min(page, pageCount - 1)
  const start = shownPage * pageSize
  return {
    page: shownPage,
    pageCount,
    pageRows: rows?.slice(start, start + pageSize),
    setPage,
  }
}
