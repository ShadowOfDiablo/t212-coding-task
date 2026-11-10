package com.tihomir.t212.tradingbot.order;

import java.util.List;

public interface ExchangeTradingGateway {

  /**
   * Sends a market order to the exchange and waits for its result.
   *
   * @throws ExchangeRejectedRequestException the exchange refused the order, nothing was executed
   * @throws OrderStatusUnknownException the order may or may not have been executed
   */
  ExecutedOrder placeMarketOrder(MarketOrderRequest request);

  /**
   * Reads the non-zero balances of the account.
   *
   * @throws ExchangeRejectedRequestException the exchange refused the request
   * @throws ExchangeUnavailableException the exchange could not be reached or failed
   */
  List<AssetBalance> accountBalances();
}
