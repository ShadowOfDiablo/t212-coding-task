/**
 * The brokers the dashboard can show, styled in their own colours: `glow` lights the card on hover,
 * `badge` and `mark` are the background and letter of its badge.
 */
export const BROKERS = [
  {
    id: 'binance',
    label: 'Binance',
    description: 'Spot demo account',
    connected: true,
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
