package com.tihomir.t212.tradingbot.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tihomir.t212.tradingbot.algorithm.AlgorithmType;
import com.tihomir.t212.tradingbot.algorithm.settings.AlgorithmSettingValues;
import com.tihomir.t212.tradingbot.algorithm.settings.AlgorithmSettings;
import com.tihomir.t212.tradingbot.algorithm.settings.AlgorithmSettingsRequest;
import com.tihomir.t212.tradingbot.bot.run.SettingsSource;
import com.tihomir.t212.tradingbot.bot.tickersettings.AssignedSettings;
import com.tihomir.t212.tradingbot.bot.tickersettings.TickerSettings;
import com.tihomir.t212.tradingbot.bot.tickersettings.TickerSettingsList;
import com.tihomir.t212.tradingbot.bot.tickersettings.TickerSettingsService;
import com.tihomir.t212.tradingbot.marketdata.update.CandleInterval;
import com.tihomir.t212.tradingbot.order.exception.TickerNotTradedException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(TickerSettingsController.class)
class TickerSettingsControllerTest {

  private static final String TICKER_SETTINGS = "/api/brokers/binance/ticker-settings";
  private static final String SETTINGS_BODY =
      """
      {"algorithmType": "SIMPLE_MOVING_AVERAGE", "timeframe": "1h",
       "liveOnTick": true, "timePeriod": 20}
      """;

  @Autowired MockMvc mockMvc;

  @MockitoBean TickerSettingsService tickerSettingsService;

  @Test
  void listsTheTickersWithAndWithoutSettings() throws Exception {
    when(tickerSettingsService.getTickerSettings("binance"))
        .thenReturn(
            new TickerSettingsList(
                List.of(assignedBtc(), new TickerSettings("ETHUSDT", Optional.empty()))));

    mockMvc
        .perform(get(TICKER_SETTINGS))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.tickers[0].ticker").value("BTCUSDT"))
        .andExpect(jsonPath("$.tickers[0].budget").value(2500))
        .andExpect(jsonPath("$.tickers[0].source").value("AUTOMATIC"))
        .andExpect(jsonPath("$.tickers[0].settings.timePeriod").value(20))
        .andExpect(jsonPath("$.tickers[1].settings").isEmpty());
  }

  @Test
  void assignsSettingsWithABudget() throws Exception {
    when(tickerSettingsService.assignSettings(
            eq("binance"), eq("BTCUSDT"), any(AlgorithmSettingsRequest.class), any()))
        .thenReturn(assignedBtc());

    mockMvc
        .perform(
            put(TICKER_SETTINGS + "/BTCUSDT")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"settings\": " + SETTINGS_BODY + ", \"budget\": 2500}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.algorithmId").value(4));

    ArgumentCaptor<Optional<BigDecimal>> budget = ArgumentCaptor.captor();
    verify(tickerSettingsService)
        .assignSettings(
            eq("binance"), eq("BTCUSDT"), any(AlgorithmSettingsRequest.class), budget.capture());
    assertThat(budget.getValue()).contains(new BigDecimal("2500"));
  }

  @Test
  void refusesANegativeBudgetAndATickerThatIsNotFollowed() throws Exception {
    mockMvc
        .perform(
            put(TICKER_SETTINGS + "/BTCUSDT")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"settings\": " + SETTINGS_BODY + ", \"budget\": -1}"))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(tickerSettingsService);

    when(tickerSettingsService.assignSettings(
            eq("binance"), eq("DOGEUSDT"), any(AlgorithmSettingsRequest.class), any()))
        .thenThrow(new TickerNotTradedException("binance", "DOGEUSDT"));
    mockMvc
        .perform(
            put(TICKER_SETTINGS + "/DOGEUSDT")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"settings\": " + SETTINGS_BODY + "}"))
        .andExpect(status().isBadRequest());
  }

  private static TickerSettings assignedBtc() {
    return new TickerSettings(
        "BTCUSDT",
        Optional.of(
            new AssignedSettings(
                4,
                new AlgorithmSettings(
                    AlgorithmType.SIMPLE_MOVING_AVERAGE,
                    20,
                    CandleInterval.ONE_HOUR,
                    true,
                    Optional.empty(),
                    new BigDecimal("100"),
                    Optional.empty(),
                    Optional.empty(),
                    AlgorithmSettingValues.NONE),
                Optional.of(new BigDecimal("2500")),
                SettingsSource.AUTOMATIC,
                Optional.of(9L))));
  }
}
