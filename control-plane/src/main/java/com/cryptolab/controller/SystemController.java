package com.cryptolab.controller;
import com.cryptolab.dto.*;
import com.cryptolab.config.LabProperties;
import com.cryptolab.service.*;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
@RestController 
@RequestMapping("/api/system") 
/** 汇总控制面、Docker、HAProxy 与所有场景状态的 REST 控制器。 */
public class SystemController{
 private final ProxyService proxy; 
 private final ScenarioService scenarios; 
 private final LabProperties properties;
 /** 注入系统状态查询所需服务和配置。 */
 public SystemController(ProxyService proxy,ScenarioService scenarios, LabProperties properties){
    this.proxy=proxy;
    this.scenarios=scenarios;
    this.properties = properties;
}
/** 返回系统状态；HAProxy 不可用时仍返回降级快照。 */
@GetMapping("/status") 
public ApiResponse<SystemStatusResponse> status(){ 
    List<ScenarioResponse> scenarioList = scenarios.list();
    List<ScenarioStatusSummary> summary = scenarioList.stream()
            .map(s -> new ScenarioStatusSummary(s.id(), s.name(), s.containerName(), s.health(), s.haproxyStatus(), s.status(), s.active()))
            .toList();
    String dockerState = properties.isDockerEnabled() ? "AVAILABLE" : "DISABLED";
    try{
        Map<String, String> backends = proxy.getRuntimeStatus();
        String activeScenario = scenarioList.stream().filter(ScenarioResponse::active).map(ScenarioResponse::id).findFirst().orElse(null);
        if (activeScenario == null) {
            activeScenario = proxy.getActiveScenario().orElse(null);
        }
        return ApiResponse.ok(new SystemStatusResponse("UP","UP",dockerState,activeScenario,backends,summary,Instant.now()));
    } catch(RuntimeException e){
        Map<String, String> fallback = summary.stream().collect(Collectors.toMap(ScenarioStatusSummary::id, ScenarioStatusSummary::haproxyStatus, (a, b) -> a));
        return ApiResponse.ok(new SystemStatusResponse("UP","DOWN",dockerState,null,fallback,summary,Instant.now()));
    }
 }
}
