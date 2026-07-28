package com.medicine.security;

import com.medicine.common.Result;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * 角色拦截器 — 校验 userType 是否在允许的角色集合中。
 * 在 JwtInterceptor 之后执行（JwtInterceptor 已将 userId/username/userType 写入 request attribute）。
 */
@Component
public class RoleInterceptor implements HandlerInterceptor {

    /** admin 路径允许的角色：管理员(2) */
    private static final Set<Integer> ADMIN_ROLES = Collections.singleton(2);

    /** pharmacist 路径允许的角色：药师(3) + 管理员(2) */
    private static final Set<Integer> PHARMACIST_ROLES =
            Collections.unmodifiableSet(new HashSet<>(Arrays.asList(2, 3)));

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String path = request.getRequestURI();

        // userType 由 JwtInterceptor 写入，理论上此处不可能为 null（JwtInterceptor 已拦截未登录请求）
        Integer userType = (Integer) request.getAttribute("userType");
        if (userType == null) {
            sendError(response, 401, "未授权，请先登录", 40100);
            return false;
        }

        if (path.startsWith("/api/v1/admin/")) {
            if (!ADMIN_ROLES.contains(userType)) {
                sendError(response, 403, "权限不足，仅管理员可操作", 40300);
                return false;
            }
        } else if (path.startsWith("/api/v1/pharmacist/")) {
            if (!PHARMACIST_ROLES.contains(userType)) {
                sendError(response, 403, "权限不足，仅药师或管理员可操作", 40300);
                return false;
            }
        }

        return true;
    }

    private void sendError(HttpServletResponse response, int status, String message, int code) throws Exception {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        Result result = Result.fail(message, code);
        response.getWriter().write(new ObjectMapper().writeValueAsString(result));
    }
}
