package com.urbansidequest.backend.controller;

import com.urbansidequest.backend.domain.vo.SystemStatusVO;
import com.urbansidequest.backend.service.SystemStatusService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/health")
public class HealthController {

    private final SystemStatusService systemStatusService;

    public HealthController(SystemStatusService systemStatusService) {
        this.systemStatusService = systemStatusService;
    }

    @GetMapping
    public SystemStatusVO health() {
        return this.systemStatusService.getStatus();
    }
}

