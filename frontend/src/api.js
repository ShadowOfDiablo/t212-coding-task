const BROKER = 'binance'
const BROKER_PATH = `/api/brokers/${BROKER}`

export class ApiError extends Error {
  constructor(status, detail) {
    super(detail || `Request failed with ${status}`)
    this.status = status
  }
}

async function request(path, options = {}) {
  const response = await fetch(path, {
    headers: { 'Content-Type': 'application/json' },
    ...options,
  })
  if (!response.ok) {
    const problem = await response.json().catch(() => ({}))
    throw new ApiError(response.status, problem.detail)
  }
  return response.json()
}

export const api = {
  broker: BROKER,
  getTickers: () => request(`${BROKER_PATH}/tickers`),
  getBalances: () => request(`${BROKER_PATH}/balance`),
  getOrderHistory: () => request(`${BROKER_PATH}/orders/history`),
  getRejectedOrders: () => request(`${BROKER_PATH}/orders/rejected`),
  getOpenOrders: () => request(`${BROKER_PATH}/orders/open`),
  getBotMode: () => request('/api/bot/mode'),
  setBotMode: (mode) =>
    request('/api/bot/mode', { method: 'PUT', body: JSON.stringify({ mode }) }),
}
