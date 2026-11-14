package com.tihomir.t212.tradingbot.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tihomir.t212.tradingbot.mode.BrokerStartupRunner;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class FollowedTickerRepositoryTest {

  @Container @ServiceConnection
  static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17");

  private static final int SECOND_BROKER_ID = 2;
  private static final String SECOND_BROKER = "other";
  private static final Instant EARLIER = Instant.parse("2026-01-01T00:00:00Z");
  private static final Instant LATER = Instant.parse("2026-01-02T00:00:00Z");

  @MockitoBean BrokerStartupRunner brokerStartupRunner;

  @Autowired FollowedTickerRepository repository;
  @Autowired CandleRepository candleRepository;
  @Autowired JdbcClient jdbcClient;

  @BeforeEach
  void emptyTableAndAddASecondBroker() {
    jdbcClient.sql("DELETE FROM ticker").update();
    jdbcClient
        .sql(
            """
            INSERT INTO brokers (broker_id, name) SELECT ?, ?
            WHERE NOT EXISTS (SELECT 1 FROM brokers WHERE name = ?)
            """)
        .param(SECOND_BROKER_ID)
        .param(SECOND_BROKER)
        .param(SECOND_BROKER)
        .update();
  }

  @Test
  void storesEachTickerOnceAndReadsThemOldestFirstPerBroker() {
    repository.insertFollowedTicker("binance", "SOLUSDT", LATER);
    repository.insertFollowedTicker("binance", "ADAUSDT", EARLIER);
    repository.insertFollowedTicker("binance", "SOLUSDT", EARLIER);
    repository.insertFollowedTicker(SECOND_BROKER, "XRPUSDT", EARLIER);

    assertThat(repository.getFollowedTickers("binance")).containsExactly("ADAUSDT", "SOLUSDT");
    assertThat(repository.getFollowedTickers(SECOND_BROKER)).containsExactly("XRPUSDT");
  }

  @Test
  void listsOnlyTheTickersThatAreFollowed() {
    candleRepository.insertTicker("binance", "ETHUSDT");
    repository.insertFollowedTicker("binance", "SOLUSDT", EARLIER);

    assertThat(repository.getFollowedTickers("binance")).containsExactly("SOLUSDT");
  }

  @Test
  void refusesATickerOfAnUnknownBroker() {
    assertThatThrownBy(() -> repository.insertFollowedTicker("unknown", "SOLUSDT", EARLIER))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("unknown");
  }
}
