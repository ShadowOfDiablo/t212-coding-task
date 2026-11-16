package com.tihomir.t212.tradingbot.order.gateway;

import com.tihomir.t212.tradingbot.order.balance.AccountBalanceSnapshot;
import com.tihomir.t212.tradingbot.order.exception.ExchangeRejectedRequestException;
import com.tihomir.t212.tradingbot.order.exception.ExchangeUnavailableException;
import com.tihomir.t212.tradingbot.order.exception.OrderStatusUnknownException;
import com.tihomir.t212.tradingbot.order.model.ExecutedOrder;
import com.tihomir.t212.tradingbot.order.model.MarketOrderRequest;
import com.tihomir.t212.tradingbot.order.model.TickerTradingRules;

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
   * @throws ExchangeUnavailableException the rules could not be read from the exchange
   */
  TickerTradingRules tradingRules(String ticker);
}
