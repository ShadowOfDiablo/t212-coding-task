package com.tihomir.t212.tradingbot.order;

public interface ExchangeTradingGateway {

  /**
   * Sends a market order to the exchange and waits for its result.
   *
   * @throws ExchangeRejectedRequestException the exchange refused the order, nothing was executed
   * @throws OrderStatusUnknownException the order may or may not have been executed
   */
  ExecutedOrder placeMarketOrder(MarketOrderRequest request);

  /**
   * Reads the non-zero balances of the account, with the time of the last account update.
   *
   * @throws ExchangeRejectedRequestException the exchange refused the request
   * @throws ExchangeUnavailableException the exchange could not be reached or failed
   */
  AccountBalanceSnapshot accountBalances();

  /**
   * The lot step and minimum order value of the ticker.
   *
   * @throws ExchangeUnavailableException the rules could not be read from the exchange
   */
  TickerTradingRules tradingRules(String ticker);
}
