const QUOTE_ASSET = 'USDT'

const HOUR_MS = 3_600_000
const DAY_MS = 24 * HOUR_MS
const YEAR_MS = 365 * DAY_MS

const priceFormat = new Intl.NumberFormat('en-US', {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
})

const compactPriceFormat = new Intl.NumberFormat('en-US', {
  notation: 'compact',
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

const utcDateTimeFormat = new Intl.DateTimeFormat('en-GB', {
  day: '2-digit',
  month: 'short',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
  timeZone: 'UTC',
})

const axisFormats = {
  seconds: new Intl.DateTimeFormat('en-GB', {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    timeZone: 'UTC',
  }),
  time: new Intl.DateTimeFormat('en-GB', { hour: '2-digit', minute: '2-digit', timeZone: 'UTC' }),
  dayTime: new Intl.DateTimeFormat('en-GB', {
    day: '2-digit',
    month: 'short',
    hour: '2-digit',
    minute: '2-digit',
    timeZone: 'UTC',
  }),
  day: new Intl.DateTimeFormat('en-GB', { day: '2-digit', month: 'short', timeZone: 'UTC' }),
  month: new Intl.DateTimeFormat('en-GB', { month: 'short', year: 'numeric', timeZone: 'UTC' }),
}

export const formatPrice = (value) => (value == null ? '—' : priceFormat.format(value))
export const formatCompactPrice = (value) => (value == null ? '—' : compactPriceFormat.format(value))
export const formatQuantity = (value) => (value == null ? '—' : quantityFormat.format(value))
export const formatTime = (iso) => (iso ? timeFormat.format(new Date(iso)) : '—')

/** Date, year and time in UTC, the zone training ranges are chosen in. */
export const formatUtcDateTime = (iso) => (iso ? `${utcDateTimeFormat.format(new Date(iso))} UTC` : '—')

/** An axis label that fits the span the chart covers: times for a day, days for a month, and so on. */
export function formatAxisTime(time, spanMs) {
  if (spanMs <= HOUR_MS) {
    return axisFormats.seconds.format(time)
  }
  if (spanMs <= DAY_MS) {
    return axisFormats.time.format(time)
  }
  if (spanMs <= 3 * DAY_MS) {
    return axisFormats.dayTime.format(time)
  }
  if (spanMs <= YEAR_MS) {
    return axisFormats.day.format(time)
  }
  return axisFormats.month.format(time)
}

export const baseAsset = (ticker) =>
  ticker.endsWith(QUOTE_ASSET) ? ticker.slice(0, -QUOTE_ASSET.length) : ticker

export { QUOTE_ASSET }
