package com.tihomir.t212.tradingbot.api;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tihomir.t212.tradingbot.broker.BrokerNotFoundException;
import com.tihomir.t212.tradingbot.marketdata.tickerprice.FollowedTickerService;
import com.tihomir.t212.tradingbot.marketdata.tickerprice.TickerPrice;
import com.tihomir.t212.tradingbot.marketdata.tickerprice.TickerPriceList;
import com.tihomir.t212.tradingbot.marketdata.tickerprice.TickerPriceService;
import com.tihomir.t212.tradingbot.marketdata.tickerprice.TickerQuote;
import com.tihomir.t212.tradingbot.marketdata.tickerprice.TickerQuoteSource;
import com.tihomir.t212.tradingbot.marketdata.tickerprice.UnknownTickerException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;

@WebMvcTest(MarketDataController.class)
class MarketDataControllerTest {

  private static final String TICKERS = "/api/brokers/binance/tickers";

  @Autowired MockMvc mockMvc;

  @MockitoBean TickerPriceService tickerPriceService;
  @MockitoBean FollowedTickerService followedTickerService;

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

  @Test
  void followsATickerAndAnswersWithAllTickers() throws Exception {
    when(tickerPriceService.getTickerPrices("binance"))
        .thenReturn(new TickerPriceList(List.of(new TickerPrice("SOLUSDT", Optional.empty()))));

    mockMvc
        .perform(followTicker("SOLUSDT"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.tickers[0].ticker").value("SOLUSDT"));
    verify(followedTickerService).followTicker("binance", "SOLUSDT");
  }

  @Test
  void followsATickerWrittenWithASlashOrInLowerCase() throws Exception {
    when(tickerPriceService.getTickerPrices("binance")).thenReturn(new TickerPriceList(List.of()));

    mockMvc.perform(followTicker("DOGE/USDT")).andExpect(status().isCreated());
    mockMvc.perform(followTicker("doge/usdt")).andExpect(status().isCreated());

    verify(followedTickerService, times(2)).followTicker("binance", "DOGEUSDT");
  }

  @Test
  void refusesABadlyWrittenOrUnknownTicker() throws Exception {
    mockMvc.perform(followTicker("sol-usdt")).andExpect(status().isBadRequest());
    mockMvc.perform(followTicker("/USDT")).andExpect(status().isBadRequest());
    mockMvc.perform(followTicker("DOGE/USDT/")).andExpect(status().isBadRequest());
    mockMvc.perform(followTicker("DOGE//USDT")).andExpect(status().isBadRequest());
    verifyNoInteractions(followedTickerService);

    when(followedTickerService.followTicker("binance", "NOPEUSDT"))
        .thenThrow(new UnknownTickerException("binance", "NOPEUSDT", new IllegalStateException()));
    mockMvc.perform(followTicker("NOPEUSDT")).andExpect(status().isBadRequest());
  }

  private static RequestBuilder followTicker(String ticker) {
    return post(TICKERS)
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"ticker\":\"" + ticker + "\"}");
  }
}
