import { useState } from 'react'
import { LineChart } from '../LineChart.jsx'
import { TimeRangePicker } from '../timerange/TimeRangePicker.jsx'
import { DEFAULT_TIME_RANGE, pointsInRange } from '../timerange/timeRanges.js'

/** The whole account's value over time, sampled every minute since the backend started. */
export function AccountValueChart({ points }) {
  const [timeRange, setTimeRange] = useState(DEFAULT_TIME_RANGE)
  const shownPoints = pointsInRange(points, timeRange)
  return (
    <div className="account-value-chart">
      <LineChart
        key={timeRange}
        points={shownPoints}
        baseline={shownPoints?.[0]?.value ?? null}
        emptyText="The first value appears within a minute of the backend starting."
      />
      <TimeRangePicker value={timeRange} onChange={setTimeRange} />
    </div>
  )
}
