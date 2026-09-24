package com.xqfx.requirements.user;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * 登录、登出、改密、查询当前用户。这些接口不经过登录拦截器，任何人都可以调用。
 */
@RestController
@RequestMapping("/api/auth")
class AuthController {

    private final AuthService authService;
    private final int sessionDurationDays;
    private final boolean secureCookie;

    AuthController(AuthService authService,
                   @Value("${app.auth.session-duration-days:30}") int sessionDurationDays,
                   @Value("${app.auth.secure-cookie:false}") boolean secureCookie) {
        this.authService = authService;
        this.sessionDurationDays = sessionDurationDays;
        this.secureCookie = secureCookie;
    }

    @PostMapping("/login")
    UserResponse login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        try {
            var result = authService.login(request);
            TokenCookie.write(response, result.token(), sessionDurationDays * 24 * 60 * 60, secureCookie);
            return result.user();
        } catch (AuthService.AuthenticationException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, e.getMessage());
        }
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void logout(HttpServletRequest request, HttpServletResponse response) {
        TokenCookie.read(request).ifPresent(authService::logout);
        TokenCookie.clear(response, secureCookie);
    }

    /**
     * 返回当前登录用户。前端在启动时调用：401 跳登录页，200 进主界面。
     */
    @GetMapping("/current-user")
    UserResponse currentUser(HttpServletRequest request) {
        return UserResponse.from(requireUser(request));
    }

    /**
     * 修改自己的密码。改密后该用户全部会话失效，因此需要重新登录。
     */
    @PostMapping("/change-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void changePassword(HttpServletRequest request, HttpServletResponse response,
                        @Valid @RequestBody ChangePasswordRequest body) {
        var user = requireUser(request);
        authService.changePassword(user.id(), body);
        TokenCookie.clear(response, secureCookie);
    }

    private UserEntity requireUser(HttpServletRequest request) {
        var user = TokenCookie.read(request).flatMap(authService::authenticate).orElse(null);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请重新登录");
        }
        return user;
    }
}
