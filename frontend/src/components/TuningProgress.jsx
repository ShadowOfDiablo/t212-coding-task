import './Tuning.css'

export function TuningProgress({ percent }) {
  return (
    <div className="tuning-progress" role="status" aria-live="polite">
      <p className="small">
        Tuning in progress. Currently <strong>{percent}%</strong> done, please wait
      </p>
      <div
        className="progress-track"
        role="progressbar"
        aria-valuemin="0"
        aria-valuemax="100"
        aria-valuenow={percent}
      >
        <div className="progress-fill" style={{ width: `${percent}%` }} />
      </div>
    </div>
  )
}
