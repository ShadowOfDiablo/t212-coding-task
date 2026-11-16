export function BrokerUnavailable({ label, onBack }) {
  return (
    <section className="card broker-unavailable">
      <div className="broker-unavailable-icon" aria-hidden="true">
        <svg viewBox="0 0 24 24">
          <path
            d="M8 12h8M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18z"
            fill="none"
            stroke="currentColor"
            strokeWidth="1.8"
            strokeLinecap="round"
          />
        </svg>
      </div>
      <h2>{label} isn't connected yet</h2>
      <p className="muted">
        The bot trades on the Binance demo account for now. Market data, orders and runs for {label} will
        show up here once it is connected.
      </p>
      <button type="button" className="button button-primary" onClick={onBack}>
        Back to Binance
      </button>
    </section>
  )
}
