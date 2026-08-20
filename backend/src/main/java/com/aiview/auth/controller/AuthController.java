package com.aiview.auth.controller;

import com.aiview.auth.dto.AuthResponse;
import com.aiview.auth.dto.LoginRequest;
import com.aiview.auth.dto.RegisterRequest;
import com.aiview.auth.dto.UserVO;
import com.aiview.auth.security.AuthUser;
import com.aiview.auth.service.AuthService;
import com.aiview.common.Result;
import com.aiview.common.UserContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public Result<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return Result.ok(authService.register(request));
    }

    @PostMapping("/login")
    public Result<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.ok(authService.login(request));
    }

    @PostMapping("/refresh")
    public Result<AuthResponse> refresh(@RequestHeader("Authorization") String authorization) {
        String token = authorization.startsWith("Bearer ") ? authorization.substring(7) : authorization;
        return Result.ok(authService.refresh(token));
    }

    @GetMapping("/me")
    public Result<UserVO> me() {
        AuthUser user = UserContext.currentUser();
        return Result.ok(new UserVO(user.id(), user.username(), user.nickname(), null));
    }
}