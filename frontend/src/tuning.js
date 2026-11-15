import { TIMEFRAMES, UPDATE_TIME_SECONDS } from './settings.js'

export const DEFAULT_TUNE_SHARE_PERCENT = '70'
export const DEFAULT_MAX_TRIALS = '50'
export const MAX_TRIALS = 200
export const TIMEFRAME = 'timeframe'

const PERCENT = 100

/** Form state of a tuning: nothing ticked yet. */
export function defaultTuning(algorithm) {
  return {
    algorithmType: algorithm?.type,
    tunedNames: [],
    tuneSharePercent: DEFAULT_TUNE_SHARE_PERCENT,
    maxTrials: DEFAULT_MAX_TRIALS,
  }
}

export const isTuned = (tuning, name) => tuning.tunedNames.includes(name)

export function withTuned(tuning, name, tuned) {
  const others = tuning.tunedNames.filter((tunedName) => tunedName !== name)
  return { ...tuning, tunedNames: tuned ? [...others, name] : others }
}

export const hasAnythingToTune = (tuning) => tuning.tunedNames.length > 0

/** The point in [from, to] where the tune part ends and the check part starts. */
export function checkFromInstant(fromInstant, toInstant, tuneSharePercent) {
  const from = new Date(fromInstant).getTime()
  const to = new Date(toInstant).getTime()
  return new Date(from + ((to - from) * Number(tuneSharePercent)) / PERCENT).toISOString()
}

/** A settings body with empty required settings shown at their default, as the server uses them. */
export function withDefaults(settingsBody, algorithm) {
  const filled = { ...settingsBody }
  for (const definition of algorithm?.settings ?? []) {
    if (filled[definition.name] == null && definition.defaultValue != null) {
      filled[definition.name] = definition.defaultValue
    }
  }
  return filled
}

/** Every ticked setting is searched over its whole allowed range; timeframe over every size. */
export function tuningToBody(tuning, algorithm) {
  const settingRanges = (algorithm?.settings ?? [])
    .filter((definition) => definition.name !== UPDATE_TIME_SECONDS && isTuned(tuning, definition.name))
    .map((definition) => ({
      name: definition.name,
      minimum: Number(definition.minimum),
      maximum: Number(definition.maximum),
    }))
  let timeframes = []
  if (isTuned(tuning, TIMEFRAME)) {
    timeframes = TIMEFRAMES.map((timeframe) => timeframe.id)
  }
  return { settingRanges, timeframes, maxTrials: Number(tuning.maxTrials) }
}
