import { useEffect, useState } from 'react'
import { api } from './api.js'
import { BROKERS } from './brokers.js'
import { BrokerUnavailable } from './components/BrokerUnavailable.jsx'
import { Header } from './components/Header.jsx'
import { HoldingsCard } from './components/HoldingsCard.jsx'
import { LiveBotPanel } from './components/LiveBotPanel.jsx'
import { OrdersCard } from './components/OrdersCard.jsx'
import { RunsPanel } from './components/RunsPanel.jsx'
import { TestOrderCard } from './components/TestOrderCard.jsx'
import { TickerStrip } from './components/TickerStrip.jsx'
import { TickerSideList } from './components/tickerstrip/TickerSideList.jsx'
import { usePinnedTickers } from './hooks/usePinnedTickers.js'
import { TrainingPanel } from './components/TrainingPanel.jsx'
import { useBotMode } from './hooks/useBotMode.js'
import { useSavedAlgorithms } from './hooks/useSavedAlgorithms.js'
import { usePolling } from './hooks/usePolling.js'
import { useTheme } from './hooks/useTheme.js'
import './styles.css'

const TICKER_REFRESH_MS = 2000
const ACCOUNT_REFRESH_MS = 5000
const RUNS_REFRESH_MS = 10000

const SECTIONS = [
  { id: 'live', label: 'Live bot' },
  { id: 'training', label: 'Training' },
  { id: 'runs', label: 'Runs' },
]

function SectionTabs({ section, onSelect }) {
  const activeIndex = SECTIONS.findIndex((candidate) => candidate.id === section)
  return (
    <div className="tabs section-tabs" role="tablist" style={{ '--tab-index': activeIndex }}>
      <span className="tab-indicator" aria-hidden="true" />
      {SECTIONS.map(({ id, label }) => (
        <button
          key={id}
          type="button"
          role="tab"
          aria-selected={section === id}
          className={section === id ? 'is-active' : ''}
          onClick={() => onSelect(id)}
        >
          {label}
        </button>
      ))}
    </div>
  )
}

function App() {
  const { theme, toggleTheme } = useTheme()
  const botMode = useBotMode()
  const [section, setSection] = useState('live')
  const [broker, setBroker] = useState(BROKERS[0].id)
  const [algorithms, setAlgorithms] = useState(null)
  const saved = useSavedAlgorithms()
  const [trainingForm, setTrainingForm] = useState(null)
  const tickers = usePolling(api.getTickers, TICKER_REFRESH_MS)
  const balances = usePolling(api.getBalances, ACCOUNT_REFRESH_MS)
  const { pinned, togglePin } = usePinnedTickers()
  const history = usePolling(api.getOrderHistory, ACCOUNT_REFRESH_MS)
  const rejected = usePolling(api.getRejectedOrders, ACCOUNT_REFRESH_MS)
  const open = usePolling(api.getOpenOrders, ACCOUNT_REFRESH_MS)
  const runs = usePolling(api.getRuns, RUNS_REFRESH_MS)

  useEffect(() => {
    api
      .getAlgorithms()
      .then((list) => setAlgorithms(list.algorithms))
      .catch(() => setAlgorithms([]))
  }, [])

  const switchModeAndSection = {
    ...botMode,
    switchMode: (next) => {
      setSection(next === 'TRAINING' ? 'training' : 'live')
      botMode.switchMode(next)
    },
  }

  const tickerList = tickers.data?.tickers
  const brokerInfo = BROKERS.find((candidate) => candidate.id === broker)
  const connected = brokerInfo.connected && tickers.data !== null && tickers.error === null
  const refreshAccount = () => {
    balances.refresh()
    history.refresh()
    rejected.refresh()
  }

  return (
    <div className="app">
      <Header
        theme={theme}
        onToggleTheme={toggleTheme}
        connected={connected}
        botMode={switchModeAndSection}
        broker={broker}
        brokerLabel={brokerInfo.label}
        onSelectBroker={setBroker}
      />
      {brokerInfo.connected && tickers.error && (
        <p className="banner" role="alert">
          The backend is not reachable. Start it with <code>./mvnw spring-boot:run</code> in{' '}
          <code>backend/</code>; the dashboard reconnects by itself.
        </p>
      )}
      {!brokerInfo.connected ? (
        <main className="layout">
          <BrokerUnavailable label={brokerInfo.label} onBack={() => setBroker(BROKERS[0].id)} />
        </main>
      ) : (
        <div className="with-ticker-list">
          <TickerSideList
            tickers={tickerList}
            balances={balances.data?.balances}
            pinned={pinned}
            onTogglePin={togglePin}
            onFollowed={tickers.refresh}
          />
          <main className="layout">
            <TickerStrip
              tickers={tickerList}
              balances={balances.data?.balances}
              pinned={pinned}
              onTogglePin={togglePin}
            />
            <SectionTabs section={section} onSelect={setSection} />
            <div hidden={section !== 'live'}>
              <LiveBotPanel
                algorithms={algorithms}
                mode={botMode.mode}
                runs={runs.data?.runs}
                saved={saved}
                currentSettings={trainingForm}
              />
            </div>
            <div hidden={section !== 'training'}>
              <TrainingPanel
                algorithms={algorithms}
                tickers={tickerList}
                mode={botMode.mode}
                onRunRecorded={runs.refresh}
                saved={saved}
                onFormChange={setTrainingForm}
              />
            </div>
            <div hidden={section !== 'runs'}>
              <RunsPanel runs={runs} />
            </div>
            <div className="columns">
              <OrdersCard history={history} rejected={rejected} open={open} />
              <div className="side-column">
                <HoldingsCard balances={balances} tickers={tickerList} />
                <TestOrderCard tickers={tickerList} onPlaced={refreshAccount} />
              </div>
            </div>
          </main>
        </div>
      )}
    </div>
  )
}

export default App
