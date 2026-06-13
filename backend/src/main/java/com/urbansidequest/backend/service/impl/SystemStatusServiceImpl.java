package com.urbansidequest.backend.service.impl;

import com.urbansidequest.backend.domain.vo.SystemStatusVO;
import com.urbansidequest.backend.service.SystemStatusService;
import java.time.Instant;
import org.springframework.stereotype.Service;

@Service
public class SystemStatusServiceImpl implements SystemStatusService {

    private static final String SERVICE_NAME = "urban-sidequest-backend";

    @Override
    public SystemStatusVO getStatus() {
        return new SystemStatusVO(SERVICE_NAME, "UP", Instant.now());
    }
}

