package com.tihomir.t212.tradingbot.database;

import com.tihomir.t212.tradingbot.algorithm.saved.SavedAlgorithm;
import com.tihomir.t212.tradingbot.algorithm.saved.SavedAlgorithmList;
import com.tihomir.t212.tradingbot.algorithm.saved.SavedAlgorithmStore;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class SavedAlgorithmRepository implements SavedAlgorithmStore {

  private static final String SELECT_SAVED_ALGORITHMS =
      """
      SELECT s.id AS saved_id, s.number, s.name, s.saved_at,
             a.id, a.algorithm_type, a.timeframe_seconds, a.settings
      FROM saved_algorithm s
      JOIN algorithm a ON a.id = s.algorithm_id
      """;

  private final JdbcClient jdbcClient;

  public SavedAlgorithmRepository(JdbcClient jdbcClient) {
    this.jdbcClient = jdbcClient;
  }

  /**
   * In one transaction: rename the variant's entry, or insert one when it has none. A new entry
   * gets the highest number so far plus one; synchronized so two saves can't draw the same number.
   */
  @Override
  @Transactional
  public synchronized long saveAlgorithm(long algorithmId, String name, Instant savedAt) {
    jdbcClient
        .sql("UPDATE saved_algorithm SET name = ? WHERE algorithm_id = ?")
        .param(name)
        .param(algorithmId)
        .update();
    jdbcClient
        .sql(
            """
            INSERT INTO saved_algorithm (number, algorithm_id, name, saved_at)
            SELECT (SELECT COALESCE(MAX(number), 0) + 1 FROM saved_algorithm), ?, ?, ?
            WHERE NOT EXISTS (SELECT 1 FROM saved_algorithm WHERE algorithm_id = ?)
            """)
        .param(algorithmId)
        .param(name)
        .param(OffsetDateTime.ofInstant(savedAt, ZoneOffset.UTC))
        .param(algorithmId)
        .update();
    return jdbcClient
        .sql("SELECT id FROM saved_algorithm WHERE algorithm_id = ?")
        .param(algorithmId)
        .query(Long.class)
        .single();
  }

  @Override
  public boolean replaceAlgorithm(long savedAlgorithmId, long algorithmId) {
    int updatedRows =
        jdbcClient
            .sql("UPDATE saved_algorithm SET algorithm_id = ? WHERE id = ?")
            .param(algorithmId)
            .param(savedAlgorithmId)
            .update();
    return updatedRows > 0;
  }

  @Override
  public SavedAlgorithmList getSavedAlgorithms() {
    return new SavedAlgorithmList(
        jdbcClient
            .sql(SELECT_SAVED_ALGORITHMS + "ORDER BY s.number")
            .query(SavedAlgorithmRepository::savedAlgorithm)
            .list());
  }

  @Override
  public Optional<SavedAlgorithm> getSavedAlgorithm(long savedAlgorithmId) {
    return jdbcClient
        .sql(SELECT_SAVED_ALGORITHMS + "WHERE s.id = ?")
        .param(savedAlgorithmId)
        .query(SavedAlgorithmRepository::savedAlgorithm)
        .optional();
  }

  @Override
  public boolean deleteSavedAlgorithm(long savedAlgorithmId) {
    int deletedRows =
        jdbcClient.sql("DELETE FROM saved_algorithm WHERE id = ?").param(savedAlgorithmId).update();
    return deletedRows > 0;
  }

  private static SavedAlgorithm savedAlgorithm(ResultSet row, int rowNumber) throws SQLException {
    return new SavedAlgorithm(
        row.getLong("saved_id"),
        row.getInt("number"),
        row.getString("name"),
        row.getObject("saved_at", OffsetDateTime.class).toInstant(),
        row.getLong("id"),
        AlgorithmRepository.algorithmSettings(row, rowNumber));
  }
}
