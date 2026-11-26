import { BrokerMenu } from './BrokerMenu.jsx'
import { Logo } from './Logo.jsx'
import { ModeSwitch } from './ModeSwitch.jsx'
import { ThemeToggle } from './ThemeToggle.jsx'

export function Header({ theme, onToggleTheme, connected, botMode, broker, brokerLabel, onSelectBroker }) {
  return (
    <header className="header">
      <div className="header-start">
        <div className="brand">
          <Logo />
          <div>
            <h1>Trading Bot</h1>
            <p className="subtitle">{brokerLabel} demo account</p>
          </div>
        </div>
        <BrokerMenu broker={broker} onSelect={onSelectBroker} />
      </div>
      <div className="header-actions">
        <span className={`live ${connected ? 'is-live' : 'is-offline'}`}>
          <span className="live-dot" aria-hidden="true" />
          {connected ? 'Live' : 'Offline'}
        </span>
        <ModeSwitch botMode={botMode} />
        <ThemeToggle theme={theme} onToggle={onToggleTheme} />
      </div>
    </header>
  )
}
