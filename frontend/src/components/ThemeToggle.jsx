export function ThemeToggle({ theme, onToggle }) {
  const dark = theme === 'dark'
  return (
    <button
      type="button"
      className={`icon-button theme-toggle ${dark ? 'is-dark' : ''}`}
      onClick={onToggle}
      aria-label={dark ? 'Switch to light theme' : 'Switch to dark theme'}
      title={dark ? 'Light theme' : 'Dark theme'}
    >
      <svg viewBox="0 0 24 24" aria-hidden="true">
        <mask id="moon-cut">
          <rect width="24" height="24" fill="#fff" />
          <circle className="moon-shadow" cx="24" cy="2" r="7" fill="#000" />
        </mask>
        <circle className="sun-core" cx="12" cy="12" r="5.5" mask="url(#moon-cut)" />
        <g className="sun-rays">
          <path d="M12 1.5v2.5M12 20v2.5M1.5 12H4M20 12h2.5M4.6 4.6l1.8 1.8M17.6 17.6l1.8 1.8M4.6 19.4l1.8-1.8M17.6 6.4l1.8-1.8" />
        </g>
      </svg>
    </button>
  )
}
