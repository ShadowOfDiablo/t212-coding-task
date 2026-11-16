import './ReviewColumn.css'

/**
 * A panel's left column. While a run is reviewed, its trades table (portalled into the slot) and
 * `aside` (Holdings) follow the main card, and the last one stretches so both columns end level.
 */
export function ReviewColumn({ reviewing, aside, onSlot, children }) {
  return (
    <div className={`panel-column ${reviewing ? 'is-reviewing' : ''}`}>
      {children}
      <div ref={onSlot} className="trades-slot" />
      {reviewing && <div className="panel-fill">{aside}</div>}
    </div>
  )
}
