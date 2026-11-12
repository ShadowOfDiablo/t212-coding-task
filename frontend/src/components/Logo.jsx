const TRADING_212_BLUE = '#00a7e1'
const LOGO_BACKGROUND = '#000'

/** The Trading 212 mark: a blue chevron on a black tile. */
export function Logo() {
  return (
    <svg className="logo" viewBox="0 0 200 200" role="img" aria-label="Trading 212">
      <rect width="200" height="200" rx="44" fill={LOGO_BACKGROUND} />
      <path
        d="M50 140 88 61q4-6 11-6h2q7 0 11 6l38 79q3 7-4 7h-18q-6 0-8-5l-20-42-20 42q-2 5-8 5H54q-7 0-4-7Z"
        fill={TRADING_212_BLUE}
      />
    </svg>
  )
}
