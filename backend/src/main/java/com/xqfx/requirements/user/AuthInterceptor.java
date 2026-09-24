package com.xqfx.requirements.user;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 统一鉴权入口：校验令牌，并按角色拦截管理员和需求处理员专属接口。
 *
 * <p>前端隐藏菜单只是界面效果，挡不住直接调用接口，因此权限判断必须落在这里。
 */
@Component
class AuthInterceptor implements HandlerInterceptor {

    /** 管理员专属的接口前缀。 */
    private static final String[] ADMIN_ONLY_PREFIXES = {
            "/api/users",
            "/api/ai-config",
            "/api/dictionaries",
    };

    /** 供全体登录用户使用、不受管理员限制的例外路径。 */
    private static final String[] ADMIN_PREFIX_EXCEPTIONS = {
            "/api/users/active",
            "/api/dictionaries/active",
    };

    /** 普通用户不可访问的处理工作台接口。 */
    private static final String[] HANDLER_ONLY_PREFIXES = {
            "/api/dashboard",
    };

    /** 系统和版本允许普通用户读取，但写操作仅限需求处理员和管理员。 */
    private static final String[] HANDLER_WRITE_PREFIXES = {
            "/api/systems",
            "/api/system-versions",
    };

    private final AuthService authService;

    AuthInterceptor(AuthService authService) {
        this.authService = authService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        var user = TokenCookie.read(request).flatMap(authService::authenticate).orElse(null);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请重新登录");
        }

        var path = request.getRequestURI();
        if (requiresAdmin(path) && !user.isAdmin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只有管理员可以执行该操作");
        }
        if (requiresHandler(request.getMethod(), path) && !user.isHandler()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只有需求处理员或管理员可以执行该操作");
        }

        CurrentUser.set(user);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        CurrentUser.clear();
    }

    private static boolean requiresAdmin(String path) {
        for (var exception : ADMIN_PREFIX_EXCEPTIONS) {
            if (path.equals(exception)) {
                return false;
            }
        }
        for (var prefix : ADMIN_ONLY_PREFIXES) {
            if (path.equals(prefix) || path.startsWith(prefix + "/")) {
                return true;
            }
        }
        return false;
    }

    private static boolean requiresHandler(String method, String path) {
        for (var prefix : HANDLER_ONLY_PREFIXES) {
            if (path.equals(prefix) || path.startsWith(prefix + "/")) {
                return true;
            }
        }
        if ("GET".equalsIgnoreCase(method)) {
            return false;
        }
        for (var prefix : HANDLER_WRITE_PREFIXES) {
            if (path.equals(prefix) || path.startsWith(prefix + "/")) {
                return true;
            }
        }
        return false;
    }
}
