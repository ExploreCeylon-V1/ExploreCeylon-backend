package com.exploreceylon.backend.service.ranking;

import com.exploreceylon.backend.model.Destination;

import java.util.List;

/**
 * Reusable ranking engine interface for prioritizing travel destinations
 * based on quality, popularity, user travel style, seasonality, and spatial context.
 */
public interface DestinationRankingEngine {

    /**
     * Calculates an intelligent composite priority score (0.0 to 100.0) for a candidate destination.
     *
     * @param destination The candidate destination entity to evaluate.
     * @param context     The current trip ranking context.
     * @return Composite priority score between 0.0 and 100.0.
     */
    double calculateScore(Destination destination, RankingContext context);

    /**
     * Ranks a list of candidate destinations in descending order of composite score.
     *
     * @param candidates The list of candidate destinations.
     * @param context    The current trip ranking context.
     * @return List of destinations sorted by priority score (descending).
     */
    List<Destination> rankDestinations(List<Destination> candidates, RankingContext context);

    /**
     * Produces a detailed internal explanation object showing individual score component values.
     * For internal logging and debugging purposes only.
     *
     * @param destination The candidate destination entity to evaluate.
     * @param context     The current trip ranking context.
     * @return Internal DestinationScore explanation object.
     */
    DestinationScore explainScore(Destination destination, RankingContext context);
}
