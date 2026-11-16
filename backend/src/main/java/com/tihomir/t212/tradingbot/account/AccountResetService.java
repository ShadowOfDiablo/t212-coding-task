package com.tihomir.t212.tradingbot.account;

import com.tihomir.t212.tradingbot.bot.exception.LiveBotRunningException;
import com.tihomir.t212.tradingbot.bot.live.TradingBotService;
import com.tihomir.t212.tradingbot.broker.Broker;
import com.tihomir.t212.tradingbot.broker.BrokerNotFoundException;
import com.tihomir.t212.tradingbot.broker.BrokerRegistryService;
import com.tihomir.t212.tradingbot.order.OrderService;
import com.tihomir.t212.tradingbot.order.balance.AssetBalance;
import com.tihomir.t212.tradingbot.order.gateway.VirtualTradingGateway;
import com.tihomir.t212.tradingbot.order.model.MarketOrderRequest;
import com.tihomir.t212.tradingbot.order.model.OrderAmountType;
import com.tihomir.t212.tradingbot.order.model.OrderSide;
import com.tihomir.t212.tradingbot.position.PositionStore;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Prepares the demo account for a reset on the exchange's website, and lists the resets found from
 * the balances. Binance has no API to reset the demo account.
 */
@Service
public class AccountResetService {

  private static final Logger log = LoggerFactory.getLogger(AccountResetService.class);
  private static final String QUOTE_ASSET = VirtualTradingGateway.QUOTE_ASSET;

  private final BrokerRegistryService brokers;
  private final OrderService orderService;
  private final TradingBotService tradingBotService;
  private final PositionStore positionStore;
  private final AccountResetStore accountResetStore;
  private final Clock clock;

  public AccountResetService(
      BrokerRegistryService brokers,
      OrderService orderService,
      TradingBotService tradingBotService,
      PositionStore positionStore,
      AccountResetStore accountResetStore,
      Clock clock) {
    this.brokers = brokers;
    this.orderService = orderService;
    this.tradingBotService = tradingBotService;
    this.positionStore = positionStore;
    this.accountResetStore = accountResetStore;
    this.clock = clock;
  }

  /**
   * Sells every free balance except USDT, USDC included, then closes the positions still open. A
   * sell that fails keeps its balance and the others go on; the orders already executed stay.
   *
   * @throws LiveBotRunningException while the broker's live bot runs
   */
  public EmptiedAccount emptyAccount(String broker) {
    if (tradingBotService.getBotStatus(broker).isPresent()) {
      throw new LiveBotRunningException(broker);
    }
    List<AssetBalance> sellable =
        broker(broker).tradingGateway().accountBalances().balances().stream()
            .filter(balance -> !balance.asset().equals(QUOTE_ASSET))
            .filter(balance -> balance.free().signum() > 0)
            .toList();
    Map<Boolean, List<AssetBalance>> soldOrKept =
        sellable.stream()
            .collect(Collectors.partitioningBy(balance -> sellBalance(broker, balance)));
    int closedPositionCount = positionStore.closeOpenPositions(broker, clock.instant());
    EmptiedAccount emptied =
        new EmptiedAccount(soldOrKept.get(true), soldOrKept.get(false), closedPositionCount);
    log.info(
        "{}: account emptied, sold {}, kept {}, closed {} open positions",
        broker,
        emptied.soldBalances(),
        emptied.keptBalances(),
        closedPositionCount);
    return emptied;
  }

  public AccountResetList getAccountResets(String broker) {
    broker(broker);
    return accountResetStore.getAccountResets(broker);
  }

  private boolean sellBalance(String broker, AssetBalance balance) {
    String ticker = balance.asset() + QUOTE_ASSET;
    try {
      orderService.placeMarketOrder(
          broker,
          new MarketOrderRequest(
              ticker, OrderSide.SELL, balance.free(), OrderAmountType.BASE_QUANTITY));
      return true;
    } catch (RuntimeException e) {
      log.warn("{}: {} {} stays in the account: {}", broker, balance.free(), ticker, e.toString());
      return false;
    }
  }

  private Broker broker(String broker) {
    return brokers.get(broker).orElseThrow(() -> new BrokerNotFoundException(broker));
  }
}
