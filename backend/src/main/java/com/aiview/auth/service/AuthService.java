package com.aiview.auth.service;

import com.aiview.auth.dto.AuthResponse;
import com.aiview.auth.dto.LoginRequest;
import com.aiview.auth.dto.RegisterRequest;
import com.aiview.auth.dto.UserVO;
import com.aiview.auth.entity.User;
import com.aiview.auth.entity.UserProfile;
import com.aiview.auth.mapper.UserMapper;
import com.aiview.auth.mapper.UserProfileMapper;
import com.aiview.auth.security.JwtUtil;
import com.aiview.common.BizException;
import com.aiview.common.ResultCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserMapper userMapper;
    private final UserProfileMapper userProfileMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        Long count = userMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<User>()
                        .eq(User::getUsername, request.getUsername()));
        if (count > 0) {
            throw new BizException(ResultCode.USERNAME_EXISTS);
        }
        User user = new User();
        user.setUsername(request.getUsername());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setNickname(request.getNickname() == null || request.getNickname().isBlank()
                ? request.getUsername() : request.getNickname());
        userMapper.insert(user);

        UserProfile profile = new UserProfile();
        profile.setUserId(user.getId());
        userProfileMapper.insert(profile);

        return buildAuthResponse(user);
    }

    public AuthResponse login(LoginRequest request) {
        User user = userMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<User>()
                        .eq(User::getUsername, request.getUsername()));
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BizException(ResultCode.LOGIN_FAILED);
        }
        return buildAuthResponse(user);
    }

    public AuthResponse refresh(String refreshToken) {
        try {
            Claims claims = jwtUtil.parse(refreshToken);
            Long userId = claims.get("uid", Long.class);
            User user = userId == null ? null : userMapper.selectById(userId);
            if (user == null) {
                throw new BizException(ResultCode.TOKEN_INVALID);
            }
            return buildAuthResponse(user);
        } catch (JwtException e) {
            throw new BizException(ResultCode.TOKEN_INVALID);
        }
    }

    private AuthResponse buildAuthResponse(User user) {
        return new AuthResponse(
                jwtUtil.createAccessToken(user.getId(), user.getUsername()),
                jwtUtil.createRefreshToken(user.getId(), user.getUsername()),
                UserVO.from(user));
    }
}