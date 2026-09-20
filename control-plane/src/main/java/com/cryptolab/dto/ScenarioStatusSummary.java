package com.cryptolab.dto;

import com.cryptolab.model.ScenarioStatus;

public record ScenarioStatusSummary(
        String id,
        String name,
        String containerName,
        String containerHealth,
        String haproxyStatus,
        ScenarioStatus status,
        boolean active
) {}
