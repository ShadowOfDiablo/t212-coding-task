import { useState } from 'react'
import { api } from '../api.js'
import { baseAsset, formatPrice, formatQuantity, QUOTE_ASSET } from '../format.js'
import { tickerOptions } from '../settings.js'
import { Dropdown } from './Dropdown.jsx'

const DEFAULT_AMOUNT = '10'

/** Places a market buy or sell on the demo account through the order endpoint, for a quick check. */
export function TestOrderCard({ tickers, onPlaced }) {
  const [ticker, setTicker] = useState('')
  const [amount, setAmount] = useState(DEFAULT_AMOUNT)
  const [placing, setPlacing] = useState(null)
  const [result, setResult] = useState(null)
  const [error, setError] = useState(null)

  const chosenTicker = ticker || tickers?.[0]?.ticker || ''
  const canPlace = placing == null && chosenTicker && Number(amount) > 0

  const place = async (side) => {
    setPlacing(side)
    setError(null)
    try {
      const order = await api.placeOrder({
        ticker: chosenTicker,
        side,
        amount: Number(amount),
        amountType: 'QUOTE_AMOUNT',
      })
      setResult(order)
      onPlaced()
    } catch (failure) {
      setResult(null)
      setError(failure)
    } finally {
      setPlacing(null)
    }
  }

  return (
    <section className="card test-order">
      <div className="card-head">
        <h2>Test order</h2>
        <span className="muted small">market, demo account</span>
      </div>
      <div className="test-order-fields">
        <div className="field">
          <span className="field-label">Ticker</span>
          <Dropdown ariaLabel="Ticker" value={chosenTicker} options={tickerOptions(tickers)} onChange={setTicker} />
        </div>
        <label className="field">
          <span className="field-label">Amount ({QUOTE_ASSET})</span>
          <input type="number" min="1" value={amount} onChange={(event) => setAmount(event.target.value)} />
        </label>
      </div>
      <div className="test-order-actions">
        <button type="button" className="button button-buy" disabled={!canPlace} onClick={() => place('BUY')}>
          {placing === 'BUY' ? 'Buying…' : 'Test buy'}
        </button>
        <button type="button" className="button button-sell" disabled={!canPlace} onClick={() => place('SELL')}>
          {placing === 'SELL' ? 'Selling…' : 'Test sell'}
        </button>
      </div>
      {result && (
        <p className="test-order-result small" key={result.exchangeOrderId}>
          {result.side === 'BUY' ? 'Bought' : 'Sold'} {formatQuantity(result.executedQuantity)}{' '}
          {baseAsset(result.ticker)} at {formatPrice(result.averagePrice)} · {result.exchangeStatus}
        </p>
      )}
      {error && (
        <p className="form-error small" role="alert">
          {error.message}
        </p>
      )}
    </section>
  )
}
