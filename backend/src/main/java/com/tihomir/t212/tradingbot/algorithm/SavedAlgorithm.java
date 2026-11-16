package com.tihomir.t212.tradingbot.algorithm;

import java.time.Instant;

/**
 * A settings variant the user saved under a name, to pick again on the training or live form.
 *
 * @param number the entry's number in the list, kept for good: deleting an entry never renumbers
 *     the others
 */
public record SavedAlgorithm(
    long id,
    int number,
    String name,
    Instant savedAt,
    long algorithmId,
    AlgorithmSettings settings) {}
