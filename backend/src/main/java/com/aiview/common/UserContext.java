package com.aiview.common;

import com.aiview.auth.security.AuthUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class UserContext {

    private UserContext() {
    }

    public static AuthUser currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthUser user) {
            return user;
        }
        throw new BizException(ResultCode.UNAUTHORIZED);
    }

    public static Long currentUserId() {
        return currentUser().id();
    }
}