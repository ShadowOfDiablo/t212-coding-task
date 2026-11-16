package com.tihomir.t212.tradingbot.database;

import static org.assertj.core.api.Assertions.assertThat;

import com.tihomir.t212.tradingbot.account.AccountReset;
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
class AccountResetRepositoryTest {

  @Container @ServiceConnection
  static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17");

  private static final Instant EARLIER = Instant.parse("2026-11-01T08:00:00Z");
  private static final Instant LATER = Instant.parse("2026-11-16T09:30:00Z");

  @MockitoBean BrokerStartupRunner brokerStartupRunner;

  @Autowired AccountResetRepository repository;
  @Autowired JdbcClient jdbcClient;

  @BeforeEach
  void emptyTable() {
    jdbcClient.sql("DELETE FROM account_reset").update();
  }

  @Test
  void storesResetsAndReadsThemOldestFirst() {
    repository.insertAccountReset("binance", LATER);
    repository.insertAccountReset("binance", EARLIER);

    assertThat(repository.getAccountResets("binance").resets())
        .containsExactly(new AccountReset(EARLIER), new AccountReset(LATER));
    assertThat(repository.getAccountResets("other").resets()).isEmpty();
  }
}
