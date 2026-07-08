package com.exploreceylon.backend.model;

// Shared budget tier used by Destination, HiddenGem (and, via name only,
// Trip's own BudgetRange) so corridor candidate filtering and per-day cost
// estimation can key off the same three tiers everywhere. Nullable on the
// entities that use it — NULL means "unknown", never used to exclude a
// candidate from trip planning.
public enum BudgetLevel {
    BUDGET, MID_RANGE, LUXURY
}
