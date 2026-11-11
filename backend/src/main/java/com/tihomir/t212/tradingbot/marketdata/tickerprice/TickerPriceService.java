package com.tihomir.t212.tradingbot.marketdata.tickerprice;

import com.tihomir.t212.tradingbot.broker.BrokerNotFoundException;
import com.tihomir.t212.tradingbot.broker.BrokerRegistryService;
import org.springframework.stereotype.Service;

@Service
public class TickerPriceService {

  private final BrokerRegistryService brokers;

  public TickerPriceService(BrokerRegistryService brokers) {
    this.brokers = brokers;
  }

  public TickerPriceList getTickerPrices(String brokerName) {
    TickerPriceReader prices =
        brokers
            .get(brokerName)
            .orElseThrow(() -> new BrokerNotFoundException(brokerName))
            .tickerPrices();
    return new TickerPriceList(
        prices.tickers().tickers().stream()
            .map(ticker -> new TickerPrice(ticker, prices.get(ticker)))
            .toList());
  }
}
