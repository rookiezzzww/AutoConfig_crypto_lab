package com.cryptolab.dto;

import java.time.Instant;

public record SwitchScenarioResponse(String previousScenario, String currentScenario, String message,
        Instant timestamp) {
}
