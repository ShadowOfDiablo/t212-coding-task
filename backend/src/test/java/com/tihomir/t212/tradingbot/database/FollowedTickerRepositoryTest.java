package com.tihomir.t212.tradingbot.database;

import static org.assertj.core.api.Assertions.assertThat;

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

  private static final Instant EARLIER = Instant.parse("2026-01-01T00:00:00Z");
  private static final Instant LATER = Instant.parse("2026-01-02T00:00:00Z");

  @MockitoBean BrokerStartupRunner brokerStartupRunner;

  @Autowired FollowedTickerRepository repository;
  @Autowired JdbcClient jdbcClient;

  @BeforeEach
  void emptyTable() {
    jdbcClient.sql("DELETE FROM followed_ticker").update();
  }

  @Test
  void storesEachTickerOnceAndReadsThemOldestFirstPerBroker() {
    repository.insertFollowedTicker("binance", "SOLUSDT", LATER);
    repository.insertFollowedTicker("binance", "ADAUSDT", EARLIER);
    repository.insertFollowedTicker("binance", "SOLUSDT", EARLIER);
    repository.insertFollowedTicker("other", "XRPUSDT", EARLIER);

    assertThat(repository.getFollowedTickers("binance")).containsExactly("ADAUSDT", "SOLUSDT");
    assertThat(repository.getFollowedTickers("other")).containsExactly("XRPUSDT");
  }
}
