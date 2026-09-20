package com.cryptolab.dto;
import com.cryptolab.model.*;
import java.util.List;
public record ScenarioResponse(String id, String name, String cve, String description, List<String> category,
        String containerName, int containerPort, String protocol, boolean enabled, String haproxyStatus,
        ScenarioStatus status, boolean active, String health,
        List<VulnerabilityOptionResponse> vulnerabilityOptions, boolean vulnerabilityConditionsMet) {}
