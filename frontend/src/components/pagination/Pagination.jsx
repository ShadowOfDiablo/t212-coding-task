import './Pagination.css'

/** Previous / next buttons with "Page 2 of 7"; hidden when everything fits on one page. */
export function Pagination({ page, pageCount, onChange }) {
  if (pageCount <= 1) {
    return null
  }
  return (
    <nav className="pagination" aria-label="Pages">
      <button type="button" className="button" disabled={page === 0} onClick={() => onChange(page - 1)}>
        Previous
      </button>
      <span className="muted small">
        Page {page + 1} of {pageCount}
      </span>
      <button
        type="button"
        className="button"
        disabled={page === pageCount - 1}
        onClick={() => onChange(page + 1)}
      >
        Next
      </button>
    </nav>
  )
}
