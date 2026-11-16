import './OrdersCard.css'
import { useState } from 'react'
import { formatPrice, formatQuantity, formatTime } from '../format.js'
import { usePage } from '../hooks/usePage.js'
import { Pagination } from './pagination/Pagination.jsx'

const TABS = [
  { id: 'history', label: 'Executed' },
  { id: 'rejected', label: 'Rejected' },
  { id: 'open', label: 'Open' },
]

function SideBadge({ side }) {
  return <span className={`side side-${side.toLowerCase()}`}>{side}</span>
}

function ExecutedTable({ orders }) {
  return (
    <table className="orders-table">
      <thead>
        <tr>
          <th>Time</th>
          <th>Side</th>
          <th>Ticker</th>
          <th className="numeric">Quantity</th>
          <th className="numeric">Avg price</th>
          <th className="numeric">Total</th>
          <th>Status</th>
        </tr>
      </thead>
      <tbody>
        {orders.map((order) => (
          <tr key={order.exchangeOrderId}>
            <td className="muted">{formatTime(order.closedAt)}</td>
            <td>
              <SideBadge side={order.side} />
            </td>
            <td>{order.ticker}</td>
            <td className="numeric">{formatQuantity(order.executedQuantity)}</td>
            <td className="numeric">{formatPrice(order.averagePrice)}</td>
            <td className="numeric">{formatPrice(order.quoteAmount)}</td>
            <td>
              <span className="status status-ok">{order.finalStatus}</span>
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  )
}

function RejectedTable({ orders }) {
  return (
    <table className="orders-table">
      <thead>
        <tr>
          <th>Time</th>
          <th>Side</th>
          <th>Ticker</th>
          <th className="numeric">Amount</th>
          <th>Reason</th>
        </tr>
      </thead>
      <tbody>
        {orders.map((order, index) => (
          <tr key={`${order.rejectedAt}-${index}`}>
            <td className="muted">{formatTime(order.rejectedAt)}</td>
            <td>
              <SideBadge side={order.side} />
            </td>
            <td>{order.ticker}</td>
            <td className="numeric">
              {formatQuantity(order.amount)}
              <span className="muted small">
                {order.amountType === 'QUOTE_AMOUNT' ? ' USDT' : ''}
              </span>
            </td>
            <td>
              <span className="status status-error">{order.errorCode}</span>{' '}
              <span className="muted">{order.errorMessage}</span>
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  )
}

function OpenTable({ orders }) {
  return (
    <table className="orders-table">
      <thead>
        <tr>
          <th>Created</th>
          <th>Side</th>
          <th>Ticker</th>
          <th>Type</th>
          <th className="numeric">Amount</th>
          <th>Status</th>
        </tr>
      </thead>
      <tbody>
        {orders.map((order) => (
          <tr key={order.exchangeOrderId}>
            <td className="muted">{formatTime(order.createdAt)}</td>
            <td>
              <SideBadge side={order.side} />
            </td>
            <td>{order.ticker}</td>
            <td>{order.orderType}</td>
            <td className="numeric">{formatQuantity(order.amount)}</td>
            <td>
              <span className="status">{order.status}</span>
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  )
}

const EMPTY_TEXT = {
  history: 'No executed orders yet.',
  rejected: 'No rejected orders.',
  open: 'No open orders. Take-profit and stop-loss orders will show up here.',
}

export function OrdersCard({ history, rejected, open }) {
  const [tab, setTab] = useState('history')
  const sources = {
    history: { state: history, rows: history.data?.orders },
    rejected: { state: rejected, rows: rejected.data?.rejectedOrders },
    open: { state: open, rows: open.data?.openOrders },
  }
  const active = sources[tab]
  const { page, pageCount, pageRows, setPage } = usePage(active.rows)
  const activeIndex = TABS.findIndex((candidate) => candidate.id === tab)
  const selectTab = (id) => {
    setTab(id)
    setPage(0)
  }

  return (
    <section className="card orders">
      <div className="card-head">
        <h2>Orders</h2>
        <div className="tabs" role="tablist" style={{ '--tab-index': activeIndex }}>
          <span className="tab-indicator" aria-hidden="true" />
          {TABS.map(({ id, label }) => (
            <button
              key={id}
              type="button"
              role="tab"
              aria-selected={tab === id}
              className={tab === id ? 'is-active' : ''}
              onClick={() => selectTab(id)}
            >
              {label}
              <span className="count">{sources[id].rows?.length ?? '·'}</span>
            </button>
          ))}
        </div>
      </div>
      <div className="table-wrap" key={tab}>
        {active.state.error ? (
          <p className="empty">Could not load the orders: {active.state.error.message}</p>
        ) : !active.rows ? (
          [0, 1, 2, 3].map((row) => <p key={row} className="skeleton skeleton-line" />)
        ) : active.rows.length === 0 ? (
          <p className="empty">{EMPTY_TEXT[tab]}</p>
        ) : tab === 'history' ? (
          <ExecutedTable orders={pageRows} />
        ) : tab === 'rejected' ? (
          <RejectedTable orders={pageRows} />
        ) : (
          <OpenTable orders={pageRows} />
        )}
      </div>
      <Pagination page={page} pageCount={pageCount} onChange={setPage} />
    </section>
  )
}
