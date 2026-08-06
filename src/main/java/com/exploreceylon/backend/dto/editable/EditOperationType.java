package com.exploreceylon.backend.dto.editable;

/**
 * Supported edit operation types for fine-grained itinerary editing.
 */
public enum EditOperationType {
    ADD_STOP,
    REMOVE_STOP,
    REPLACE_STOP,
    MOVE_STOP,
    CHANGE_START_TIME,
    CHANGE_VISIT_DURATION,
    CHANGE_TRAVEL_STYLE,
    CHANGE_BUDGET,
    LOCK_STOP,
    UNLOCK_STOP
}
