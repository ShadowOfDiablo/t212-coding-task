import { Logo } from './Logo.jsx'
import { ModeSwitch } from './ModeSwitch.jsx'
import { ThemeToggle } from './ThemeToggle.jsx'

export function Header({ theme, onToggleTheme, connected, botMode }) {
  return (
    <header className="header">
      <div className="brand">
        <Logo />
        <div>
          <h1>Trading Bot 212</h1>
          <p className="subtitle">Binance demo account</p>
        </div>
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
