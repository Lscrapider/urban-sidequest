package com.urbansidequest.backend.controller;

import com.urbansidequest.backend.config.AuthenticatedUser;
import com.urbansidequest.backend.domain.param.LoginParam;
import com.urbansidequest.backend.domain.vo.CurrentUserVO;
import com.urbansidequest.backend.domain.vo.LoginVO;
import com.urbansidequest.backend.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public LoginVO login(@Valid @RequestBody LoginParam loginParam) {
        return this.authService.login(loginParam);
    }

    @GetMapping("/me")
    public CurrentUserVO me(@AuthenticationPrincipal AuthenticatedUser authenticatedUser) {
        return this.authService.getCurrentUser(authenticatedUser);
    }
}
