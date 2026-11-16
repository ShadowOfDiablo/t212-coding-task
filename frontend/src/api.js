const BROKER = 'binance'
const BROKER_PATH = `/api/brokers/${BROKER}`

const NO_CONTENT = 204

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
  if (response.status === NO_CONTENT) {
    return undefined
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
  placeOrder: (order) => send('POST', `${BROKER_PATH}/orders`, order),
  getBotMode: () => request('/api/bot/mode'),
  setBotMode: (mode) => send('PUT', '/api/bot/mode', { mode }),
  getAlgorithms: () => request('/api/algorithms'),
  getSavedAlgorithms: () => request('/api/algorithms/saved'),
  saveAlgorithm: (name, settings) => send('POST', '/api/algorithms/saved', { name, settings }),
  replaceSavedSettings: (id, settings) => send('PUT', `/api/algorithms/saved/${id}`, { settings }),
  deleteSavedAlgorithm: (id) => request(`/api/algorithms/saved/${id}`, { method: 'DELETE' }),
  emptyAccount: () => request(`${BROKER_PATH}/account/reset`, { method: 'POST' }),
  getAccountResets: () => request(`${BROKER_PATH}/account/resets`),
  getAccountValuePoints: () => request(`${BROKER_PATH}/account/value-points`),
  getTickerSettings: () => request(`${BROKER_PATH}/ticker-settings`),
  assignTickerSettings: (ticker, settings, budget) =>
    send('PUT', `${BROKER_PATH}/ticker-settings/${ticker}`, { settings, budget }),
  getBots: () => request(`${BROKER_PATH}/bots`),
  startAllBots: () => request(`${BROKER_PATH}/bots/start-all`, { method: 'POST' }),
  stopAllBots: () => request(`${BROKER_PATH}/bots/stop-all`, { method: 'POST' }),
  startBot: (ticker) => request(`${BROKER_PATH}/bots/${ticker}/start`, { method: 'POST' }),
  stopBot: (ticker) => request(`${BROKER_PATH}/bots/${ticker}/stop`, { method: 'POST' }),
  pauseBot: (ticker) => request(`${BROKER_PATH}/bots/${ticker}/pause`, { method: 'POST' }),
  resumeBot: (ticker) => request(`${BROKER_PATH}/bots/${ticker}/resume`, { method: 'POST' }),
  updateBotParameters: (ticker, settings) =>
    send('PUT', `${BROKER_PATH}/bots/${ticker}/current-parameters`, settings),
  getTraining: () => request(`${BROKER_PATH}/training`),
  runTraining: (body) => send('POST', `${BROKER_PATH}/training/runs`, body),
  runTuning: (body) => send('POST', `${BROKER_PATH}/training/tunings`, body),
  getRuns: () => request(`${BROKER_PATH}/runs`),
  getRun: (id) => request(`${BROKER_PATH}/runs/${id}`),
  getRunPositions: (id) => request(`${BROKER_PATH}/runs/${id}/positions`),
  getRunPrices: (id) => request(`${BROKER_PATH}/runs/${id}/candles`),
}
