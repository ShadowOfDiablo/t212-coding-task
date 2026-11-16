/** Binance's logo on a 24 × 24 grid, from Simple Icons (CC0). */
const BINANCE_LOGO_PATH =
  'M16.624 13.9202l2.7175 2.7154-7.353 7.353-7.353-7.352 2.7175-2.7164 4.6355 4.6595 4.6356-4.6595zm4.6366-4.6366L24 12l-2.7154 2.7164L18.5682 12l2.6924-2.7164zm-9.272.001l2.7163 2.6914-2.7164 2.7174v-.001L9.2721 12l2.7164-2.7154zm-9.2722-.001L5.4088 12l-2.6914 2.6924L0 12l2.7164-2.7164zM11.9885.0115l7.353 7.329-2.7174 2.7154-4.6356-4.6356-4.6355 4.6595-2.7174-2.7154 7.353-7.353z'

/**
 * The brokers the dashboard can show, styled in their own colours: `glow` lights the card on hover,
 * `badge` and `mark` are the background and logo (or letter, without `logoPath`) of its badge.
 */
export const BROKERS = [
  {
    id: 'binance',
    label: 'Binance',
    description: 'Spot demo account',
    connected: true,
    logoPath: BINANCE_LOGO_PATH,
    colors: { glow: '#f0b90b', badge: '#181a20', mark: '#f0b90b' },
  },
  {
    id: 'kraken',
    label: 'Kraken',
    description: 'Not connected yet',
    connected: false,
    colors: { glow: '#7132f5', badge: '#7132f5', mark: '#ffffff' },
  },
]
