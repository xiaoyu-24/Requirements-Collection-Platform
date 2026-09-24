package com.xqfx.requirements.user;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;
import com.xqfx.requirements.dictionary.DictionaryService;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({AuthService.class, UserService.class, DictionaryService.class})
class AuthSecurityTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserSessionRepository sessionRepository;

    @BeforeEach
    void setUp() {
        sessionRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void loginWithCorrectPassword_succeeds() {
        var created = userService.createUser(new UserSaveRequest("alice", "Alice Chen", null, UserRole.USER));
        var loginResponse = authService.login(new LoginRequest("alice", created.initialPassword()));

        assertThat(loginResponse.user().username()).isEqualTo("alice");
        assertThat(loginResponse.user().mustChangePassword()).isTrue();
    }

    @Test
    void loginWithWrongPassword_fails() {
        var created = userService.createUser(new UserSaveRequest("alice", "Alice Chen", null, UserRole.USER));

        assertThatThrownBy(() -> authService.login(new LoginRequest("alice", "wrong")))
                .isInstanceOf(AuthService.AuthenticationException.class)
                .hasMessageContaining("账号或密码不正确");
    }

    @Test
    void loginWithDisabledAccount_fails() {
        var created = userService.createUser(new UserSaveRequest("alice", "Alice Chen", null, UserRole.USER));
        userService.updateDisabled(created.user().id(), true);

        assertThatThrownBy(() -> authService.login(new LoginRequest("alice", created.initialPassword())))
                .isInstanceOf(AuthService.AuthenticationException.class)
                .hasMessageContaining("已停用");
    }

    @Test
    void loginFailureRateLimiting_locksAccountAfter5Failures() {
        var created = userService.createUser(new UserSaveRequest("alice", "Alice Chen", null, UserRole.USER));

        for (int i = 0; i < 5; i++) {
            try {
                authService.login(new LoginRequest("alice", "wrong"));
            } catch (AuthService.AuthenticationException ignored) {
            }
        }

        // 验证锁定时间戳已持久化到数据库。这是回归测试锁住一个真实缺陷：
        // login 方法最初标注了 @Transactional，密码错误抛出的 AuthenticationException
        // 属于未检查异常，会回滚当前事务，把刚写入的失败计数一并撤销，导致账号永远锁不上。
        // 修复方式是去掉 login 的事务注解，把"记录失败"拆成独立事务方法，由 Spring 代理调用。
        // 注意：达到 5 次失败后，实体会把计数清零并设置 lockedUntil，所以只断言后者非空。
        var user = userRepository.findByUsername("alice").orElseThrow();
        assertThat(user.lockedUntil()).isNotNull();

        assertThatThrownBy(() -> authService.login(new LoginRequest("alice", created.initialPassword())))
                .isInstanceOf(AuthService.AuthenticationException.class)
                .hasMessageContaining("登录失败次数过多");
    }

    @Test
    void authenticateWithValidToken_returnsUser() {
        var created = userService.createUser(new UserSaveRequest("alice", "Alice Chen", null, UserRole.USER));
        var loginResponse = authService.login(new LoginRequest("alice", created.initialPassword()));

        var authenticated = authService.authenticate(loginResponse.token());

        assertThat(authenticated).isPresent();
        assertThat(authenticated.get().username()).isEqualTo("alice");
    }

    @Test
    void authenticateWithInvalidToken_returnsEmpty() {
        var authenticated = authService.authenticate("invalid-token");
        assertThat(authenticated).isEmpty();
    }

    @Test
    void authenticateAfterAccountDisabled_returnsEmptyAndClearsSession() {
        var created = userService.createUser(new UserSaveRequest("alice", "Alice Chen", null, UserRole.USER));
        var loginResponse = authService.login(new LoginRequest("alice", created.initialPassword()));

        userService.updateDisabled(created.user().id(), true);
        var authenticated = authService.authenticate(loginResponse.token());

        assertThat(authenticated).isEmpty();
        assertThat(sessionRepository.findAll()).isEmpty();
    }

    @Test
    void changePassword_invalidatesAllSessions() {
        var created = userService.createUser(new UserSaveRequest("alice", "Alice Chen", null, UserRole.USER));
        var loginResponse = authService.login(new LoginRequest("alice", created.initialPassword()));

        authService.changePassword(created.user().id(),
                new ChangePasswordRequest(created.initialPassword(), "newPassword123"));

        var authenticated = authService.authenticate(loginResponse.token());
        assertThat(authenticated).isEmpty();
    }

    @Test
    void resetPassword_invalidatesAllSessions() {
        var created = userService.createUser(new UserSaveRequest("alice", "Alice Chen", null, UserRole.USER));
        var loginResponse = authService.login(new LoginRequest("alice", created.initialPassword()));

        userService.resetPassword(created.user().id());

        var authenticated = authService.authenticate(loginResponse.token());
        assertThat(authenticated).isEmpty();
    }

    @Test
    void cannotDisableLastAdmin() {
        var created = userService.createUser(new UserSaveRequest("admin", "Admin User", null, UserRole.ADMIN));

        assertThatThrownBy(() -> userService.updateDisabled(created.user().id(), true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("至少需要保留一个启用中的管理员");
    }

    @Test
    void ordinaryUserCanReadSystems() {
        var interceptor = interceptorFor(UserRole.USER);
        var request = authenticatedRequest("GET", "/api/systems");

        try {
            assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), new Object())).isTrue();
        } finally {
            CurrentUser.clear();
        }
    }

    @Test
    void ordinaryUserCanReadSystemVersions() {
        var interceptor = interceptorFor(UserRole.USER);
        var request = authenticatedRequest("GET", "/api/systems/1/versions");

        try {
            assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), new Object())).isTrue();
        } finally {
            CurrentUser.clear();
        }
    }

    @Test
    void ordinaryUserCannotCreateSystems() {
        var interceptor = interceptorFor(UserRole.USER);
        var request = authenticatedRequest("POST", "/api/systems");

        assertThatThrownBy(() -> interceptor.preHandle(request, new MockHttpServletResponse(), new Object()))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    private static AuthInterceptor interceptorFor(UserRole role) {
        var authService = mock(AuthService.class);
        var user = new UserEntity("reader", "hash", "Reader", null, role);
        when(authService.authenticate("test-token")).thenReturn(Optional.of(user));
        return new AuthInterceptor(authService);
    }

    private static MockHttpServletRequest authenticatedRequest(String method, String path) {
        var request = new MockHttpServletRequest(method, path);
        request.setCookies(new Cookie(TokenCookie.NAME, "test-token"));
        return request;
    }
}
