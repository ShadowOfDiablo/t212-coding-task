import './LineChart.css'
import { useState } from 'react'
import { formatPrice, formatTime } from '../format.js'

const WIDTH = 640
const HEIGHT = 220
const PADDING = { top: 16, right: 12, bottom: 24, left: 64 }
const PLOT_WIDTH = WIDTH - PADDING.left - PADDING.right
const PLOT_HEIGHT = HEIGHT - PADDING.top - PADDING.bottom
const GRID_LINES = 4

function scaleFor(points, baseline) {
  const values = points.map((point) => point.value)
  if (baseline != null) {
    values.push(baseline)
  }
  const min = Math.min(...values)
  const max = Math.max(...values)
  const margin = (max - min) * 0.08 || Math.abs(max) * 0.01 || 1
  const low = min - margin
  const high = max + margin
  const times = points.map((point) => new Date(point.time).getTime())
  const first = Math.min(...times)
  const last = Math.max(...times)
  return {
    low,
    high,
    x: (time) =>
      PADDING.left + (last === first ? PLOT_WIDTH : ((time - first) / (last - first)) * PLOT_WIDTH),
    y: (value) => PADDING.top + (1 - (value - low) / (high - low)) * PLOT_HEIGHT,
  }
}

/** Portfolio value over time, with the starting value as a dashed line. */
export function LineChart({ points, baseline = null, emptyText = 'No data yet.' }) {
  const [hoverIndex, setHoverIndex] = useState(null)

  if (!points || points.length === 0) {
    return <p className="empty chart-empty">{emptyText}</p>
  }

  const scale = scaleFor(points, baseline)
  const coordinates = points.map((point) => ({
    x: scale.x(new Date(point.time).getTime()),
    y: scale.y(point.value),
  }))
  const line = coordinates.map(({ x, y }, index) => `${index === 0 ? 'M' : 'L'}${x},${y}`).join(' ')
  const area = `${line} L${coordinates.at(-1).x},${PADDING.top + PLOT_HEIGHT} L${coordinates[0].x},${PADDING.top + PLOT_HEIGHT} Z`
  const lastValue = points.at(-1).value
  const trend = baseline == null || lastValue >= baseline ? 'up' : 'down'
  const gridValues = Array.from(
    { length: GRID_LINES + 1 },
    (_, step) => scale.low + ((scale.high - scale.low) * step) / GRID_LINES,
  )
  const hovered = hoverIndex == null ? null : points[hoverIndex]

  const onMove = (event) => {
    const box = event.currentTarget.getBoundingClientRect()
    const x = ((event.clientX - box.left) / box.width) * WIDTH
    let nearest = 0
    coordinates.forEach((coordinate, index) => {
      if (Math.abs(coordinate.x - x) < Math.abs(coordinates[nearest].x - x)) {
        nearest = index
      }
    })
    setHoverIndex(nearest)
  }

  return (
    <div className={`line-chart trend-${trend}`}>
      <svg
        viewBox={`0 0 ${WIDTH} ${HEIGHT}`}
        role="img"
        aria-label="Portfolio value over time"
        onMouseMove={onMove}
        onMouseLeave={() => setHoverIndex(null)}
      >
        {gridValues.map((value) => (
          <g key={value} className="chart-grid">
            <line x1={PADDING.left} x2={WIDTH - PADDING.right} y1={scale.y(value)} y2={scale.y(value)} />
            <text x={PADDING.left - 8} y={scale.y(value)} dy="0.32em" textAnchor="end">
              {formatPrice(value)}
            </text>
          </g>
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
        <path className="chart-area" d={area} />
        <path className="chart-line" d={line} />
        <text className="chart-axis" x={PADDING.left} y={HEIGHT - 6}>
          {formatTime(points[0].time)}
        </text>
        <text className="chart-axis" x={WIDTH - PADDING.right} y={HEIGHT - 6} textAnchor="end">
          {formatTime(points.at(-1).time)}
        </text>
        {hovered && (
          <g className="chart-hover">
            <line
              x1={coordinates[hoverIndex].x}
              x2={coordinates[hoverIndex].x}
              y1={PADDING.top}
              y2={PADDING.top + PLOT_HEIGHT}
            />
            <circle cx={coordinates[hoverIndex].x} cy={coordinates[hoverIndex].y} r="4" />
          </g>
        )}
      </svg>
      <p className="chart-readout small">
        {hovered ? (
          <>
            <strong>{formatPrice(hovered.value)}</strong> USDT
            <span className="muted"> · cash {formatPrice(hovered.cash)} · {formatTime(hovered.time)}</span>
          </>
        ) : (
          <>
            <strong>{formatPrice(lastValue)}</strong> USDT now
            {baseline != null && (
              <span className={`change change-${trend}`}>
                {' '}
                {lastValue >= baseline ? '+' : ''}
                {formatPrice(lastValue - baseline)} ({(((lastValue - baseline) / baseline) * 100).toFixed(2)}%)
              </span>
            )}
          </>
        )}
      </p>
    </div>
  )
}
