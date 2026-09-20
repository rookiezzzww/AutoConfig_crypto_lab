package com.cryptolab.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record SystemStatusResponse(
		String application,
		String haproxy,
		String docker,
		String activeScenario,
		Map<String, String> backends,
		List<ScenarioStatusSummary> scenarios,
		Instant timestamp) {
}
