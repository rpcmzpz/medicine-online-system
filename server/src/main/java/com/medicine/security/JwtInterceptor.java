package com.medicine.security;

import com.medicine.common.BusinessException;
import com.medicine.common.Result;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Component
public class JwtInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;

    public JwtInterceptor(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            sendError(response, 401, "未授权，请先登录", 40100);
            return false;
        }

        String token = authHeader.substring(7);
        try {
            Claims claims = jwtUtil.parseToken(token);
            request.setAttribute("userId", Long.valueOf(claims.get("userId").toString()));
            request.setAttribute("username", claims.get("username").toString());
            request.setAttribute("userType", Integer.valueOf(claims.get("userType").toString()));
            return true;
        } catch (ExpiredJwtException e) {
            sendError(response, 401, "Token已过期，请重新登录", 40101);
            return false;
        } catch (Exception e) {
            sendError(response, 401, "Token无效", 40100);
            return false;
        }
    }

    private void sendError(HttpServletResponse response, int status, String message, int code) throws Exception {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        Result result = Result.fail(message, code);
        response.getWriter().write(new ObjectMapper().writeValueAsString(result));
    }
}
