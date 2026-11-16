package com.tihomir.t212.tradingbot.api;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tihomir.t212.tradingbot.algorithm.AlgorithmRegistryService;
import com.tihomir.t212.tradingbot.algorithm.AlgorithmType;
import com.tihomir.t212.tradingbot.algorithm.TradingAlgorithmList;
import com.tihomir.t212.tradingbot.algorithm.exception.SavedAlgorithmNotFoundException;
import com.tihomir.t212.tradingbot.algorithm.exception.SettingsAlreadySavedException;
import com.tihomir.t212.tradingbot.algorithm.indicator.SimpleMovingAverageAlgorithm;
import com.tihomir.t212.tradingbot.algorithm.saved.SavedAlgorithm;
import com.tihomir.t212.tradingbot.algorithm.saved.SavedAlgorithmList;
import com.tihomir.t212.tradingbot.algorithm.saved.SavedAlgorithmService;
import com.tihomir.t212.tradingbot.algorithm.settings.AlgorithmSettingsRequest;
import com.tihomir.t212.tradingbot.marketdata.update.CandleInterval;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AlgorithmController.class)
class AlgorithmControllerTest {

  private static final String SAVED = "/api/algorithms/saved";
  private static final String NAME = "BTC hourly";
  private static final long SAVED_ID = 3;
  private static final int SAVED_NUMBER = 2;
  private static final long UNKNOWN_ID = 99;
  private static final long ALGORITHM_ID = 17;
  private static final String SAVE_BODY =
      """
      {"name": "BTC hourly",
       "settings": {"algorithmType": "SIMPLE_MOVING_AVERAGE", "timeframe": "1h",
                    "liveOnTick": true}}
      """;

  private static final String REPLACE_BODY =
      """
      {"settings": {"algorithmType": "SIMPLE_MOVING_AVERAGE", "timeframe": "1d",
                    "liveOnTick": true}}
      """;

  @Autowired MockMvc mockMvc;

  @MockitoBean AlgorithmRegistryService algorithms;
  @MockitoBean SavedAlgorithmService savedAlgorithms;

  @Test
  void listsTheAlgorithmsWithTheirSettingDefinitions() throws Exception {
    when(algorithms.all())
        .thenReturn(new TradingAlgorithmList(List.of(new SimpleMovingAverageAlgorithm())));

    mockMvc
        .perform(get("/api/algorithms"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.algorithms[0].type").value("SIMPLE_MOVING_AVERAGE"))
        .andExpect(jsonPath("$.algorithms[0].name").value("example-strategy-SMA"))
        .andExpect(jsonPath("$.algorithms[0].displayName").value("SMA"))
        .andExpect(jsonPath("$.algorithms[0].description").value(containsString("Mean reversion")))
        .andExpect(jsonPath("$.algorithms[0].settings[0].name").value("timePeriod"))
        .andExpect(jsonPath("$.algorithms[0].settings[0].required").value(true))
        .andExpect(jsonPath("$.algorithms[0].settings[0].wholeNumber").value(true))
        .andExpect(jsonPath("$.algorithms[0].settings[0].defaultValue").value(20))
        .andExpect(jsonPath("$.algorithms[0].settings[3].name").value("stopLossPercent"))
        .andExpect(jsonPath("$.algorithms[0].settings[3].required").value(false))
        .andExpect(jsonPath("$.algorithms[0].settings[3].defaultValue").isEmpty());
  }

  @Test
  void listsTheSavedSettingsWithTheirNames() throws Exception {
    when(savedAlgorithms.getSavedAlgorithms())
        .thenReturn(new SavedAlgorithmList(List.of(savedAlgorithm())));

    mockMvc
        .perform(get(SAVED))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.savedAlgorithms[0].id").value(SAVED_ID))
        .andExpect(jsonPath("$.savedAlgorithms[0].number").value(SAVED_NUMBER))
        .andExpect(jsonPath("$.savedAlgorithms[0].name").value(NAME))
        .andExpect(jsonPath("$.savedAlgorithms[0].settings.timeframe").value("1h"))
        .andExpect(jsonPath("$.savedAlgorithms[0].settings.timePeriod").value(20));
  }

  @Test
  void savesSettingsUnderAName() throws Exception {
    when(savedAlgorithms.saveAlgorithm(eq(NAME), any(AlgorithmSettingsRequest.class)))
        .thenReturn(savedAlgorithm());

    mockMvc
        .perform(post(SAVED).contentType(MediaType.APPLICATION_JSON).content(SAVE_BODY))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(SAVED_ID))
        .andExpect(jsonPath("$.name").value(NAME));
  }

  @Test
  void replacesTheSettingsOfAnEntryOrAnswersConflictWhenTheyAreSavedElsewhere() throws Exception {
    when(savedAlgorithms.replaceSettings(eq(SAVED_ID), any(AlgorithmSettingsRequest.class)))
        .thenReturn(savedAlgorithm());
    when(savedAlgorithms.replaceSettings(eq(UNKNOWN_ID), any(AlgorithmSettingsRequest.class)))
        .thenThrow(new SettingsAlreadySavedException(NAME));

    mockMvc
        .perform(
            put(SAVED + "/" + SAVED_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(REPLACE_BODY))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value(NAME));
    mockMvc
        .perform(
            put(SAVED + "/" + UNKNOWN_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(REPLACE_BODY))
        .andExpect(status().isConflict());
  }

  @Test
  void refusesABlankName() throws Exception {
    mockMvc
        .perform(
            post(SAVED)
                .contentType(MediaType.APPLICATION_JSON)
                .content(SAVE_BODY.replace(NAME, " ")))
        .andExpect(status().isBadRequest());
  }

  @Test
  void deletesASavedEntryAndAnswersNotFoundForAnUnknownOne() throws Exception {
    doThrow(new SavedAlgorithmNotFoundException(UNKNOWN_ID))
        .when(savedAlgorithms)
        .deleteSavedAlgorithm(UNKNOWN_ID);

    mockMvc.perform(delete(SAVED + "/" + SAVED_ID)).andExpect(status().isNoContent());
    verify(savedAlgorithms).deleteSavedAlgorithm(SAVED_ID);
    mockMvc.perform(delete(SAVED + "/" + UNKNOWN_ID)).andExpect(status().isNotFound());
  }

  private static SavedAlgorithm savedAlgorithm() {
    return new SavedAlgorithm(
        SAVED_ID,
        SAVED_NUMBER,
        NAME,
        Instant.parse("2026-01-01T10:00:00Z"),
        ALGORITHM_ID,
        new SimpleMovingAverageAlgorithm()
            .resolveSettings(
                new AlgorithmSettingsRequest(
                    AlgorithmType.SIMPLE_MOVING_AVERAGE, CandleInterval.ONE_HOUR, true, Map.of())));
  }
}
