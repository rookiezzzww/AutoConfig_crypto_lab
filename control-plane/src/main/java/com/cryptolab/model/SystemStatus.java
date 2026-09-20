package com.cryptolab.model;

import java.util.Map;

/** Internal system snapshot model, kept separate from the REST response DTO. */
public record SystemStatus(String application, String haproxy, String docker, String activeScenario,
        Map<String, String> backends) {
}
