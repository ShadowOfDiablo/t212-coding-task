package com.tihomir.t212.tradingbot.database;

import com.tihomir.t212.tradingbot.account.AccountReset;
import com.tihomir.t212.tradingbot.account.AccountResetList;
import com.tihomir.t212.tradingbot.account.AccountResetStore;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class AccountResetRepository implements AccountResetStore {

  private final JdbcClient jdbcClient;

  public AccountResetRepository(JdbcClient jdbcClient) {
    this.jdbcClient = jdbcClient;
  }

  @Override
  public void insertAccountReset(String broker, Instant resetAt) {
    jdbcClient
        .sql(
            """
            INSERT INTO account_reset (broker_id, reset_at)
            SELECT broker_id, ? FROM brokers WHERE name = ?
            """)
        .param(OffsetDateTime.ofInstant(resetAt, ZoneOffset.UTC))
        .param(broker)
        .update();
  }

  @Override
  public AccountResetList getAccountResets(String broker) {
    return new AccountResetList(
        jdbcClient
            .sql(
                """
                SELECT a.reset_at FROM account_reset a
                JOIN brokers b ON b.broker_id = a.broker_id
                WHERE b.name = ?
                ORDER BY a.reset_at
                """)
            .param(broker)
            .query((row, rowNumber) ->
                new AccountReset(row.getObject("reset_at", OffsetDateTime.class).toInstant()))
            .list());
  }
}
