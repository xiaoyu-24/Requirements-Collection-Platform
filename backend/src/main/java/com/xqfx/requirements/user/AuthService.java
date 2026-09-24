package com.xqfx.requirements.user;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

/**
 * 登录认证与会话管理。
 *
 * <p>会话采用"数据库保存令牌哈希 + 访问时续期"的方式：用户持续使用不会被登出，
 * 但账号被停用或会话被清除后，下一次请求即失效。这样"长期保持登录"不会让停用操作落空。
 */
@Service
public class AuthService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final int TOKEN_BYTES = 32;

    private final UserRepository userRepository;
    private final UserSessionRepository sessionRepository;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final SecureRandom secureRandom = new SecureRandom();
    private final int sessionDurationDays;
    /** 自身的 Spring 代理。内部直接调用 this.xxx 不经过代理，@Transactional 不会生效。 */
    private final AuthService self;

    AuthService(UserRepository userRepository,
                UserSessionRepository sessionRepository,
                @Value("${app.auth.session-duration-days:30}") int sessionDurationDays,
                @Lazy AuthService self) {
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
        this.sessionDurationDays = sessionDurationDays;
        this.self = self;
    }

    /**
     * 登录校验。
     *
     * <p>本方法刻意不加 @Transactional：密码错误时要抛出未检查异常，
     * 而未检查异常会回滚当前事务——若在同一事务里写失败计数，这条记录会被一并撤销，
     * 账号永远锁不上，暴力破解防护就失效了。因此把"记录失败"和"建立会话"
     * 拆成两个独立的事务方法，由注入的自身代理调用，保证各自独立提交。
     */
    public LoginResponse login(LoginRequest request) {
        var user = userRepository.findByUsername(request.username().trim()).orElse(null);
        if (user == null) {
            // 用户不存在与密码错误返回同一提示，避免暴露哪些账号真实存在。
            throw new AuthenticationException("账号或密码不正确");
        }
        if (user.isDisabled()) {
            throw new AuthenticationException("账号已停用，请联系管理员");
        }
        if (user.isLocked()) {
            throw new AuthenticationException("登录失败次数过多，请稍后再试");
        }
        if (!passwordEncoder.matches(request.password(), user.passwordHash())) {
            self.recordLoginFailure(user.id());
            throw new AuthenticationException("账号或密码不正确");
        }
        return self.createSession(user.id());
    }

    /** 记录一次登录失败。独立事务，不受调用方回滚影响。 */
    @Transactional
    void recordLoginFailure(Long userId) {
        userRepository.findById(userId).ifPresent(user -> {
            user.recordLoginFailure();
            userRepository.save(user);
        });
    }

    /** 登录成功后清除失败计数并签发会话。 */
    @Transactional
    LoginResponse createSession(Long userId) {
        var user = userRepository.findById(userId).orElseThrow();
        user.clearLoginFailures();
        userRepository.save(user);

        var token = generateToken();
        var session = new UserSessionEntity(hashToken(token), user, LocalDateTime.now(ZONE).plusDays(sessionDurationDays));
        sessionRepository.save(session);
        return new LoginResponse(token, UserResponse.from(user));
    }

    /**
     * 校验令牌并返回当前用户。会话有效时顺带续期。
     * 账号在会话存续期间被停用的，这里同样拒绝。
     */
    @Transactional
    public Optional<UserEntity> authenticate(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        var session = sessionRepository.findByTokenHash(hashToken(token)).orElse(null);
        if (session == null) {
            return Optional.empty();
        }
        if (session.isExpired()) {
            sessionRepository.delete(session);
            return Optional.empty();
        }
        var user = session.user();
        if (user.isDisabled()) {
            sessionRepository.delete(session);
            return Optional.empty();
        }
        // 避免每个请求都写库：距上次续期不足一天时跳过。
        var now = LocalDateTime.now(ZONE);
        if (session.lastSeenAt().isBefore(now.minusDays(1))) {
            session.touch(now.plusDays(sessionDurationDays));
            sessionRepository.save(session);
        }
        return Optional.of(user);
    }

    @Transactional
    public void logout(String token) {
        if (token != null && !token.isBlank()) {
            sessionRepository.deleteByTokenHash(hashToken(token));
        }
    }

    /**
     * 修改自己的密码。改密后清除该用户全部会话，
     * 其他设备上的登录一并失效——密码被泄露时这一步是必要的。
     */
    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        var user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        if (!passwordEncoder.matches(request.currentPassword(), user.passwordHash())) {
            throw new IllegalArgumentException("当前密码不正确");
        }
        if (passwordEncoder.matches(request.newPassword(), user.passwordHash())) {
            throw new IllegalArgumentException("新密码不能与当前密码相同");
        }
        user.changePassword(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
        sessionRepository.deleteAllByUserId(userId);
    }

    String encodePassword(String rawPassword) {
        return passwordEncoder.encode(rawPassword);
    }

    @Transactional
    void invalidateSessionsFor(Long userId) {
        sessionRepository.deleteAllByUserId(userId);
    }

    private String generateToken() {
        var bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * 令牌以 SHA-256 存储。令牌本身是高熵随机串，不存在被字典攻击的风险，
     * 因此不需要 BCrypt——用哈希是为了数据库泄露时令牌不能被直接拿去登录。
     */
    private static String hashToken(String token) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("计算令牌哈希失败", e);
        }
    }

    public static class AuthenticationException extends RuntimeException {
        public AuthenticationException(String message) {
            super(message);
        }
    }
}
