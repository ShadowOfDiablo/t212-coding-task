export const TIMEFRAMES = [
  { id: 'ONE_MINUTE', label: '1 minute' },
  { id: 'FIVE_MINUTES', label: '5 minutes' },
  { id: 'FIFTEEN_MINUTES', label: '15 minutes' },
  { id: 'ONE_HOUR', label: '1 hour' },
  { id: 'FOUR_HOURS', label: '4 hours' },
  { id: 'ONE_DAY', label: '1 day' },
]

export const UPDATE_TIME_SECONDS = 'updateTimeSeconds'

const SHARED_SETTINGS = [
  'timePeriod',
  UPDATE_TIME_SECONDS,
  'orderSizePercent',
  'riskPerTradePercent',
  'stopLossPercent',
]

const asText = (value) => (value == null ? '' : String(value))

/** Form state of an algorithm with every required setting at its default. */
export function defaultSettings(algorithm) {
  const values = {}
  for (const definition of algorithm.settings) {
    values[definition.name] = asText(definition.defaultValue)
  }
  return { algorithmId: algorithm.id, timeframe: 'FIFTEEN_MINUTES', liveOnTick: true, values }
}

/** Form state of settings as the API returns them (`currentParameters`, a run's settings). */
export function settingsFromResponse(settings) {
  const values = {}
  for (const name of SHARED_SETTINGS) {
    values[name] = asText(settings[name])
  }
  for (const [name, value] of Object.entries(settings.options ?? {})) {
    values[name] = asText(value)
  }
  return {
    algorithmId: settings.algorithmId,
    timeframe: settings.timeframe,
    liveOnTick: settings.liveOnTick,
    values,
  }
}

/** The request body: an empty field is sent as null, so required settings take their default. */
export function settingsToBody(form) {
  const number = (name) => (form.values[name] === '' || form.values[name] == null ? null : Number(form.values[name]))
  const options = {}
  for (const name of Object.keys(form.values)) {
    if (!SHARED_SETTINGS.includes(name) && number(name) != null) {
      options[name] = number(name)
    }
  }
  return {
    algorithmId: form.algorithmId,
    timeframe: form.timeframe,
    liveOnTick: form.liveOnTick,
    timePeriod: number('timePeriod'),
    updateTimeSeconds: form.liveOnTick ? null : number(UPDATE_TIME_SECONDS),
    orderSizePercent: number('orderSizePercent'),
    riskPerTradePercent: number('riskPerTradePercent'),
    stopLossPercent: number('stopLossPercent'),
    options,
  }
}

export const timeframeLabel = (id) => TIMEFRAMES.find((timeframe) => timeframe.id === id)?.label ?? id
