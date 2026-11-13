package com.tihomir.t212.tradingbot.order;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * The account of one training run. It implements the same gateway as the live exchange and follows
 * the same rules: base quantities are rounded down to the ticker's lot step, orders below the
 * minimum order value are refused with Binance's error codes, and the standard 0.1% taker fee is
 * taken in the asset received. Orders fill here at the price the replay sets; nothing is sent to
 * the exchange.
 */
public final class VirtualTradingGateway implements ExchangeTradingGateway, AccountView {

  public static final String QUOTE_ASSET = "USDT";
  public static final BigDecimal TAKER_FEE_RATE = new BigDecimal("0.001");

  private static final int AMOUNT_SCALE = 8;
  private static final int QUANTITY_DIVISION_SCALE = 16;
  private static final int INSUFFICIENT_BALANCE = -2010;
  private static final int UNSUPPORTED_ORDER = -1106;
  private static final int FILTER_FAILURE = -1013;
  private static final int UNKNOWN_TICKER = -1121;
  private static final String FILLED = "FILLED";
  private static final String CLIENT_ORDER_ID_PREFIX = "training-";

  private final String ticker;
  private final String baseAsset;
  private final TickerTradingRules rules;
  private BigDecimal cash;
  private BigDecimal baseQuantity = BigDecimal.ZERO;
  private BigDecimal positionCost = BigDecimal.ZERO;
  private BigDecimal marketPrice = BigDecimal.ZERO;
  private Instant marketTime = Instant.EPOCH;
  private long nextOrderId = 1;

  public VirtualTradingGateway(String ticker, BigDecimal startingCash, TickerTradingRules rules) {
    if (!ticker.endsWith(QUOTE_ASSET) || ticker.equals(QUOTE_ASSET)) {
      throw new IllegalArgumentException("Training supports " + QUOTE_ASSET + " tickers only");
    }
    this.ticker = ticker;
    this.baseAsset = ticker.substring(0, ticker.length() - QUOTE_ASSET.length());
    this.cash = startingCash;
    this.rules = rules;
  }

  public void updateMarketPrice(BigDecimal price, Instant time) {
    marketPrice = price;
    marketTime = time;
  }

  @Override
  public ExecutedOrder placeMarketOrder(MarketOrderRequest request) {
    if (!request.ticker().equals(ticker)) {
      throw new ExchangeRejectedRequestException(UNKNOWN_TICKER, "This account trades " + ticker);
    }
    if (marketPrice.signum() <= 0) {
      throw new IllegalStateException("No market price to fill at yet");
    }
    return switch (request.side()) {
      case BUY -> buy(request);
      case SELL -> sell(request);
    };
  }

  @Override
  public AccountBalanceSnapshot accountBalances() {
    return new AccountBalanceSnapshot(
        marketTime,
        Stream.of(
                new AssetBalance(QUOTE_ASSET, cash, BigDecimal.ZERO),
                new AssetBalance(baseAsset, baseQuantity, BigDecimal.ZERO))
            .filter(balance -> balance.free().signum() > 0)
            .toList());
  }

  @Override
  public TickerTradingRules tradingRules(String requestedTicker) {
    return rules;
  }

  @Override
  public String ticker() {
    return ticker;
  }

  @Override
  public BigDecimal cash() {
    return cash;
  }

  @Override
  public BigDecimal baseQuantity() {
    return baseQuantity;
  }

  @Override
  public Optional<BigDecimal> entryPrice() {
    if (baseQuantity.signum() <= 0) {
      return Optional.empty();
    }
    return Optional.of(positionCost.divide(baseQuantity, AMOUNT_SCALE, RoundingMode.HALF_EVEN));
  }

  private ExecutedOrder buy(MarketOrderRequest request) {
    if (request.amountType() != OrderAmountType.QUOTE_AMOUNT) {
      throw new ExchangeRejectedRequestException(
          UNSUPPORTED_ORDER, "Training buys take a quote amount");
    }
    BigDecimal requestedAmount = request.amount();
    checkMinOrderValue(requestedAmount);
    BigDecimal unroundedQuantity =
        requestedAmount.divide(marketPrice, QUANTITY_DIVISION_SCALE, RoundingMode.DOWN);
    BigDecimal quantity = rules.roundDownToLotStep(unroundedQuantity);
    checkAtLeastOneLotStep(quantity, unroundedQuantity);
    if (requestedAmount.compareTo(cash) > 0) {
      throw new ExchangeRejectedRequestException(INSUFFICIENT_BALANCE, "Not enough " + QUOTE_ASSET);
    }
    BigDecimal spent = quantity.multiply(marketPrice);
    BigDecimal fee = fee(quantity);
    cash = cash.subtract(spent);
    positionCost = positionCost.add(spent);
    baseQuantity = baseQuantity.add(quantity).subtract(fee);
    return executedOrder(
        OrderSide.BUY, spent, new OrderFill(nextOrderId, marketPrice, quantity, fee, baseAsset));
  }

  private ExecutedOrder sell(MarketOrderRequest request) {
    if (request.amountType() != OrderAmountType.BASE_QUANTITY) {
      throw new ExchangeRejectedRequestException(
          UNSUPPORTED_ORDER, "Training sells take a base quantity");
    }
    BigDecimal quantity = rules.roundDownToLotStep(request.amount());
    checkAtLeastOneLotStep(quantity, request.amount());
    BigDecimal received = quantity.multiply(marketPrice).setScale(AMOUNT_SCALE, RoundingMode.DOWN);
    checkMinOrderValue(received);
    if (quantity.compareTo(baseQuantity) > 0) {
      throw new ExchangeRejectedRequestException(INSUFFICIENT_BALANCE, "Not enough " + baseAsset);
    }
    BigDecimal fee = fee(received);
    positionCost = remainingCost(quantity);
    baseQuantity = baseQuantity.subtract(quantity);
    cash = cash.add(received).subtract(fee);
    return executedOrder(
        OrderSide.SELL,
        received,
        new OrderFill(nextOrderId, marketPrice, quantity, fee, QUOTE_ASSET));
  }

  private void checkMinOrderValue(BigDecimal orderValue) {
    if (orderValue.compareTo(rules.minOrderValue()) < 0) {
      throw new ExchangeRejectedRequestException(FILTER_FAILURE, "Filter failure: NOTIONAL");
    }
  }

  private void checkAtLeastOneLotStep(BigDecimal quantity, BigDecimal unroundedQuantity) {
    if (quantity.signum() <= 0) {
      throw new ExchangeRejectedRequestException(
          FILTER_FAILURE,
          unroundedQuantity.stripTrailingZeros().toPlainString()
              + " "
              + ticker
              + " is less than one lot step of "
              + rules.lotStepSize().toPlainString());
    }
  }

  /** The cost of what is left after selling {@code soldQuantity}, at the average entry price. */
  private BigDecimal remainingCost(BigDecimal soldQuantity) {
    BigDecimal remainingQuantity = baseQuantity.subtract(soldQuantity);
    if (remainingQuantity.signum() <= 0) {
      return BigDecimal.ZERO;
    }
    return positionCost
        .multiply(remainingQuantity)
        .divide(baseQuantity, AMOUNT_SCALE, RoundingMode.HALF_EVEN);
  }

  private BigDecimal fee(BigDecimal receivedAmount) {
    return receivedAmount.multiply(TAKER_FEE_RATE).setScale(AMOUNT_SCALE, RoundingMode.UP);
  }

  private ExecutedOrder executedOrder(OrderSide side, BigDecimal quoteAmount, OrderFill fill) {
    long orderId = nextOrderId++;
    return new ExecutedOrder(
        orderId,
        CLIENT_ORDER_ID_PREFIX + orderId,
        ticker,
        side,
        FILLED,
        fill.quantity(),
        quoteAmount,
        marketPrice,
        List.of(fill),
        marketTime);
  }
}
