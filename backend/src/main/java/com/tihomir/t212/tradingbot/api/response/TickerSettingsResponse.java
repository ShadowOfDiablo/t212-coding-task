package com.tihomir.t212.tradingbot.api.response;

import com.tihomir.t212.tradingbot.bot.run.SettingsSource;
import com.tihomir.t212.tradingbot.bot.tickersettings.AssignedSettings;
import com.tihomir.t212.tradingbot.bot.tickersettings.TickerSettings;
import java.math.BigDecimal;
import java.util.Optional;

/** A followed ticker; the settings fields are JSON {@code null} until it has settings. */
public record TickerSettingsResponse(
    String ticker,
    Optional<AlgorithmSettingsResponse> settings,
    Optional<Long> algorithmId,
    Optional<BigDecimal> budget,
    Optional<SettingsSource> source,
    Optional<Long> tunedRunId) {

  public static TickerSettingsResponse of(TickerSettings tickerSettings) {
    Optional<AssignedSettings> assigned = tickerSettings.assigned();
    return new TickerSettingsResponse(
        tickerSettings.ticker(),
        assigned.map(AssignedSettings::settings).map(AlgorithmSettingsResponse::of),
        assigned.map(AssignedSettings::algorithmId),
        assigned.flatMap(AssignedSettings::budget),
        assigned.map(AssignedSettings::source),
        assigned.flatMap(AssignedSettings::tunedRunId));
  }
}
