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

const send = (method, path, body) => request(path, { method, body: JSON.stringify(body) })

export const api = {
  broker: BROKER,
  getTickers: () => request(`${BROKER_PATH}/tickers`),
  followTicker: (ticker) => send('POST', `${BROKER_PATH}/tickers`, { ticker }),
  getBalances: () => request(`${BROKER_PATH}/balance`),
  getOrderHistory: () => request(`${BROKER_PATH}/orders/history`),
  getRejectedOrders: () => request(`${BROKER_PATH}/orders/rejected`),
  getOpenOrders: () => request(`${BROKER_PATH}/orders/open`),
  getBotMode: () => request('/api/bot/mode'),
  setBotMode: (mode) => send('PUT', '/api/bot/mode', { mode }),
  getAlgorithms: () => request('/api/algorithms'),
  getBot: () => request(`${BROKER_PATH}/bot`),
  startBot: (ticker, settings) => send('POST', `${BROKER_PATH}/bot/start`, { ticker, settings }),
  stopBot: () => request(`${BROKER_PATH}/bot/stop`, { method: 'POST' }),
  updateBotParameters: (settings) => send('PUT', `${BROKER_PATH}/bot/current-parameters`, settings),
  getTraining: () => request(`${BROKER_PATH}/training`),
  runTraining: (body) => send('POST', `${BROKER_PATH}/training/runs`, body),
  getRuns: () => request(`${BROKER_PATH}/runs`),
  getRun: (id) => request(`${BROKER_PATH}/runs/${id}`),
}
