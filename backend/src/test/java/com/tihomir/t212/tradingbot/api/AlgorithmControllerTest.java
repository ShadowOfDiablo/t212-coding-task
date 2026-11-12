package com.tihomir.t212.tradingbot.api;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tihomir.t212.tradingbot.algorithm.AlgorithmRegistryService;
import com.tihomir.t212.tradingbot.algorithm.SimpleMovingAverageAlgorithm;
import com.tihomir.t212.tradingbot.algorithm.TradingAlgorithmList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AlgorithmController.class)
class AlgorithmControllerTest {

  @Autowired MockMvc mockMvc;

  @MockitoBean AlgorithmRegistryService algorithms;

  @Test
  void listsTheAlgorithmsWithTheirSettingDefinitions() throws Exception {
    when(algorithms.all())
        .thenReturn(new TradingAlgorithmList(List.of(new SimpleMovingAverageAlgorithm())));

    mockMvc
        .perform(get("/api/algorithms"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.algorithms[0].id").value(1))
        .andExpect(jsonPath("$.algorithms[0].name").value("example-strategy-SMA"))
        .andExpect(jsonPath("$.algorithms[0].settings[0].name").value("timePeriod"))
        .andExpect(jsonPath("$.algorithms[0].settings[0].required").value(true))
        .andExpect(jsonPath("$.algorithms[0].settings[0].wholeNumber").value(true))
        .andExpect(jsonPath("$.algorithms[0].settings[0].defaultValue").value(20))
        .andExpect(jsonPath("$.algorithms[0].settings[3].name").value("riskPerTradePercent"))
        .andExpect(jsonPath("$.algorithms[0].settings[3].required").value(false))
        .andExpect(jsonPath("$.algorithms[0].settings[3].defaultValue").isEmpty());
  }
}
