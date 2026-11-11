import { api } from './api.js'
import { Header } from './components/Header.jsx'
import { HoldingsCard } from './components/HoldingsCard.jsx'
import { TickerStrip } from './components/TickerStrip.jsx'
import { usePolling } from './hooks/usePolling.js'
import { useTheme } from './hooks/useTheme.js'
import './styles.css'

const TICKER_REFRESH_MS = 2000
const ACCOUNT_REFRESH_MS = 5000

function App() {
  const { theme, toggleTheme } = useTheme()
  const tickers = usePolling(api.getTickers, TICKER_REFRESH_MS)
  const balances = usePolling(api.getBalances, ACCOUNT_REFRESH_MS)

  const connected = tickers.data !== null && tickers.error === null

  return (
    <div className="app">
      <Header theme={theme} onToggleTheme={toggleTheme} connected={connected} />
      {tickers.error && (
        <p className="banner" role="alert">
          The backend is not reachable. Start it with <code>./mvnw spring-boot:run</code> in{' '}
          <code>backend/</code>; the dashboard reconnects by itself.
        </p>
      )}
      <main className="layout">
        <TickerStrip tickers={tickers.data?.tickers} />
        <div className="columns">
          <HoldingsCard balances={balances} tickers={tickers.data?.tickers} />
        </div>
      </main>
    </div>
  )
}

export default App
