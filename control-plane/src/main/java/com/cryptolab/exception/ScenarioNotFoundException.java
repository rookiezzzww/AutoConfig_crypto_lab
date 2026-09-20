package com.cryptolab.exception;

public class ScenarioNotFoundException extends RuntimeException {
    public ScenarioNotFoundException(String id) {
        super("Scenario not found: " + id);
    }
}
