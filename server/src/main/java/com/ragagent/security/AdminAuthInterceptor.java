package com.ragagent.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

/**
 * 拦截 /api/admin/**：要求 JWT 中 role == "ADMIN"。
 * 注册见 {@link com.ragagent.config.WebConfig#addInterceptors}。
 */
@Component
public class AdminAuthInterceptor implements HandlerInterceptor {

    private final JwtAuthFilter jwtAuthFilter;

    public AdminAuthInterceptor(JwtAuthFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) return true; // 放行 CORS 预检
        String role = jwtAuthFilter.getRole(request);
        if (role == null) {
            write(response, 401, "请先登录");
            return false;
        }
        if (!"ADMIN".equals(role)) {
            write(response, 403, "需要管理员权限");
            return false;
        }
        return true;
    }

    private void write(HttpServletResponse response, int status, String msg) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"error\":\"" + msg + "\"}");
    }
}
