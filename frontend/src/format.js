const QUOTE_ASSET = 'USDT'

const priceFormat = new Intl.NumberFormat('en-US', {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
})

const quantityFormat = new Intl.NumberFormat('en-US', {
  minimumFractionDigits: 0,
  maximumFractionDigits: 8,
})

const timeFormat = new Intl.DateTimeFormat('en-GB', {
  day: '2-digit',
  month: 'short',
  hour: '2-digit',
  minute: '2-digit',
  second: '2-digit',
})

export const formatPrice = (value) => (value == null ? '—' : priceFormat.format(value))
export const formatQuantity = (value) => (value == null ? '—' : quantityFormat.format(value))
export const formatTime = (iso) => (iso ? timeFormat.format(new Date(iso)) : '—')

export const baseAsset = (ticker) =>
  ticker.endsWith(QUOTE_ASSET) ? ticker.slice(0, -QUOTE_ASSET.length) : ticker

export { QUOTE_ASSET }
