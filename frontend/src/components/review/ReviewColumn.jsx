import './ReviewColumn.css'

/**
 * A panel's left column. While a run is reviewed, `aside` (Holdings) follows the main card and
 * stretches so both columns end level. With `onSlot`, the run's trades table is portalled in
 * between; without it, the trades stay under the run's chart.
 */
export function ReviewColumn({ reviewing, aside, onSlot, children }) {
  return (
    <div className={`panel-column ${reviewing ? 'is-reviewing' : ''}`}>
      {children}
      {onSlot && <div ref={onSlot} className="trades-slot" />}
      {reviewing && <div className="panel-fill">{aside}</div>}
    </div>
  )
}
