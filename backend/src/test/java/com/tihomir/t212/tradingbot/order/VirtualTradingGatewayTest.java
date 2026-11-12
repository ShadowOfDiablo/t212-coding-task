package com.tihomir.t212.tradingbot.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class VirtualTradingGatewayTest {

  private static final BigDecimal FEE_RATE = new BigDecimal("0.001");
  private static final Instant FILL_TIME = Instant.parse("2020-01-01T00:05:00Z");

  private final VirtualTradingGateway account =
      new VirtualTradingGateway("BTCUSDT", new BigDecimal("1000"), FEE_RATE);

  @Test
  void buysWithAQuoteAmountAndPaysTheFeeInTheCoin() {
    account.updateMarketPrice(new BigDecimal("200"), FILL_TIME);

    ExecutedOrder order = account.placeMarketOrder(buy("1000"));

    assertThat(order.executedQuantity()).isEqualByComparingTo("5");
    assertThat(order.quoteAmount()).isEqualByComparingTo("1000");
    assertThat(order.averagePrice()).isEqualByComparingTo("200");
    assertThat(order.transactTime()).isEqualTo(FILL_TIME);
    assertThat(order.fills().getFirst().commission()).isEqualByComparingTo("0.005");
    assertThat(order.fills().getFirst().commissionAsset()).isEqualTo("BTC");
    assertThat(account.cash()).isEqualByComparingTo("0");
    assertThat(account.baseQuantity()).isEqualByComparingTo("4.995");
  }

  @Test
  void sellsABaseQuantityAndPaysTheFeeInUsdt() {
    account.updateMarketPrice(new BigDecimal("200"), FILL_TIME);
    account.placeMarketOrder(buy("1000"));
    account.updateMarketPrice(new BigDecimal("400"), FILL_TIME);

    ExecutedOrder order = account.placeMarketOrder(sell("4.995"));

    assertThat(order.quoteAmount()).isEqualByComparingTo("1998");
    assertThat(order.fills().getFirst().commission()).isEqualByComparingTo("1.998");
    assertThat(account.cash()).isEqualByComparingTo("1996.002");
    assertThat(account.baseQuantity()).isEqualByComparingTo("0");
  }

  @Test
  void tracksTheAverageEntryPriceOfThePositionHeld() {
    assertThat(account.entryPrice()).isEmpty();
    account.updateMarketPrice(new BigDecimal("100"), FILL_TIME);
    account.placeMarketOrder(buy("500"));
    account.updateMarketPrice(new BigDecimal("200"), FILL_TIME);
    account.placeMarketOrder(buy("500"));

    BigDecimal entry = account.entryPrice().orElseThrow();
    assertThat(entry).isEqualByComparingTo("133.46680013");

    account.placeMarketOrder(sell("1.0000"));
    assertThat(account.entryPrice())
        .hasValueSatisfying(
            price -> assertThat(price).isCloseTo(entry, within(new BigDecimal("0.00000002"))));

    account.placeMarketOrder(sell(account.baseQuantity().toPlainString()));
    assertThat(account.entryPrice()).isEmpty();
  }

  @Test
  void givesEveryOrderItsOwnId() {
    account.updateMarketPrice(new BigDecimal("200"), FILL_TIME);

    long first = account.placeMarketOrder(buy("100")).exchangeOrderId();
    long second = account.placeMarketOrder(buy("100")).exchangeOrderId();

    assertThat(second).isGreaterThan(first);
  }

  @Test
  void refusesOrdersItCannotCoverAndKeepsItsBalances() {
    account.updateMarketPrice(new BigDecimal("200"), FILL_TIME);

    assertThatThrownBy(() -> account.placeMarketOrder(buy("1000.01")))
        .isInstanceOf(ExchangeRejectedRequestException.class);
    assertThatThrownBy(() -> account.placeMarketOrder(sell("0.1")))
        .isInstanceOf(ExchangeRejectedRequestException.class);
    assertThatThrownBy(() -> account.placeMarketOrder(buy("0.000000001")))
        .isInstanceOf(ExchangeRejectedRequestException.class);
    assertThat(account.cash()).isEqualByComparingTo("1000");
    assertThat(account.baseQuantity()).isZero();
  }

  @Test
  void refusesOtherTickersAndOrdersBeforeAnyPrice() {
    assertThatThrownBy(() -> account.placeMarketOrder(buy("10")))
        .isInstanceOf(IllegalStateException.class);
    account.updateMarketPrice(new BigDecimal("200"), FILL_TIME);
    assertThatThrownBy(() ->
            account.placeMarketOrder(
                new MarketOrderRequest(
                    "ETHUSDT", OrderSide.BUY, BigDecimal.TEN, OrderAmountType.QUOTE_AMOUNT)))
        .isInstanceOf(ExchangeRejectedRequestException.class);
  }

  @Test
  void acceptsOnlyUsdtTickers() {
    assertThatThrownBy(() -> new VirtualTradingGateway("ETHBTC", BigDecimal.ONE, FEE_RATE))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void reportsItsNonZeroBalances() {
    account.updateMarketPrice(new BigDecimal("200"), FILL_TIME);
    account.placeMarketOrder(buy("1000"));

    assertThat(account.accountBalances().balances())
        .extracting(AssetBalance::asset)
        .containsExactly("BTC");
  }

  private static MarketOrderRequest buy(String quoteAmount) {
    return new MarketOrderRequest(
        "BTCUSDT", OrderSide.BUY, new BigDecimal(quoteAmount), OrderAmountType.QUOTE_AMOUNT);
  }

  private static MarketOrderRequest sell(String quantity) {
    return new MarketOrderRequest(
        "BTCUSDT", OrderSide.SELL, new BigDecimal(quantity), OrderAmountType.BASE_QUANTITY);
  }
}
