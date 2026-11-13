package com.tihomir.t212.tradingbot.database;

import com.tihomir.t212.tradingbot.algorithm.AlgorithmOptionStore;
import com.tihomir.t212.tradingbot.algorithm.AlgorithmSettingValues;
import com.tihomir.t212.tradingbot.algorithm.AlgorithmSettings;
import com.tihomir.t212.tradingbot.algorithm.SimpleMovingAverageAlgorithm;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Settings of {@link SimpleMovingAverageAlgorithm}: the shared row plus its {@code sma_settings}.
 */
@Repository
public class SimpleMovingAverageSettingsStore implements AlgorithmOptionStore {

  private final JdbcClient jdbcClient;
  private final AlgorithmSettingsRepository sharedSettings;

  public SimpleMovingAverageSettingsStore(
      JdbcClient jdbcClient, AlgorithmSettingsRepository sharedSettings) {
    this.jdbcClient = jdbcClient;
    this.sharedSettings = sharedSettings;
  }

  @Override
  public int algorithmId() {
    return SimpleMovingAverageAlgorithm.ID;
  }

  @Override
  @Transactional
  public long findOrInsertSettings(AlgorithmSettings settings) {
    Optional<Long> storedId =
        jdbcClient
            .sql(
                "SELECT a.id FROM algorithm_settings a"
                    + " JOIN sma_settings s ON s.settings_id = a.id WHERE "
                    + AlgorithmSettingsRepository.SHARED_SETTINGS_MATCH
                    + " ORDER BY a.id")
            .params(sharedSettings.matchValues(settings))
            .query(Long.class)
            .stream()
            .findFirst();
    if (storedId.isPresent()) {
      return storedId.get();
    }
    long settingsId = sharedSettings.insertSharedSettings(settings);
    jdbcClient.sql("INSERT INTO sma_settings (settings_id) VALUES (?)").param(settingsId).update();
    return settingsId;
  }

  @Override
  public Optional<AlgorithmSettings> getSettings(long settingsId) {
    return jdbcClient
        .sql(
            """
            SELECT a.* FROM algorithm_settings a
            JOIN sma_settings s ON s.settings_id = a.id
            WHERE a.id = ?
            """)
        .param(settingsId)
        .query((row, rowNumber) ->
            AlgorithmSettingsRepository.sharedSettings(row, AlgorithmSettingValues.NONE))
        .optional();
  }
}
