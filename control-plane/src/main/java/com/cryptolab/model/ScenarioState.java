package com.cryptolab.model;

/**
 * Persistable state vocabulary reserved for a later durable scenario-state
 * store.
 */
public enum ScenarioState {
    UNKNOWN, INITIALIZING, STANDBY, ACTIVE, UNHEALTHY, ERROR
}
