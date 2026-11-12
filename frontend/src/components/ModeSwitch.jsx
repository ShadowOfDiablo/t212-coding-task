const MODES = ['TRADING', 'TRAINING']

export function ModeSwitch({ botMode }) {
  const { mode, saving, error, switchMode } = botMode
  return (
    <div className="mode-switch">
      <div
        className={`segmented ${mode === 'TRAINING' ? 'is-second' : ''} ${mode ? '' : 'is-loading'}`}
        role="radiogroup"
        aria-label="Bot mode"
        title={error ? `Could not change the mode: ${error.message}` : 'Bot mode'}
      >
        <span className="segmented-thumb" aria-hidden="true" />
        {MODES.map((option) => (
          <button
            key={option}
            type="button"
            role="radio"
            aria-checked={mode === option}
            className={mode === option ? 'is-active' : ''}
            disabled={saving}
            onClick={() => switchMode(option)}
          >
            {option === 'TRADING' ? 'Trading' : 'Training'}
          </button>
        ))}
      </div>
      {error && (
        <p className="mode-error" role="alert">
          {error.message}
        </p>
      )}
    </div>
  )
}
