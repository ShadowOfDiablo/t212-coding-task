const DAY_MS = 24 * 60 * 60 * 1000

export const TIME_RANGES = [
  { id: '1D', days: 1 },
  { id: '1W', days: 7 },
  { id: '1M', days: 30 },
  { id: '3M', days: 91 },
  { id: '6M', days: 182 },
  { id: 'YTD' },
  { id: 'MAX' },
]

export const DEFAULT_TIME_RANGE = 'MAX'

function rangeStart(rangeId, now) {
  if (rangeId === 'MAX') {
    return null
  }
  if (rangeId === 'YTD') {
    return new Date(now.getFullYear(), 0, 1)
  }
  const { days } = TIME_RANGES.find((range) => range.id === rangeId)
  return new Date(now.getTime() - days * DAY_MS)
}

/** The points from the range's start until now; every point for MAX. */
export function pointsInRange(points, rangeId, now = new Date()) {
  const start = rangeStart(rangeId, now)
  if (!points || start == null) {
    return points
  }
  return points.filter((point) => new Date(point.time) >= start)
}
