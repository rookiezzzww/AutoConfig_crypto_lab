package com.cryptolab.controller;
import com.cryptolab.dto.ApiResponse;
import com.cryptolab.model.AuditLog;
import com.cryptolab.service.AuditService;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController 
@RequestMapping("/api/audit") 
/** 提供最近场景切换和状态漂移日志的 REST 查询接口。 */
public class AuditController { 
    private final AuditService service; 
    /** 注入审计服务。 */
    public AuditController(AuditService service){
        this.service=service;
    } 
    @GetMapping("/logs") 
    /** 返回按时间倒序排列的最近审计记录。 */
    public ApiResponse<List<AuditLog>> logs(){
        return ApiResponse.ok(service.recent());
    } 
}
