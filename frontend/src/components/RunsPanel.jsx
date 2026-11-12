import './Panels.css'
import { useEffect, useState } from 'react'
import { api } from '../api.js'
import { formatPrice, formatTime } from '../format.js'
import { RunDetails } from './RunDetails.jsx'

/** Every recorded run, live and training; pick one to see its chart and settings history. */
export function RunsPanel({ runs }) {
  const [selectedId, setSelectedId] = useState(null)
  const [details, setDetails] = useState(null)
  const [error, setError] = useState(null)

  useEffect(() => {
    if (selectedId == null) {
      return
    }
    let current = true
    api
      .getRun(selectedId)
      .then((loaded) => current && setDetails(loaded))
      .catch((failure) => current && setError(failure))
    return () => {
      current = false
    }
  }, [selectedId])

  const select = (id) => {
    setError(null)
    setDetails(null)
    setSelectedId(id)
  }

  return (
    <div className="panel-grid">
      <section className="card">
        <div className="card-head">
          <h2>Recorded runs</h2>
          <span className="muted small">newest first</span>
        </div>
        {runs.error && <p className="form-error">Could not load the runs: {runs.error.message}</p>}
        {!runs.data ? (
          [0, 1, 2].map((row) => <p key={row} className="skeleton skeleton-line" />)
        ) : runs.data.runs.length === 0 ? (
          <p className="empty">No runs yet.</p>
        ) : (
          <div className="table-wrap">
            <table className="runs-table">
              <thead>
                <tr>
                  <th>Run</th>
                  <th>Mode</th>
                  <th>Ticker</th>
                  <th>Status</th>
                  <th>Started</th>
                  <th>Final value</th>
                </tr>
              </thead>
              <tbody>
                {runs.data.runs.map((run) => (
                  <tr
                    key={run.id}
                    className={run.id === selectedId ? 'is-selected' : ''}
                    onClick={() => select(run.id)}
                  >
                    <td>{run.id}</td>
                    <td>{run.mode === 'TRAINING' ? 'Training' : 'Live'}</td>
                    <td>{run.ticker}</td>
                    <td>{run.status}</td>
                    <td className="muted">{formatTime(run.startedAt)}</td>
                    <td>{formatPrice(run.finalValue)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
      <section className="card">
        <div className="card-head">
          <h2>Run details</h2>
        </div>
        {error && <p className="form-error">Could not load the run: {error.message}</p>}
        {selectedId == null && <p className="empty chart-empty">Pick a run on the left.</p>}
        {selectedId != null && !details && !error && <p className="skeleton skeleton-line" />}
        {details && <RunDetails details={details} />}
      </section>
    </div>
  )
}
