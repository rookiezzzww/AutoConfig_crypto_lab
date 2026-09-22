package com.cryptolab.controller;
import com.cryptolab.dto.*;
import com.cryptolab.service.ScenarioService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import java.util.List;
/** 提供场景列表、详情、当前状态和激活动作的 REST 控制器。 */
@RestController 
@RequestMapping("/api/scenarios")
public class ScenarioController {
    private final ScenarioService service; 
    /** 注入场景业务服务。 */
    public ScenarioController(ScenarioService service){
        this.service=service;
    }
    /** 查询全部场景及其实时状态。 */
    @GetMapping 
    public ApiResponse<List<ScenarioResponse>> all(){
        return ApiResponse.ok(service.list());
    }
    /** 查询 HAProxy 当前实际指向的场景。 */
    @GetMapping("/current") 
    public ApiResponse<ScenarioResponse> current(){
        return ApiResponse.ok(service.current());
    }
    /** 按 ID 查询单个场景。 */
    @GetMapping("/{id}") 
    public ApiResponse<ScenarioResponse> one(@PathVariable String id){
        return ApiResponse.ok(service.get(id));
    }
    /** 触发目标场景的动态切换，并记录来源客户端 IP。 */
    @PostMapping("/{id}/activate") 
    public ApiResponse<SwitchScenarioResponse> activate(@PathVariable String id,
            @RequestBody(required = false) ActivateScenarioRequest body, HttpServletRequest request){
        ActivateScenarioRequest command = body == null ? new ActivateScenarioRequest(java.util.Map.of(), false) : body;
        return ApiResponse.ok(service.activate(id, command.safeOptions(), command.stopPrevious(), request.getRemoteAddr()));
    }
}
