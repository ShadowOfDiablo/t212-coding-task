package com.tihomir.t212.tradingbot.api;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tihomir.t212.tradingbot.broker.BrokerNotFoundException;
import com.tihomir.t212.tradingbot.marketdata.tickerprice.TickerPrice;
import com.tihomir.t212.tradingbot.marketdata.tickerprice.TickerPriceList;
import com.tihomir.t212.tradingbot.marketdata.tickerprice.TickerPriceService;
import com.tihomir.t212.tradingbot.marketdata.tickerprice.TickerQuote;
import com.tihomir.t212.tradingbot.marketdata.tickerprice.TickerQuoteSource;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(MarketDataController.class)
class MarketDataControllerTest {

  private static final String TICKERS = "/api/brokers/binance/tickers";

  @Autowired MockMvc mockMvc;

  @MockitoBean TickerPriceService tickerPriceService;

  @Test
  void listsTickersWithTheirLatestQuote() throws Exception {
    when(tickerPriceService.getTickerPrices("binance"))
        .thenReturn(
            new TickerPriceList(
                List.of(
                    new TickerPrice(
                        "BTCUSDT",
                        Optional.of(
                            new TickerQuote(
                                new BigDecimal("100000.10"),
                                new BigDecimal("100000.20"),
                                new BigDecimal("100000.15"),
                                Instant.EPOCH,
                                TickerQuoteSource.WEBSOCKET))),
                    new TickerPrice("ETHUSDT", Optional.empty()))));

    mockMvc
        .perform(get(TICKERS))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.tickers[0].ticker").value("BTCUSDT"))
        .andExpect(jsonPath("$.tickers[0].quote.lastPrice").value(100000.15))
        .andExpect(jsonPath("$.tickers[1].ticker").value("ETHUSDT"))
        .andExpect(jsonPath("$.tickers[1].quote").isEmpty());
  }

  @Test
  void unknownBrokerIsNotFound() throws Exception {
    when(tickerPriceService.getTickerPrices("binance"))
        .thenThrow(new BrokerNotFoundException("binance"));

    mockMvc.perform(get(TICKERS)).andExpect(status().isNotFound());
  }
}
