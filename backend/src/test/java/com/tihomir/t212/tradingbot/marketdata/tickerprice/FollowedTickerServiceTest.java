package com.tihomir.t212.tradingbot.marketdata.tickerprice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.tihomir.t212.tradingbot.broker.BrokerNotFoundException;
import com.tihomir.t212.tradingbot.broker.BrokerRegistryService;
import com.tihomir.t212.tradingbot.broker.FakeBroker;
import com.tihomir.t212.tradingbot.order.exception.ExchangeUnavailableException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseActions;
import org.springframework.web.client.RestClient;

class FollowedTickerServiceTest {

  private static final String BROKER = "binance";
  private static final String NEW_TICKER = "SOLUSDT";

  private final RestClient.Builder restBuilder = RestClient.builder();
  private final MockRestServiceServer rest = MockRestServiceServer.bindTo(restBuilder).build();
  private final FakeBroker broker = new FakeBroker(BROKER, restBuilder.build());
  private final FakeFollowedTickerStore store = new FakeFollowedTickerStore();
  private final FollowedTickerService service =
      new FollowedTickerService(
          new BrokerRegistryService(List.of(broker)),
          store,
          Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));

  @Test
  void followsATickerTheExchangeListsAndStoresIt() {
    expectCandleCheck().andRespond(withSuccess("", MediaType.APPLICATION_JSON));

    TickerList tickers = service.followTicker(BROKER, NEW_TICKER);
    service.followTicker(BROKER, NEW_TICKER);

    assertThat(tickers).containsExactly(FakeBroker.TRADED_TICKER, NEW_TICKER);
    assertThat(store.getFollowedTickers(BROKER)).containsExactly(NEW_TICKER);
    rest.verify();
  }

  @Test
  void refusesATickerTheExchangeDoesNotListOrCantBeAskedAboutAndStoresNothing() {
    expectCandleCheck().andRespond(withBadRequest());
    expectCandleCheck().andRespond(withServerError());

    assertThatThrownBy(() -> service.followTicker(BROKER, "NOPEUSDT"))
        .isInstanceOf(UnknownTickerException.class);
    assertThatThrownBy(() -> service.followTicker(BROKER, NEW_TICKER))
        .isInstanceOf(ExchangeUnavailableException.class);
    assertThatThrownBy(() -> service.followTicker("other", NEW_TICKER))
        .isInstanceOf(BrokerNotFoundException.class);

    assertThat(store.getFollowedTickers(BROKER)).isEmpty();
    assertThat(broker.tickers()).containsExactly(FakeBroker.TRADED_TICKER);
  }

  @Test
  void restoresTheStoredTickersAndGoesOnWhenTheDatabaseIsDown() {
    store.insertFollowedTicker(BROKER, NEW_TICKER, Instant.EPOCH);

    service.restoreFollowedTickers(broker);
    store.takeDatabaseDown();
    service.restoreFollowedTickers(broker);

    assertThat(broker.tickers()).containsExactly(FakeBroker.TRADED_TICKER, NEW_TICKER);
  }

  @Test
  void storesTheConfiguredTickersAsFollowedAtStartup() {
    store.insertFollowedTicker(BROKER, NEW_TICKER, Instant.EPOCH);

    service.restoreFollowedTickers(broker);
    service.restoreFollowedTickers(broker);

    assertThat(store.getFollowedTickers(BROKER))
        .containsExactly(NEW_TICKER, FakeBroker.TRADED_TICKER);
    assertThat(broker.tickers()).containsExactly(FakeBroker.TRADED_TICKER, NEW_TICKER);
  }

  private ResponseActions expectCandleCheck() {
    return rest.expect(requestTo(Matchers.startsWith("http://rest.test/candles")));
  }
}
