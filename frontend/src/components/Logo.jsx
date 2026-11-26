import hydraMark from '../assets/hydra-mark.png'

const LOGO_BACKGROUND = '#000'

/** The three-headed hydra mark, recoloured to the brand blue, on a black tile. */
export function Logo() {
  return (
    <svg className="logo" viewBox="0 0 200 200" role="img" aria-label="Trading Bot">
      <rect width="200" height="200" rx="44" fill={LOGO_BACKGROUND} />
      <image href={hydraMark} width="200" height="200" />
    </svg>
  )
}
