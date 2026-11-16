import './BrokerMenu.css'
import { useRef, useState } from 'react'
import { BROKERS } from '../brokers.js'
import { useDismiss } from '../hooks/useDismiss.js'

function brokerStyle({ colors }) {
  return { '--broker-glow': colors.glow, '--broker-badge': colors.badge, '--broker-mark': colors.mark }
}

function BrokerBadge({ broker }) {
  return (
    <span className="broker-badge" style={brokerStyle(broker)} aria-hidden="true">
      {broker.logoPath ? (
        <svg className="broker-logo" viewBox="0 0 24 24">
          <path d={broker.logoPath} />
        </svg>
      ) : (
        broker.label.slice(0, 1)
      )}
    </span>
  )
}

/** The "Brokers" button: opens a panel of brokers, each lit in its own colours on hover. */
export function BrokerMenu({ broker, onSelect }) {
  const [open, setOpen] = useState(false)
  const rootRef = useRef(null)
  const current = BROKERS.find((candidate) => candidate.id === broker)
  useDismiss(rootRef, open, () => setOpen(false))

  const choose = (id) => {
    onSelect(id)
    setOpen(false)
  }

  return (
    <div ref={rootRef} className={`broker-menu ${open ? 'is-open' : ''}`}>
      <button
        type="button"
        className="broker-menu-trigger"
        aria-haspopup="true"
        aria-expanded={open}
        style={brokerStyle(current)}
        onClick={() => setOpen(!open)}
      >
        <span className="broker-menu-label">Brokers</span>
        <BrokerBadge broker={current} />
        <strong>{current.label}</strong>
        <svg className="broker-menu-chevron" viewBox="0 0 16 16" aria-hidden="true">
          <path d="M4 6l4 4 4-4" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
        </svg>
      </button>
      <div className="broker-panel" role="menu" aria-label="Choose a broker">
        <p className="broker-panel-title">Choose a broker</p>
        {BROKERS.map((candidate, index) => (
          <button
            key={candidate.id}
            type="button"
            role="menuitemradio"
            aria-checked={candidate.id === broker}
            className={`broker-option ${candidate.id === broker ? 'is-selected' : ''}`}
            style={{ ...brokerStyle(candidate), '--option-index': index }}
            onClick={() => choose(candidate.id)}
          >
            <BrokerBadge broker={candidate} />
            <span className="broker-option-text">
              <strong>{candidate.label}</strong>
              <span className="muted small">{candidate.description}</span>
            </span>
            <span className={`broker-status ${candidate.connected ? 'is-connected' : ''}`}>
              {candidate.connected ? 'Connected' : 'Soon'}
            </span>
          </button>
        ))}
      </div>
    </div>
  )
}
