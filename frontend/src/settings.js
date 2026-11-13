export const TIMEFRAMES = [
  { id: '1m', label: '1 minute' },
  { id: '5m', label: '5 minutes' },
  { id: '15m', label: '15 minutes' },
  { id: '1h', label: '1 hour' },
  { id: '4h', label: '4 hours' },
  { id: '1d', label: '1 day' },
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
  return { algorithmType: algorithm.type, timeframe: '15m', liveOnTick: true, values }
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
    algorithmType: settings.algorithmType,
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
    algorithmType: form.algorithmType,
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
