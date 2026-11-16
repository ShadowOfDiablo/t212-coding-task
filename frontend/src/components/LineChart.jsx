import './LineChart.css'
import { useId, useState } from 'react'
import { formatAxisTime, formatPrice, formatUtcDateTime } from '../format.js'

const WIDTH = 720
const HEIGHT = 260
const PADDING = { top: 12, right: 8, bottom: 28, left: 72 }
const PLOT_WIDTH = WIDTH - PADDING.left - PADDING.right
const PLOT_HEIGHT = HEIGHT - PADDING.top - PADDING.bottom
const GRID_LINES = 4
const TIME_TICKS = 5
const VALUE_MARGIN = 0.08
const PERCENT = 100
const TOOLTIP_FLIP_SHARE = 0.6
const MARKER_LABEL_GAP = 4
const TRADE_MARK_SIZE = 6

/** A triangle pointing up for a buy and down for a sell, centred on the trade. */
function tradeMarkPath(x, y, side) {
  const tip = side === 'BUY' ? -TRADE_MARK_SIZE : TRADE_MARK_SIZE
  return `M${x},${y + tip} L${x - TRADE_MARK_SIZE},${y - tip / 2} L${x + TRADE_MARK_SIZE},${y - tip / 2} Z`
}

const timeOf = (point) => new Date(point.time).getTime()

function scaleFor(points, baseline) {
  const values = points.map((point) => point.value)
  if (baseline != null) {
    values.push(baseline)
  }
  const min = Math.min(...values)
  const max = Math.max(...values)
  const margin = (max - min) * VALUE_MARGIN || Math.abs(max) * 0.01 || 1
  const low = min - margin
  const high = max + margin
  const times = points.map(timeOf)
  const first = Math.min(...times)
  const last = Math.max(...times)
  return {
    low,
    high,
    first,
    last,
    x: (time) => PADDING.left + (last === first ? PLOT_WIDTH : ((time - first) / (last - first)) * PLOT_WIDTH),
    y: (value) => PADDING.top + (1 - (value - low) / (high - low)) * PLOT_HEIGHT,
  }
}

/** Tangents of a monotone cubic (Fritsch–Carlson), so the smooth line never overshoots a point. */
function monotoneTangents(coordinates) {
  const slopes = coordinates.slice(1).map((point, index) => {
    const previous = coordinates[index]
    const run = point.x - previous.x
    return run === 0 ? 0 : (point.y - previous.y) / run
  })
  return coordinates.map((_, index) => {
    if (index === 0) {
      return slopes[0] ?? 0
    }
    if (index === coordinates.length - 1) {
      return slopes[index - 1]
    }
    const before = slopes[index - 1]
    const after = slopes[index]
    return before * after <= 0 ? 0 : (2 * before * after) / (before + after)
  })
}

function smoothPath(coordinates) {
  const tangents = monotoneTangents(coordinates)
  return coordinates
    .map((point, index) => {
      if (index === 0) {
        return `M${point.x},${point.y}`
      }
      const previous = coordinates[index - 1]
      const third = (point.x - previous.x) / 3
      const firstControl = `${previous.x + third},${previous.y + tangents[index - 1] * third}`
      const secondControl = `${point.x - third},${point.y - tangents[index] * third}`
      return `C${firstControl} ${secondControl} ${point.x},${point.y}`
    })
    .join(' ')
}

const toCoordinates = (points, scale) =>
  points.map((point) => ({ x: scale.x(timeOf(point)), y: scale.y(point.value) }))

function nearestIndex(coordinates, x) {
  let nearest = 0
  coordinates.forEach((coordinate, index) => {
    if (Math.abs(coordinate.x - x) < Math.abs(coordinates[nearest].x - x)) {
      nearest = index
    }
  })
  return nearest
}

function ChangeBadge({ value, baseline }) {
  if (baseline == null || baseline === 0) {
    return null
  }
  const change = value - baseline
  const trend = change >= 0 ? 'up' : 'down'
  const sign = change >= 0 ? '+' : ''
  return (
    <span className={`change-badge change-badge-${trend}`}>
      {sign}
      {formatPrice(change)} ({sign}
      {((change / baseline) * PERCENT).toFixed(2)}%)
    </span>
  )
}

function Tooltip({ point, coordinate, baseline }) {
  const share = coordinate.x / WIDTH
  const style = { left: `${share * PERCENT}%`, top: `${(coordinate.y / HEIGHT) * PERCENT}%` }
  return (
    <div className={`chart-tooltip ${share > TOOLTIP_FLIP_SHARE ? 'is-flipped' : ''}`} style={style}>
      <strong>{formatPrice(point.value)} USDT</strong>
      <ChangeBadge value={point.value} baseline={baseline} />
      {point.cash != null && <span className="muted">Cash {formatPrice(point.cash)}</span>}
      <span className="muted">{formatUtcDateTime(point.time)}</span>
    </div>
  )
}

/**
 * Portfolio value over time as a smooth line. `baseline` (the starting value) is drawn dashed and
 * sets the colour of the change; `comparePoints` adds a second, dashed line. `markers` ({time,
 * label}) are vertical lines at moments inside the chart's time range, such as account resets.
 * `trades` ({time, price, side}) are drawn as ▲ for a buy and ▼ for a sell at their own price.
 */
export function LineChart({
  points,
  comparePoints = null,
  baseline = null,
  markers = [],
  trades = [],
  emptyText = 'No data yet.',
}) {
  const [hoverIndex, setHoverIndex] = useState(null)
  const gradientId = useId()

  if (!points || points.length === 0) {
    return <p className="empty chart-empty">{emptyText}</p>
  }

  const tradePoints = trades.map((trade) => ({ time: trade.time, value: Number(trade.price) }))
  const scale = scaleFor([...points, ...(comparePoints ?? []), ...tradePoints], baseline)
  const coordinates = toCoordinates(points, scale)
  const line = smoothPath(coordinates)
  const bottom = PADDING.top + PLOT_HEIGHT
  const area = `${line} L${coordinates.at(-1).x},${bottom} L${coordinates[0].x},${bottom} Z`
  const compareLine = comparePoints?.length ? smoothPath(toCoordinates(comparePoints, scale)) : null
  const lastValue = points.at(-1).value
  const valueTicks = Array.from(
    { length: GRID_LINES + 1 },
    (_, step) => scale.low + ((scale.high - scale.low) * step) / GRID_LINES,
  )
  const span = scale.last - scale.first
  const timeTicks =
    span === 0
      ? [scale.first]
      : Array.from({ length: TIME_TICKS }, (_, step) => scale.first + (span * step) / (TIME_TICKS - 1))
  const hovered = hoverIndex == null ? null : points[hoverIndex]
  const shownMarkers = markers.filter((marker) => {
    const time = timeOf(marker)
    return time >= scale.first && time <= scale.last
  })

  const onMove = (event) => {
    const box = event.currentTarget.getBoundingClientRect()
    setHoverIndex(nearestIndex(coordinates, ((event.clientX - box.left) / box.width) * WIDTH))
  }

  return (
    <div className="line-chart">
      <div className="chart-headline">
        <span className="chart-value">
          {formatPrice(lastValue)} <span className="muted">USDT</span>
        </span>
        <ChangeBadge value={lastValue} baseline={baseline} />
      </div>
      <div className="chart-frame">
        <svg
          viewBox={`0 0 ${WIDTH} ${HEIGHT}`}
          role="img"
          aria-label="Portfolio value over time"
          onMouseMove={onMove}
          onMouseLeave={() => setHoverIndex(null)}
        >
          <defs>
            <linearGradient id={gradientId} x1="0" x2="0" y1="0" y2="1">
              <stop offset="0%" className="chart-gradient-top" />
              <stop offset="100%" className="chart-gradient-bottom" />
            </linearGradient>
          </defs>
          {valueTicks.map((value) => (
            <g key={value} className="chart-grid">
              <line x1={PADDING.left} x2={WIDTH - PADDING.right} y1={scale.y(value)} y2={scale.y(value)} />
              <text x={PADDING.left - 10} y={scale.y(value)} dy="0.32em" textAnchor="end">
                {formatPrice(value)}
              </text>
            </g>
          ))}
          {timeTicks.map((time, index) => (
            <text
              key={time}
              className="chart-axis"
              x={scale.x(time)}
              y={HEIGHT - 8}
              textAnchor={index === 0 ? 'start' : index === timeTicks.length - 1 ? 'end' : 'middle'}
            >
              {formatAxisTime(time, span)}
            </text>
          ))}
          {baseline != null && (
            <line
              className="chart-baseline"
              x1={PADDING.left}
              x2={WIDTH - PADDING.right}
              y1={scale.y(baseline)}
              y2={scale.y(baseline)}
            />
          )}
          {shownMarkers.map((marker) => (
            <g key={marker.time} className="chart-marker">
              <line
                x1={scale.x(timeOf(marker))}
                x2={scale.x(timeOf(marker))}
                y1={PADDING.top}
                y2={bottom}
              />
              <text x={scale.x(timeOf(marker)) - MARKER_LABEL_GAP} y={PADDING.top} dy="0.9em" textAnchor="end">
                {marker.label}
              </text>
            </g>
          ))}
          <g key={points[0].time} className="chart-series">
            <path className="chart-area" d={area} fill={`url(#${gradientId})`} />
            {compareLine && <path className="chart-line-compare" d={compareLine} />}
            <path className="chart-line" d={line} pathLength="1" />
          </g>
          {trades.map((trade) => (
            <path
              key={`${trade.side}-${trade.time}`}
              className={`chart-trade chart-trade-${trade.side === 'BUY' ? 'buy' : 'sell'}`}
              d={tradeMarkPath(scale.x(timeOf(trade)), scale.y(Number(trade.price)), trade.side)}
            >
              <title>
                {trade.side === 'BUY' ? 'Buy' : 'Sell'} at {formatPrice(trade.price)} USDT,{' '}
                {formatUtcDateTime(trade.time)}
              </title>
            </path>
          ))}
          {hovered && (
            <g className="chart-hover">
              <line
                x1={coordinates[hoverIndex].x}
                x2={coordinates[hoverIndex].x}
                y1={PADDING.top}
                y2={bottom}
              />
              <circle cx={coordinates[hoverIndex].x} cy={coordinates[hoverIndex].y} r="5" />
            </g>
          )}
        </svg>
        {hovered && <Tooltip point={hovered} coordinate={coordinates[hoverIndex]} baseline={baseline} />}
      </div>
    </div>
  )
}
