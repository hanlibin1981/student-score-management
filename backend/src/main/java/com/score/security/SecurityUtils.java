package com.score.security;

import com.score.common.BusinessException;
import com.score.common.Role;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static AuthUser current() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthUser user)) {
            throw new BusinessException(401, "未登录");
        }
        return user;
    }

    public static Long currentUserId() {
        return current().id();
    }

    public static boolean isAdmin() {
        return Role.ADMIN.equals(current().role());
    }

    public static boolean isStudent() {
        return Role.STUDENT.equals(current().role());
    }
}
